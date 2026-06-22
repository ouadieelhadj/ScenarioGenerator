package com.staging.sg.acquirer.orchestration;

import com.staging.sg.common.entity.GeneratedTransaction;
import com.staging.sg.common.repository.GeneratedTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Provisionne les cartes côté issuer DMAS (port 8501) pour les PAN d'une campagne.
 * Étape séparée, déclenchée manuellement avant l'exécution.
 * Solde généreux fixe pour que toutes les transactions soient approuvées.
 */
@Service
public class CardProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(CardProvisioningService.class);

    private final GeneratedTransactionRepository txRepo;
    private final DmasClient dmasClient;

    @Value("${dmas.issuer.base-url:http://localhost:8501}") private String issuerUrl;
    @Value("${dmas.login:admin}")        private String login;
    @Value("${dmas.password:Admin123!}") private String password;
    @Value("${dmas.provision.balance:100000000}") private Long defaultBalance; // 1 000 000.00
    @Value("${dmas.provision.pin:1234}")          private String defaultPin;

    public CardProvisioningService(GeneratedTransactionRepository txRepo, DmasClient dmasClient) {
        this.txRepo = txRepo;
        this.dmasClient = dmasClient;
    }

    public Map<String,Object> provision(Long campaignId) {
        List<GeneratedTransaction> txs = txRepo.findByCampaignId(campaignId);
        if (txs.isEmpty()) {
            throw new IllegalStateException("Aucune transaction pour la campagne " + campaignId);
        }

        // PAN uniques (+ expiry associée)
        Map<String,String> panExpiry = new LinkedHashMap<>();
        for (GeneratedTransaction t : txs) {
            if (t.getDe2Pan() != null && !panExpiry.containsKey(t.getDe2Pan())) {
                panExpiry.put(t.getDe2Pan(), t.getDe14Expiry() != null ? t.getDe14Expiry() : "2812");
            }
        }

        String token = dmasClient.ensureToken(issuerUrl, login, password);

        int created = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String,String> e : panExpiry.entrySet()) {
            try {
                Map<String,Object> card = new LinkedHashMap<>();
                card.put("pan", e.getKey());
                card.put("pin", defaultPin);
                card.put("balance", defaultBalance);
                card.put("currency", "840");
                card.put("expiry", e.getValue());
                dmasClient.postJson(issuerUrl, "/api/admin/dmas/cards", token, card);
                created++;
            } catch (Exception ex) {
                failed++;
                if (errors.size() < 5) errors.add(e.getKey() + " : " + ex.getMessage());
            }
        }

        log.info("[PROVISION] Campagne {} : {} cartes créées, {} échecs ({} PAN uniques)",
                campaignId, created, failed, panExpiry.size());

        Map<String,Object> r = new LinkedHashMap<>();
        r.put("campaignId", campaignId);
        r.put("uniquePans", panExpiry.size());
        r.put("created", created);
        r.put("failed", failed);
        r.put("balance", defaultBalance);
        if (!errors.isEmpty()) r.put("sampleErrors", errors);
        return r;
    }
}
