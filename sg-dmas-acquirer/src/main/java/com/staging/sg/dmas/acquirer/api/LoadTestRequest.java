package com.staging.sg.dmas.acquirer.api;

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
}
