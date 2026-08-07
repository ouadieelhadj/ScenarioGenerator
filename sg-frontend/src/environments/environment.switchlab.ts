const gateway = 'http://localhost:8090';

export const environment = {
  production: false,
  // Entrée BFF unique : aucun backend technique n'est appelé directement.
  apiOrchestrator: gateway,
  apiAcquirer: gateway,
  apiIssuer: gateway,
};
