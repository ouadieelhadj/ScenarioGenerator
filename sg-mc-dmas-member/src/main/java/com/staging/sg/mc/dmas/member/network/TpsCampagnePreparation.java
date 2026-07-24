package com.staging.sg.mc.dmas.member.network;

import com.staging.sg.mc.dmas.member.api.LoadTestRequest;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Phase de PREPARATION d'un test de charge : construite UNE fois avant le lancement des threads.
 * Porte les regles de variation (PAN, montant, ...) et produit a la demande les donnees
 * d'une transaction (nextTx()). Point d'extension unique : ajouter un champ variable = l'ajouter ici.
 *
 * Les threads ne decident de rien : ils appellent nextTx() et envoient ce qui est prepare.
 */
public class TpsCampagnePreparation {

    /** Donnees prêtes d'une transaction (deja tirees / formatees). */
    public static class PreparedTx {
        public final String pan;
        public final String pin;       // null si pas de PIN
        public final String amount;    // formate 12 chiffres
        public final String entryMode;
        public PreparedTx(String pan, String pin, String amount, String entryMode) {
            this.pan = pan;
            this.pin = pin;
            this.amount = amount;
            this.entryMode = entryMode;
        }
    }

    private final List<LoadTestRequest.CardEntry> cards;  // pool de cartes (peut etre vide/null)
    private final String  fixedPan;
    private final boolean withPin;

    private final String  fixedAmount;   // utilise si pas de plage
    private final Long     amountMin;
    private final Long     amountMax;
    private final boolean  amountVariable;

    private final String fixedEntryMode;

    public TpsCampagnePreparation(LoadTestRequest req) {
        this.cards    = req.cards;
        this.fixedPan = req.pan;
        this.withPin  = req.withPin;

        this.fixedAmount = req.amount;
        this.amountMin   = req.amountMin;
        this.amountMax   = req.amountMax;
        this.amountVariable = (req.amountMin != null && req.amountMax != null
                               && req.amountMax >= req.amountMin);

        this.fixedEntryMode = req.entryMode;
    }

    /** Produit les donnees d'UNE transaction (appelee par thread, tirage par transaction). */
    public PreparedTx nextTx() {
        // ----- PAN / PIN : tirage dans le pool si fourni, sinon PAN fixe -----
        String pan, pin;
        if (cards != null && !cards.isEmpty()) {
            LoadTestRequest.CardEntry c = cards.get(ThreadLocalRandom.current().nextInt(cards.size()));
            pan = c.pan;
            pin = c.pin;
        } else {
            pan = fixedPan;
            pin = null;
        }

        // ----- Montant : tirage dans [min, max] si plage definie, sinon fixe -----
        String amount;
        if (amountVariable) {
            long amt = ThreadLocalRandom.current().nextLong(amountMin, amountMax + 1);
            amount = String.format("%012d", amt);
        } else {
            amount = fixedAmount;
        }

        // ----- Entry mode : fixe pour l'instant (extension future : LIST) -----
        String entryMode = fixedEntryMode;

        return new PreparedTx(pan, pin, amount, entryMode);
    }
}
