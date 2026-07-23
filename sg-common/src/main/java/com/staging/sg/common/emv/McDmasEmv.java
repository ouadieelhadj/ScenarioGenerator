package com.staging.sg.common.emv;

import com.staging.sg.common.iso.crypto.JposHsmService;
import org.jpos.iso.ISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;

/**
 * Construction du DE55 EMV et de l'ARQC, cote MEMBRE.
 *
 * ------------------------------------------------------------------
 *  CHOIX CRYPTOGRAPHIQUES — STANDARD EMV
 * ------------------------------------------------------------------
 * Derivation clef ICC     : EMV Book 2, Option A
 *     ICC = 3DES( PAN[16 droite] || PSN )  sous MDK
 *
 * Derivation clef session : EMV Common Session Key (CSK)
 *     SK = 3DES( ATC || F0 || 00...  ,  ATC || 0F || 00... )  sous ICC
 *
 * ARQC : MAC ISO 9797-1 algorithme 3 (Retail MAC) sur les donnees
 *     CDOL1, dans l'ordre M/Chip standard :
 *     9F02 9F03 9F1A 95 5F2A 9A 9C 9F37 82 9F36 9F10
 *
 * Ces choix suivent les specifications EMV et M/Chip par defaut. Si un
 * membre reel impose un autre schema (M/Chip Master Key, ordre CDOL1
 * different), il faudra les rendre parametrables par carte.
 *
 * ------------------------------------------------------------------
 *  AUCUNE CLE EN CLAIR EN BASE
 * ------------------------------------------------------------------
 * La MDK est stockee sous LMK. On la reconstruit via le HSM et on
 * n'expose ses octets qu'en memoire, le temps du calcul.
 */
@Service
public class McDmasEmv {

    private static final Logger log = LoggerFactory.getLogger(McDmasEmv.class);

    private final JposHsmService hsm;

    public McDmasEmv(JposHsmService hsm) {
        this.hsm = hsm;
    }

    // ==================================================================
    //  DONNEES D'ENTREE DU CALCUL
    // ==================================================================

    /** Ce que la carte et la transaction apportent pour construire le DE55. */
    public static class EmvInput {
        // cle
        public String mdkUnderLmk;   // MDK chiffree sous LMK
        public String mdkKcv;
        public int    mdkLenBytes = 16;

        // carte
        public String pan;
        public String psn = "00";    // PAN Sequence Number
        public int    atc;           // compteur, incremente par carte
        public String aid;           // tag 84
        public String aip;           // tag 82
        public String iad;           // tag 9F10
        public String appVersion;    // tag 9F09
        public String cvmResults;    // tag 9F34

        // transaction
        public String amount;        // tag 9F02, 12 chiffres
        public String otherAmount = "000000000000"; // 9F03
        public String currency;      // 5F2A, ex. 0504
        public String countryCode;   // 9F1A, ex. 0504
        public String date;          // 9A, AAMMJJ
        public String txType = "00"; // 9C
        public String tvr = "0000008000";           // 95
        public String unpredictable; // 9F37, 4 octets ; genere si absent
        public String termCapabilities = "E0F8C8";  // 9F33
        public String termType = "22";               // 9F35
    }

    /** Resultat : le DE55 assemble et l'ARQC, pour tracabilite. */
    public static class EmvResult {
        public byte[] de55;
        public String de55Hex;
        public String arqc;
        public String iccKeyKcv;      // KCV de la cle ICC, pour debug
        public String sessionKeyKcv;
    }

    // ==================================================================
    //  POINT D'ENTREE
    // ==================================================================

    public EmvResult build(EmvInput in) throws Exception {
        // 1. MDK claire, en memoire uniquement
        byte[] mdk = mdkClear(in);

        // 2. cle ICC (EMV Option A)
        byte[] icc = deriveIcc(mdk, in.pan, in.psn);

        // 3. cle de session (EMV CSK)
        byte[] sk = deriveSessionKey(icc, in.atc, unpredictable(in));

        // 4. donnees CDOL1 et ARQC
        byte[] cdol = buildCdol1(in);
        byte[] arqc = retailMac(sk, cdol);
        String arqcHex = ISOUtil.hexString(arqc).toUpperCase();

        // 5. assemblage du DE55
        traceArqc("MEMBRE", in, icc, sk, cdol, arqcHex);
        byte[] de55 = assembleDe55(in, arqcHex);

        EmvResult r = new EmvResult();
        r.arqc          = arqcHex;
        r.de55          = de55;
        r.de55Hex       = ISOUtil.hexString(de55).toUpperCase();
        r.iccKeyKcv     = kcv3(icc);
        r.sessionKeyKcv = kcv3(sk);

        log.info("[EMV] ARQC={} ATC={} PAN=***{} (iccKcv={} skKcv={})",
                arqcHex, in.atc,
                in.pan.length() >= 4 ? in.pan.substring(in.pan.length()-4) : in.pan,
                r.iccKeyKcv, r.sessionKeyKcv);
        return r;
    }

