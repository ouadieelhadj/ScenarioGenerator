package com.staging.sg.mc.dmas.mastercard.emv;

import com.staging.sg.common.emv.McDmasEmv;
import com.staging.sg.common.entity.McDmasCard;
import com.staging.sg.common.entity.McDmasMastercardKey;
import com.staging.sg.common.repository.McDmasCardRepository;
import com.staging.sg.common.repository.McDmasMastercardKeyRepository;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decodage et VALIDATION du DE55 EMV, cote reseau Mastercard.
 *
 * Le membre construit l'ARQC ; le reseau le RECALCULE a partir de la
 * meme MDK et compare. C'est ce qui rend le cryptogramme infalsifiable :
 * les deux cotes derivent la cle ICC puis la cle de session sans jamais
 * les faire circuler.
 *
 * Reutilise McDmasEmv (sg-common), le meme code que le membre — aucune
 * divergence possible entre construction et validation.
 */
@Service
public class McDmasEmvValidator {

    private static final Logger log = LoggerFactory.getLogger(McDmasEmvValidator.class);

    private final McDmasEmv emv;
    private final McDmasCardRepository cardRepo;
    private final McDmasMastercardKeyRepository keyRepo;

    public McDmasEmvValidator(McDmasEmv emv,
                              McDmasCardRepository cardRepo,
                              McDmasMastercardKeyRepository keyRepo) {
        this.emv = emv;
        this.cardRepo = cardRepo;
        this.keyRepo = keyRepo;
    }

    /** Tags decodes d'un DE55, pour journalisation. */
    public Map<String, String> decode(byte[] de55) {
        Map<String, String> tags = new LinkedHashMap<>();
        if (de55 == null) return tags;
        int i = 0;
        while (i < de55.length) {
            int tagStart = i;
            int b0 = de55[i] & 0xFF;
            i++;
            // tag sur 2 octets si les 5 bits de poids faible sont a 1
            if ((b0 & 0x1F) == 0x1F && i < de55.length) i++;
            String tag = ISOUtil.hexString(de55, tagStart, i - tagStart).toUpperCase();

            if (i >= de55.length) break;
            int len = de55[i] & 0xFF;
            i++;
            if (i + len > de55.length) break;
            String val = ISOUtil.hexString(de55, i, len).toUpperCase();
            i += len;
            tags.put(tag, val);
        }
        return tags;
    }

    /**
     * Recalcule l'ARQC et le compare a celui recu.
     * Le membre acquereur/emetteur est identifie par le member_group_id
     * deja resolu par le handler (via le DE2).
     */
    public Map<String, Object> validate(ISOMsg msg, String memberGroupId, String bankCode) {
        Map<String, Object> r = new LinkedHashMap<>();
        try {
            byte[] de55 = msg.hasField(55) ? msg.getBytes(55) : null;
            if (de55 == null) {
                r.put("present", false);
                return r;
            }
            r.put("present", true);

            Map<String, String> tags = decode(de55);
            r.put("tags", tags);

            String arqcRecu = tags.get("9F26");
            String atcHex   = tags.get("9F36");
            String iad      = tags.get("9F10");
            String aip      = tags.get("82");
            String un       = tags.get("9F37");
            log.info("[EMV-VAL] DE55 recu — {} tags, ARQC={} ATC={}",
                    tags.size(), arqcRecu, atcHex);

            String pan = msg.hasField(2) ? msg.getString(2) : null;
            McDmasCard card = (pan != null) ? cardRepo.findByPan(pan).orElse(null) : null;
            McDmasMastercardKey mdk = keyRepo
                    .findByMemberGroupIdAndKeyTypeAndStatus(memberGroupId, "MDK", "ACTIVE")
                    .orElse(null);

            if (card == null || mdk == null) {
                r.put("validated", false);
                r.put("reason", card == null ? "carte inconnue" : "MDK absente");
                log.warn("[EMV-VAL] Validation impossible : {}", r.get("reason"));
                return r;
            }

            // Recalcul avec les valeurs REUES dans le DE55
            McDmasEmv.EmvInput in = new McDmasEmv.EmvInput();
            in.mdkUnderLmk = mdk.getKeyUnderLmk();
            in.mdkKcv      = mdk.getKcv();
            in.mdkLenBytes = mdk.getKeyLength() != null ? mdk.getKeyLength() : 16;
            in.pan         = pan;

            // DE23 is a 3-digit Card Sequence Number in ISO 8583.
            // EMV PSN is the last 2 digits (for example DE23=001 -> PSN=01).
            String psnDb = normalizePsn(card.getEmvPsn());
            String rawDe23 = msg.hasField(23) ? msg.getString(23) : null;
            String psnDe23 = normalizePsn(rawDe23);

            // DE23=000 does not provide a usable PSN.
            // In that case, use the PSN stored for the card.
            boolean de23Usable = psnDe23 != null && !"00".equals(psnDe23);
            in.psn = de23Usable ? psnDe23 : (psnDb != null ? psnDb : "00");

            String psnSource = de23Usable ? "DE23" : (psnDb != null ? "DB" : "DEFAULT");
            log.info("[EMV-VAL] PSN source={} value={} (DE23={} DB={})",
                    psnSource, in.psn, rawDe23 != null ? rawDe23 : "absent", psnDb);

            if (de23Usable && psnDb != null && !psnDe23.equals(psnDb)) {
                log.warn("[EMV-VAL] PSN mismatch: DE23={} DB={} - DE23 is used for ARQC",
                        psnDe23, psnDb);
            }
            in.atc         = Integer.parseInt(atcHex, 16);
            in.aip         = aip;
            in.iad         = iad;
            in.unpredictable = un;
            in.amount      = tags.getOrDefault("9F02", "000000000000");
            in.otherAmount = tags.getOrDefault("9F03", "000000000000");
            in.currency    = tags.getOrDefault("5F2A", "0000");
            in.countryCode = tags.getOrDefault("9F1A", "0000");
            in.date        = tags.getOrDefault("9A", "000000");
            in.txType      = tags.getOrDefault("9C", "00");
            in.tvr         = tags.getOrDefault("95", "0000000000");

            String arqcCalcule = emv.recomputeArqc(in);
            boolean match = arqcCalcule.equalsIgnoreCase(arqcRecu);

            r.put("arqc_recu", arqcRecu);
            r.put("arqc_calcule", arqcCalcule);
            r.put("validated", match);

            // ARPC : reponse cryptographique de l'emetteur, si l'ARQC est bon
            if (match) {
                try {
                    String arc = "0012";   // approbation, valeur observee sur le reseau reel
                    r.put("arpc", emv.computeArpc(in, arqcRecu, arc));
                    r.put("arc",  arc);
                } catch (Exception ae) {
                    log.warn("[EMV-VAL] calcul ARPC impossible : {}", ae.getMessage());
                }
            }

            log.info("[EMV-VAL] ARQC recu={} calcule={} match={}",
                    arqcRecu, arqcCalcule, match);
            return r;

        } catch (Exception e) {
            r.put("validated", false);
            r.put("error", String.valueOf(e.getMessage()));
            log.error("[EMV-VAL] Erreur de validation : {}", e.getMessage(), e);
            return r;
        }
    }
    private String normalizePsn(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        if (digits.length() == 1) return "0" + digits;
        return digits.substring(digits.length() - 2);
    }

}
