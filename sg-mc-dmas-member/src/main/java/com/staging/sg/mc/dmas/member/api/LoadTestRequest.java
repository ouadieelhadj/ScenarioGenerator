package com.staging.sg.mc.dmas.member.api;

import java.util.List;

/** Parametres d'un test de charge de la connexion permanente jPOS. */
public class LoadTestRequest {
    public String  pan;
    public String  amount          = "000000000000";
    public String  entryMode       = "CARD_PRESENT";
    public Integer count;                 // mode A : nb fixe de tx
    public Integer durationSeconds;       // mode B : duree
    public Integer targetTps;             // cadence cible (optionnel)
    public Integer concurrency     = 50;
    public Integer timeoutSeconds  = 10;
    public String  mti             = "0100";   // MTI resolu par l'orchestrateur (defaut = comportement actuel)

    // Montant variable par transaction : si amountMin/amountMax non null, tire dans [min, max]
    public Long amountMin;
    public Long amountMax;

    // ----- v1.1.0 : tirage de cartes + PIN -----
    public boolean withPin = false;       // si true, chiffre le PIN block (DE52) sous PEK
    public boolean withEmv = false;      // si true, construit le DE55 EMV (ARQC)
    public List<CardEntry> cards;         // si non vide, tire une carte au hasard PAR transaction

    /** Une carte du pool de tirage : PAN + PIN clair. */
    public static class CardEntry {
        public String pan;
        public String pin;
        public CardEntry() {}
        public CardEntry(String pan, String pin) { this.pan = pan; this.pin = pin; }
    }
}
