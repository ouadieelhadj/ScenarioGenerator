#!/bin/bash
# Verifie que les autorisations sont passees par la connexion permanente jPOS.
cd "$(dirname "$0")/../.."   # racine projet
echo "=== ACQUEREUR : transport jpos + reponses ==="
grep -E "Transport = JPOS|<- 0110" logs/dmas-acquirer.log | tail -8
echo ""
echo "=== ISSUER : reception 0100 + decision ==="
grep -E "0100 AUTORISATION recue|DEBIT|APPROUVE|0110 construit|SQL Error" logs/dmas-issuer.log | tail -10
