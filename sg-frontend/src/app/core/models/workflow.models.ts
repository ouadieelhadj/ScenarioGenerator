export interface WorkflowRequestSummary {
  id: number;
  moduleCode: string;
  operationType: string;
  objectReference: string;
  status: string;
  createdBy: string;
  createdAt: string;
  expiresAt?: string;
}
