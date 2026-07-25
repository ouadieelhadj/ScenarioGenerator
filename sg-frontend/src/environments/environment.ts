export const environment = {
  production: false,
  // API REST de l'orchestrateur (campagnes, executions, users, auth)
  apiOrchestrator: 'http://localhost:8080',
  // API DMAS acquereur (KEK, PEK, achats, crypto)
  apiAcquirer: 'http://localhost:8084',
  // API DMAS issuer (sign-on, cartes)
  apiIssuer: 'http://localhost:8501',
};
