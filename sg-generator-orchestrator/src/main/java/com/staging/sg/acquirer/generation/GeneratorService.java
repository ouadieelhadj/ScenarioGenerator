package com.staging.sg.acquirer.generation;

import com.staging.sg.common.entity.*;
import com.staging.sg.common.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Moteur de génération de transactions monétiques.
 * Pour chaque transaction, génère les champs SÉLECTIONNÉS par la campagne
 * (campaign.selectedFields), selon la gen_strategy de chaque champ (iso_field_catalog).
 */
@Service
public class GeneratorService {

    private static final Logger log = LoggerFactory.getLogger(GeneratorService.class);

    private final CampaignRepository campaignRepo;
    private final GeneratedTransactionRepository txRepo;
    private final IsoFieldCatalogRepository fieldRepo;
    private final BinRangeRepository binRepo;

    public GeneratorService(CampaignRepository campaignRepo,
                            GeneratedTransactionRepository txRepo,
                            IsoFieldCatalogRepository fieldRepo,
                            BinRangeRepository binRepo) {
        this.campaignRepo = campaignRepo;
        this.txRepo = txRepo;
        this.fieldRepo = fieldRepo;
        this.binRepo = binRepo;
    }

    // Données pour DE43 (nom/ville/pays)
    private static final String[] MERCHANTS = {"SHOP","STORE","MARKET","CAFE","RESTAURANT","HOTEL","STATION","PHARMA"};
    private static final Map<String,String[]> CITIES = Map.of(
        "FR", new String[]{"PARIS","LYON","MARSEILLE","LILLE","NICE"},
        "BE", new String[]{"BRUXELLES","ANVERS","GAND"},
        "ES", new String[]{"MADRID","BARCELONA","SEVILLA"},
        "DE", new String[]{"BERLIN","MUNICH","HAMBURG"}
    );
    private static final Map<String,String> CURRENCY_NUM = Map.of("EUR","978","USD","840","GBP","826");
    private static final Map<String,String> COUNTRY_NUM = Map.of("FR","250","BE","056","ES","724","DE","276");

    private int stanCounter = new Random().nextInt(900000);

    @Transactional
    public int generate(Long campaignId) {
        Campaign c = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campagne introuvable : " + campaignId));

        // Champs sélectionnés (CSV) ; si vide -> tous les champs activés du catalogue
        Set<String> selected = parseSelected(c.getSelectedFields());
        if (selected.isEmpty()) {
            fieldRepo.findByEnabledTrueOrderByDisplayOrderAsc().forEach(f -> selected.add(f.getFieldCode()));
        }

        // BIN à utiliser
        BinRange bin = (c.getBinRangeId() != null) ? binRepo.findById(c.getBinRangeId()).orElse(null) : null;

        // Purge des anciennes transactions de la campagne (regénération)
        txRepo.deleteByCampaignId(campaignId);

        int n = c.getTxCount() != null ? c.getTxCount() : 100;
        List<GeneratedTransaction> batch = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            batch.add(buildOne(c, selected, bin));
        }
        txRepo.saveAll(batch);

        c.setStatus("GENERATED");
        campaignRepo.save(c);