    /**
     * Recalcule uniquement l'ARQC, pour la VALIDATION cote reseau.
     * Meme derivation que build(), sans reassembler le DE55.
     */
    public String recomputeArqc(EmvInput in) throws Exception {
        byte[] mdk = mdkClear(in);
        byte[] icc = deriveIcc(mdk, in.pan, in.psn);
        byte[] sk  = deriveSessionKey(icc, in.atc, unpredictable(in));
        byte[] cdol = buildCdol1(in);
        byte[] arqc = retailMac(sk, cdol);
        String arqcHex = ISOUtil.hexString(arqc).toUpperCase();
        traceArqc("RESEAU", in, icc, sk, cdol, arqcHex);
        return arqcHex;
    }

    // ==================================================================
    //  CRYPTO
    // ==================================================================

    /** MDK claire, reconstruite depuis le LMK. En memoire, jamais en base. */
    private byte[] mdkClear(EmvInput in) throws Exception {
        return hsm.exposeClearKey("MDK", in.mdkUnderLmk, in.mdkKcv, in.mdkLenBytes);
    }

    /** 3DES ECB. Accepte une cle 16 octets (etendue en 24) ou 24. */
    private byte[] tdesEcb(byte[] key16or24, byte[] data, int mode) throws Exception {
        byte[] k = key16or24;
        if (k.length == 16) {
            k = new byte[24];
            System.arraycopy(key16or24, 0, k, 0, 16);
            System.arraycopy(key16or24, 0, k, 16, 8);
        }
        Cipher c = Cipher.getInstance("DESede/ECB/NoPadding");
        c.init(mode, new SecretKeySpec(k, "DESede"));
        return c.doFinal(data);
    }

    /**
     * Cle ICC — EMV Book 2, Option A.
     *   Y = (PAN || PSN), 16 chiffres de droite du PAN concatenes au PSN
     *   ICC_gauche  = 3DES( Y )      sous MDK
     *   ICC_droite  = 3DES( Y XOR FF..FF ) sous MDK
     */
    /** Parite impaire sur chaque octet — exigee par M/Chip pour les cles derivees. */
    private byte[] oddParity(byte[] in) {
        byte[] out = new byte[in.length];
        for (int i = 0; i < in.length; i++) {
            int v = in[i] & 0xFE;
            int bits = Integer.bitCount(v);
            out[i] = (byte) (v | ((bits % 2 == 0) ? 1 : 0));
        }
        return out;
    }

    private byte[] deriveIcc(byte[] mdk, String pan, String psn) throws Exception {
        String digits = pan.replaceAll("[^0-9]", "");
        String right16;
        if (digits.length() >= 16) {
            right16 = digits.substring(digits.length() - 16);
        } else {
            StringBuilder sb = new StringBuilder();
            while (sb.length() + digits.length() < 16) sb.append('0');
            sb.append(digits);
            right16 = sb.toString();
        }
        String y = right16 + (psn == null || psn.isEmpty() ? "00" : psn);
        // Y fait 18 chiffres -> on garde les 16 de droite comme bloc de 8 octets
        y = y.substring(y.length() - 16);
        byte[] yb = ISOUtil.hex2byte(y);

        byte[] left  = tdesEcb(mdk, yb, Cipher.ENCRYPT_MODE);

        byte[] yInv = new byte[yb.length];
        for (int i = 0; i < yb.length; i++) yInv[i] = (byte) (yb[i] ^ 0xFF);
        byte[] right = tdesEcb(mdk, yInv, Cipher.ENCRYPT_MODE);

        byte[] icc = new byte[16];
        System.arraycopy(left,  0, icc, 0, 8);
        System.arraycopy(right, 0, icc, 8, 8);
        return oddParity(icc);   // M/Chip : parite impaire
    }

