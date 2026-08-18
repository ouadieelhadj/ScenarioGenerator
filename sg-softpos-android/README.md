# FuturPayment SoftPOS Android

Squelette Android natif destiné à recevoir le SDK SoftPOS certifié. Le build
`debug` peut utiliser l'adaptateur laboratoire. Aucun adaptateur de laboratoire
n'existe dans `src/main` ou `src/release`, ce qui interdit son inclusion dans
un APK de production.

Le projet fixe Android 12 comme minimum initial, désactive le trafic HTTP clair,
interdit les captures d'écran et sépare l'interface fournisseur du parcours de
paiement. Le client HTTP réel, l'OIDC, le stockage matériel des jetons et le SDK
certifié seront raccordés lorsque le fournisseur sera choisi.

Ce squelette n'est pas qualifié PCI MPoC et ne permet pas un paiement NFC réel.
