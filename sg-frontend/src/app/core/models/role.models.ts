export interface PermissionSummary {
  id: number;
  code: string;
  label: string;
  category: string;
}

export interface RoleSummary {
  id: number;
  code: string;
  label: string;
  description?: string;
  permissions: PermissionSummary[];
}