    /**
     * Cle de session — EMV Common Session Key (CSK).
     *   R = ATC(2) || F0 || 00 00 00 00 00   pour la moitie gauche
     *   R = ATC(2) || 0F || 00 00 00 00 00   pour la moitie droite
     *   SK = 3DES(R) sous ICC, par moitie
     */
    /**
     * Cle de session — M/Chip 4 (CVN 10).
     *   R_gauche = ATC || F0 || 00 || UN
     *   R_droite = ATC || 0F || 00 || UN
     *   SK = 3DES(R) sous la cle ICC, par moitie
     *
     * Difference avec le CSK generique : les 4 derniers octets portent
     * l'Unpredictable Number, pas des zeros.
     */
    private byte[] deriveSessionKey(byte[] icc, int atc, String unHex) throws Exception {
        byte[] atcB = new byte[]{ (byte)((atc >> 8) & 0xFF), (byte)(atc & 0xFF) };
        byte[] un   = ISOUtil.hex2byte(unHex);

        byte[] rL = new byte[8];
        rL[0] = atcB[0]; rL[1] = atcB[1]; rL[2] = (byte)0xF0; rL[3] = 0x00;
        System.arraycopy(un, 0, rL, 4, 4);

        byte[] rR = new byte[8];
        rR[0] = atcB[0]; rR[1] = atcB[1]; rR[2] = (byte)0x0F; rR[3] = 0x00;
        System.arraycopy(un, 0, rR, 4, 4);

        byte[] skL = tdesEcb(icc, rL, Cipher.ENCRYPT_MODE);
        byte[] skR = tdesEcb(icc, rR, Cipher.ENCRYPT_MODE);

        byte[] sk = new byte[16];
        System.arraycopy(skL, 0, sk, 0, 8);
        System.arraycopy(skR, 0, sk, 8, 8);
        return sk;
    }

