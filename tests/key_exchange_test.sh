#!/bin/bash

# Paramètres de connexion et d'authentification
ISSUER_URL="http://localhost:8501"
ACQUIRER_URL="http://localhost:8084"
USER="admin"
PASSWORD="Admin123!"

# Fonction pour obtenir un jeton JWT
function get_jwt {
  local url=$1
  local credentials=$(echo -n "$USER:$PASSWORD" | base64)
  curl -s -X POST "$url/auth/login" -H "Content-Type: application/json" -H "Authorization: Basic $credentials" | jq -r '.token'
}

# Récupération des jetons JWT
ISSUER_JWT=$(get_jwt $ISSUER_URL)
ACQUIRER_JWT=$(get_jwt $ACQUIRER_URL)

echo "=== Initialisation du key exchange côté acquirer ==="
INIT_RESPONSE=$(curl -s -X POST "$ACQUIRER_URL/api/key-exchange/init" -H "Authorization: Bearer $ACQUIRER_JWT")
KEK=$(echo $INIT_RESPONSE | jq -r '.kek')
echo "KEK générée : $KEK"
echo

echo "=== Génération d'une PEK côté acquirer ==="
PEK_RESPONSE=$(curl -s -X POST "$ACQUIRER_URL/api/pek/generate" -H "Authorization: Bearer $ACQUIRER_JWT")
ENCRYPTED_PEK=$(echo $PEK_RESPONSE | jq -r '.encryptedPek')
PEK_KCV=$(echo $PEK_RESPONSE | jq -r '.kcv')
echo "PEK chiffrée : $ENCRYPTED_PEK"
echo "KCV de la PEK : $PEK_KCV"  
echo

echo "=== Envoi de la demande de key exchange à l'issuer ==="
KEY_EXCHANGE_REQUEST=$(curl -s -X POST "$ACQUIRER_URL/api/key-exchange/send" -H "Authorization: Bearer $ACQUIRER_JWT" -H "Content-Type: application/json" -d "{\"encryptedPek\":\"$ENCRYPTED_PEK\",\"kcv\":\"$PEK_KCV\"}")
echo $KEY_EXCHANGE_REQUEST
echo

echo "=== Traitement de la demande de key exchange côté issuer ==="
KEY_EXCHANGE_RESPONSE=$(curl -s -X POST "$ISSUER_URL/api/key-exchange/process" -H "Authorization: Bearer $ISSUER_JWT" -H "Content-Type: application/json" -d "$KEY_EXCHANGE_REQUEST")
echo $KEY_EXCHANGE_RESPONSE
echo

echo "=== Vérification du résultat côté acquirer ==="
VERIFY_RESPONSE=$(curl -s -X POST "$ACQUIRER_URL/api/key-exchange/verify" -H "Authorization: Bearer $ACQUIRER_JWT" -H "Content-Type: application/json" -d "$KEY_EXCHANGE_RESPONSE")
echo $VERIFY_RESPONSE