        log.info("[GEN] Campagne {} : {} transactions générées ({} champs sélectionnés)",
                campaignId, n, selected.size());
        return n;
    }

    private GeneratedTransaction buildOne(Campaign c, Set<String> sel, BinRange bin) {
        GeneratedTransaction t = new GeneratedTransaction();
        t.setCampaignId(c.getId());
        t.setTxType(c.getTxType());

        LocalDateTime now = LocalDateTime.now();

        if (sel.contains("DE2"))  t.setDe2Pan(genPan(bin));
        if (sel.contains("DE3"))  t.setDe3ProcessingCode(procCode(c.getTxType()));
        if (sel.contains("DE4"))  t.setDe4Amount(genAmount(c.getAmountMin(), c.getAmountMax()));
        if (sel.contains("DE7"))  t.setDe7TransmissionDt(now.format(DateTimeFormatter.ofPattern("MMddHHmmss")));
        if (sel.contains("DE11")) t.setDe11Stan(genStan());
        if (sel.contains("DE12")) t.setDe12LocalTime(now.format(DateTimeFormatter.ofPattern("HHmmss")));
        if (sel.contains("DE13")) t.setDe13LocalDate(now.format(DateTimeFormatter.ofPattern("MMdd")));
        if (sel.contains("DE14")) t.setDe14Expiry(genExpiry());
        if (sel.contains("DE18")) t.setDe18Mcc(c.getMcc() != null ? c.getMcc() : "5999");
        if (sel.contains("DE22")) t.setDe22PosEntryMode(posEntry(c.getChannel()));
        if (sel.contains("DE25")) t.setDe25PosCondition(posCondition(c.getChannel()));
        if (sel.contains("DE32")) t.setDe32AcquirerId("00000111111");
        if (sel.contains("DE37")) t.setDe37Rrn(genRrn());
        if (sel.contains("DE41")) t.setDe41TerminalId(genTerminal());
        if (sel.contains("DE42")) t.setDe42MerchantId(genMerchant());
        if (sel.contains("DE43")) t.setDe43MerchantNameLoc(genDe43(c.getCountry()));
        if (sel.contains("DE49")) t.setDe49Currency(CURRENCY_NUM.getOrDefault(c.getCurrency(), "978"));

        return t;
    }

    // ----- Stratégies de génération -----

    /** PAN selon BIN précis ou plage, complété + Luhn. */
    private String genPan(BinRange bin) {
        int len = (bin != null && bin.getPanLength() != null) ? bin.getPanLength() : 16;
        String prefix;
        if (bin == null) {
            prefix = String.valueOf(51 + ThreadLocalRandom.current().nextInt(5)); // 51-55
        } else if (Boolean.TRUE.equals(bin.getIsRange())) {
            prefix = prefixFromRange(bin.getCode());
        } else {
            prefix = bin.getCode();
        }
        StringBuilder sb = new StringBuilder(prefix);
        while (sb.length() < len - 1) sb.append(ThreadLocalRandom.current().nextInt(10));
        sb.append(luhnCheckDigit(sb.toString()));
        return sb.toString();
    }

    /** Tire un BIN dans une plage "51-55" ou "2221-2720". */
    private String prefixFromRange(String code) {
        String[] parts = code.split("-");
        if (parts.length != 2) return code;
        long lo = Long.parseLong(parts[0].trim());
        long hi = Long.parseLong(parts[1].trim());
        long v = lo + (long)(ThreadLocalRandom.current().nextDouble() * (hi - lo + 1));
        return String.valueOf(v);
    }

    /** Clé de Luhn pour compléter le PAN. */
    private int luhnCheckDigit(String partial) {
        int sum = 0; boolean dbl = true;
        for (int i = partial.length() - 1; i >= 0; i--) {
            int d = partial.charAt(i) - '0';
            if (dbl) { d *= 2; if (d > 9) d -= 9; }
            sum += d; dbl = !dbl;
        }
        return (10 - (sum % 10)) % 10;
    }

    private String procCode(String type) {
        String sf1 = switch (type == null ? "purchase" : type) {
            case "withdrawal" -> "01";
            case "purchase_cashback" -> "09";
            case "cash_disbursement" -> "17";
            case "refund" -> "20";
            case "payment" -> "28";
            case "balance_inquiry" -> "30";
            case "transfer" -> "40";
            default -> "00"; // purchase
        };
        return sf1 + "0000";
    }

    private Long genAmount(Long min, Long max) {
        long lo = min != null ? min : 1000L;
        long hi = max != null ? max : 50000L;
        if (hi <= lo) return lo;
        return lo + (long)(ThreadLocalRandom.current().nextDouble() * (hi - lo));
    }

    private synchronized String genStan() {
        stanCounter = (stanCounter + 1) % 1000000;
        return String.format("%06d", stanCounter);
    }

    private String genExpiry() {
        LocalDate d = LocalDate.now().plusYears(1 + ThreadLocalRandom.current().nextInt(4));
        return String.format("%02d%02d", d.getYear() % 100, d.getMonthValue());
    }

    private String posEntry(String channel) {
        return switch (channel == null ? "POS" : channel) {
            case "ECOMMERCE", "VAD" -> "010";
            case "ATM" -> "051";
            default -> "051"; // POS puce
        };
    }

    private String posCondition(String channel) {
        return switch (channel == null ? "POS" : channel) {
            case "ECOMMERCE", "VAD" -> "59";
            case "ATM" -> "02";
            default -> "00";
        };
    }

    private String genRrn() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(ThreadLocalRandom.current().nextInt(10));
        return sb.toString();
    }

    private String genTerminal() {
        return String.format("TERM%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private String genMerchant() {
        return String.format("MERCH%010d", ThreadLocalRandom.current().nextInt(1000000000));
    }

    /** DE43 = [nom 22][espace][ville 13][espace][pays 3] = 40 caractères. */
    private String genDe43(String country) {
        String cc = country != null ? country : "FR";
        String name = MERCHANTS[ThreadLocalRandom.current().nextInt(MERCHANTS.length)] + " " +
                      String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        String[] cities = CITIES.getOrDefault(cc, new String[]{"PARIS"});
        String city = cities[ThreadLocalRandom.current().nextInt(cities.length)];
        return pad(name, 22) + " " + pad(city, 13) + " " + pad(cc, 3);
    }

    private String pad(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s.substring(0, n);
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) sb.append(' ');
        return sb.toString();
    }

    private Set<String> parseSelected(String csv) {
        Set<String> s = new HashSet<>();
        if (csv != null && !csv.isBlank()) {
            for (String p : csv.split(",")) {
                String f = p.trim().toUpperCase();
                if (!f.isEmpty()) s.add(f);
            }
        }
        return s;
    }
}