    /**
     * ARQC — MAC ISO 9797-1 algorithme 3 (Retail MAC).
     * DES-CBC avec la moitie gauche sur tous les blocs, puis une passe
     * finale 3DES (decrypt droite, encrypt gauche) sur le dernier bloc.
     * Bourrage EMV : 0x80 puis des 0x00 jusqu'au multiple de 8.
     */
    private byte[] retailMac(byte[] sk16, byte[] data) throws Exception {
        byte[] kL = new byte[8], kR = new byte[8];
        System.arraycopy(sk16, 0, kL, 0, 8);
        System.arraycopy(sk16, 8, kR, 0, 8);

        byte[] padded = emvPad(data);

        Cipher desEnc = Cipher.getInstance("DES/CBC/NoPadding");
        desEnc.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kL, "DES"),
                new javax.crypto.spec.IvParameterSpec(new byte[8]));
        byte[] enc = desEnc.doFinal(padded);

        byte[] lastBlock = new byte[8];
        System.arraycopy(enc, enc.length - 8, lastBlock, 0, 8);

        // 3DES sur le dernier bloc : D(kR) puis E(kL)
        Cipher dR = Cipher.getInstance("DES/ECB/NoPadding");
        dR.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kR, "DES"));
        byte[] step = dR.doFinal(lastBlock);

        Cipher eL = Cipher.getInstance("DES/ECB/NoPadding");
        eL.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kL, "DES"));
        return eL.doFinal(step);
    }

    private byte[] emvPad(byte[] data) {
        int len = data.length + 1;
        while (len % 8 != 0) len++;
        byte[] p = new byte[len];
        System.arraycopy(data, 0, p, 0, data.length);
        p[data.length] = (byte) 0x80;
        return p;
    }

    // ==================================================================
    //  CDOL1 ET DE55
    // ==================================================================

    /**
     * Donnees signees par l'ARQC, ordre M/Chip standard :
     *   9F02 9F03 9F1A 95 5F2A 9A 9C 9F37 82 9F36 9F10
     */
    private byte[] buildCdol1(EmvInput in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(ISOUtil.hex2byte(pad(in.amount, 12)));       // 9F02
        b.write(ISOUtil.hex2byte(pad(in.otherAmount, 12)));  // 9F03
        b.write(ISOUtil.hex2byte(pad(in.countryCode, 4)));   // 9F1A
        b.write(ISOUtil.hex2byte(pad(in.tvr, 10)));          // 95
        b.write(ISOUtil.hex2byte(pad(in.currency, 4)));      // 5F2A
        b.write(ISOUtil.hex2byte(pad(in.date, 6)));          // 9A
        b.write(ISOUtil.hex2byte(pad(in.txType, 2)));        // 9C
        b.write(ISOUtil.hex2byte(unpredictable(in)));        // 9F37
        b.write(ISOUtil.hex2byte(pad(in.aip, 4)));           // 82
        b.write(ISOUtil.hex2byte(atcHex(in.atc)));           // 9F36
        // 9F10 : pour CVN 10 / M/Chip 4, seuls 6 octets de l'IAD entrent
        // dans les donnees signees (octets 3 a 8 du tag complet).
        String iadFull = in.iad == null ? "" : in.iad.replaceAll("[^0-9A-Fa-f]", "");
        String iadPart = iadFull.length() >= 16 ? iadFull.substring(4, 16) : iadFull;
        b.write(ISOUtil.hex2byte(iadPart));                  // 9F10 partiel
        return b.toByteArray();
    }

    /** Assemble les 18 tags EMV en TLV binaire. */
    private byte[] assembleDe55(EmvInput in, String arqcHex) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        tlv(b, "9F33", in.termCapabilities);       // Terminal Capabilities
        tlv(b, "95",   pad(in.tvr, 10));           // TVR
        tlv(b, "9F37", unpredictable(in));         // Unpredictable Number
        tlv(b, "9F26", arqcHex);                   // ARQC
        tlv(b, "9F36", atcHex(in.atc));            // ATC
        tlv(b, "82",   pad(in.aip, 4));            // AIP
        tlv(b, "9C",   pad(in.txType, 2));         // Transaction Type
        tlv(b, "9F1A", pad(in.countryCode, 4));    // Terminal Country
        tlv(b, "9A",   pad(in.date, 6));           // date
        tlv(b, "9F02", pad(in.amount, 12));        // montant
        tlv(b, "5F2A", pad(in.currency, 4));       // devise
        tlv(b, "9F03", pad(in.otherAmount, 12));   // autre montant
        tlv(b, "9F27", "80");                      // CID = ARQC
        tlv(b, "9F34", pad(in.cvmResults, 6));     // CVM Results
        tlv(b, "9F35", in.termType);               // Terminal Type
        tlv(b, "9F53", "52");                      // Transaction Category Code
        tlv(b, "84",   in.aid);                    // AID
        tlv(b, "9F09", pad(in.appVersion, 4));     // App Version
        tlv(b, "9F41", "00000001");                // Transaction Sequence Counter
        tlv(b, "9F10", in.iad);                    // IAD (en dernier)
        return b.toByteArray();
    }

    /** Ecrit un tag TLV : tag, longueur (1 octet), valeur. */
    private void tlv(ByteArrayOutputStream b, String tagHex, String valHex) throws Exception {
        if (valHex == null || valHex.isEmpty()) return;
        byte[] tag = ISOUtil.hex2byte(tagHex);
        byte[] val = ISOUtil.hex2byte(valHex);
        b.write(tag);
        b.write(val.length & 0xFF);
        b.write(val);
    }

    // ==================================================================
    //  OUTILS
    // ==================================================================

    private String unpredictable(EmvInput in) {
        if (in.unpredictable != null && in.unpredictable.length() == 8) return in.unpredictable;
        byte[] r = new byte[4];
        new java.security.SecureRandom().nextBytes(r);
        in.unpredictable = ISOUtil.hexString(r).toUpperCase();
        return in.unpredictable;
    }

    private String atcHex(int atc) {
        return String.format("%04X", atc & 0xFFFF);
    }

    private String pad(String hex, int lenChars) {
        if (hex == null) hex = "";
        hex = hex.replaceAll("[^0-9A-Fa-f]", "");
        if (hex.length() > lenChars) return hex.substring(hex.length() - lenChars);
        StringBuilder sb = new StringBuilder();
        while (sb.length() + hex.length() < lenChars) sb.append('0');
        sb.append(hex);
        return sb.toString();
    }

    /** Trace detaillee du calcul ARQC, pour comparer membre et reseau. */
    private void traceArqc(String cote, EmvInput in, byte[] icc, byte[] sk,
                           byte[] cdol, String arqc) {
        try {
            log.info("[EMV-TRACE:{}] PAN=***{} PSN={} ATC={}", cote,
                    in.pan != null && in.pan.length()>=4 ? in.pan.substring(in.pan.length()-4) : in.pan,
                    in.psn, in.atc);
            log.info("[EMV-TRACE:{}]   9F02={} 9F03={} 9F1A={} 95={} 5F2A={}", cote,
                    pad(in.amount,12), pad(in.otherAmount,12), pad(in.countryCode,4),
                    pad(in.tvr,10), pad(in.currency,4));
            log.info("[EMV-TRACE:{}]   9A={} 9C={} 9F37={} 82={} 9F36={} 9F10={}", cote,
                    pad(in.date,6), pad(in.txType,2),
                    in.unpredictable, pad(in.aip,4), atcHex(in.atc), in.iad);
            log.info("[EMV-TRACE:{}]   ICC.kcv={} SK.kcv={}", cote, kcv3(icc), kcv3(sk));
            log.info("[EMV-TRACE:{}]   CDOL1={}", cote, ISOUtil.hexString(cdol).toUpperCase());
            log.info("[EMV-TRACE:{}]   ARQC={}", cote, arqc);
        } catch (Exception e) {
            log.warn("[EMV-TRACE:{}] trace KO : {}", cote, e.getMessage());
        }
    }

    private String kcv3(byte[] key) throws Exception {
        byte[] enc = tdesEcb(key, new byte[8], Cipher.ENCRYPT_MODE);
        return ISOUtil.hexString(enc).substring(0, 6).toUpperCase();
    }
}
