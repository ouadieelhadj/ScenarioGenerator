# Lancement et bootstrap DMAS / SWAM

Ces scripts Git Bash démarrent chaque module s'il n'est pas déjà actif,
attendent son API puis reforment ses clés sous le LMK local.

```bash
# DMAS
bash tests/dmas/mastercard/start-and-bootstrap.sh
bash tests/dmas/member/start-and-bootstrap.sh

# SWAM : réseau avant membre
bash tests/swam/issuer/start-and-bootstrap.sh
bash tests/swam/acquirer/start-and-bootstrap.sh
```

Pour Way4 vers notre réseau DMAS, seul le script Mastercard est requis.
Les secrets sont demandés sans affichage ou fournis par variables
`ADMIN_PASSWORD`, `KEK_CLEAR`, `PEK_CLEAR` et `MDK_CLEAR`.

Les autres paramètres surchargeables sont `SG_ROOT`, `JAVA_BIN`,
`REST_PORT`, `BANK_CODE`, `MEMBER_GROUP_ID` et `INTERFACE_ID`.

Ne jamais recopier `key_under_lmk` entre machines. Exécuter le script sur
le poste cible afin de reformer les clés sous son propre LMK.
