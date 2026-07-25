export interface User {
  id?: number;
  login: string;
  email: string;
  role: string;
  active: boolean;
}

export interface CreateUserRequest {
  login: string;
  password?: string;  // requis a la creation, optionnel a l'edition
  email: string;
  role: string;
}

// Roles connus de la plateforme (pas d'endpoint pour les lister)
export const ROLES = ['ADMIN', 'EXPLOITATION', 'OBSERVATEUR'] as const;

