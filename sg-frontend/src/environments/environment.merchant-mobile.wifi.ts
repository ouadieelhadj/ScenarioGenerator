const pcHost = '192.168.1.86';

export const environment = {
  production: false,
  apiOrchestrator: `http://${pcHost}:8080`,
  apiOnboarding: `http://${pcHost}:8570`,
  apiAcquirer: `http://${pcHost}:8570`,
  apiIssuer: `http://${pcHost}:8080`,
};
