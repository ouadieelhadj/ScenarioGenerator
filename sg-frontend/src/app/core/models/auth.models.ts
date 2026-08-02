// Reponse du POST /auth/login
export interface LoginResponse {
  token: string;
  login: string;
  role: string;
  expiresIn: number;
}

export interface LoginRequest {
  login: string;
  password: string;
}

// Contenu decode du JWT (claims)
export interface JwtClaims {
  sub: string;            // login
  role: string;           // ex. "ADMIN"
  permissions: string[];  // ex. ["CAMPAIGN_CREATE", "TPS_RUN"]
  iat: number;
  exp: number;
}

// Utilisateur courant (derive du token)
export interface CurrentUser {
  login: string;
  role: string;
  permissions: string[];
}

// Les permissions connues du back (catalogue)
export enum Permission {
  USER_MANAGE = 'USER_MANAGE',
  ROLE_MANAGE = 'ROLE_MANAGE',
  CATALOG_MANAGE = 'CATALOG_MANAGE',
  CAMPAIGN_VIEW = 'CAMPAIGN_VIEW',
  CAMPAIGN_CREATE = 'CAMPAIGN_CREATE',
  CAMPAIGN_GENERATE = 'CAMPAIGN_GENERATE',
  CAMPAIGN_EXPORT = 'CAMPAIGN_EXPORT',
  CARD_PROVISION = 'CARD_PROVISION',
  CAMPAIGN_REPLAY = 'CAMPAIGN_REPLAY',
  TPS_CREATE = 'TPS_CREATE',
  TPS_RUN = 'TPS_RUN',
  EXECUTION_VIEW = 'EXECUTION_VIEW',
  DEPLOYMENT_VIEW = 'DEPLOYMENT_VIEW',
  DEPLOYMENT_PREPARE = 'DEPLOYMENT_PREPARE',
  DEPLOYMENT_APPROVE = 'DEPLOYMENT_APPROVE',
  DEPLOYMENT_EXECUTE = 'DEPLOYMENT_EXECUTE',
  DEPLOYMENT_ROLLBACK = 'DEPLOYMENT_ROLLBACK',
}
