const gateway = 'http://localhost:8091';

export const environment = {
  production: false,
  // Entrée BFF unique : aucun backend technique n'est appelé directement.
  apiOrchestrator: gateway,
  apiAcquirer: gateway,
  apiIssuer: gateway,
};
