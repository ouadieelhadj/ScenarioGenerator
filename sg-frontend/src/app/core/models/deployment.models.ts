export type TargetOs = 'WINDOWS' | 'LINUX';
export type ShellType = 'GIT_BASH' | 'POWERSHELL' | 'CMD_WINDOWS' | 'BASH_LINUX';
export type DatabaseType = 'NONE' | 'POSTGRESQL' | 'ORACLE';
export type ModuleSide = 'MEMBER' | 'SIMULATOR';

export interface DeploymentModule {
  code: string;
  label: string;
  side: ModuleSide;
  artifactId: string;
  mainClass: string;
  defaultPort?: number;
  requiredVariables: string[];
}

export interface DeploymentCatalog {
  modules: DeploymentModule[];
  targetOperatingSystems: TargetOs[];
  compatibleShells: Record<TargetOs, ShellType[]>;
  databaseTypes: DatabaseType[];
}

export interface DeploymentClient {
  id?: number;
  code: string;
  legalName: string;
  commercialName?: string;
  countryCode: string;
  currencyCode?: string;
  status?: string;
}

export interface DeploymentEnvironment {
  id?: number;
  clientId: number;
  code: string;
  environmentType: string;
  targetOs: TargetOs;
  shellType: ShellType;
  shellExecutable?: string;
  deploymentRoot: string;
  javaExecutable: string;
  databaseType: DatabaseType;
  databaseHost?: string;
  databasePort?: number;
  databaseName?: string;
  databaseSchema?: string;
  databaseUser?: string;
  databasePasswordSecretRef?: string;
  oracleServiceName?: string;
  oracleSid?: string;
  memberModules: string[];
  simulatorModules: string[];
  variableReferences: Record<string, string>;
  membersBundlePath?: string;
  simulatorsBundlePath?: string;
  licensePath?: string;
  licensePublicKeyPath?: string;
}

export interface PreflightCheck {
  code: string;
  status: 'OK' | 'WARNING' | 'BLOCKING';
  detail: string;
}

export interface PreflightReport {
  executionId: string;
  checkedAt: string;
  verdict: 'READY' | 'WARNING' | 'BLOCKING';
  checks: PreflightCheck[];
}

export interface DeploymentLicense {
  id: string;
  clientId: number;
  environmentId: number;
  status: 'PENDING' | 'ACTIVE' | 'REJECTED' | 'REVOKED' | 'EXPIRED';
  validFrom: string;
  validUntil: string;
  memberModules: string[];
  simulatorModules: string[];
  bundleVersion: string;
  technicalLicensePath?: string;
  pdfPath?: string;
  technicalSha256?: string;
  preparedBy: string;
  approvedBy?: string;
  createdAt: string;
  approvedAt?: string;
}

export interface DeploymentExecution {
  id: string;
  environmentId: number;
  action: string;
  status: string;
  requestedBy: string;
  approvedBy?: string;
  startedAt?: string;
  finishedAt?: string;
  detail: Record<string, unknown>;
  createdAt: string;
}
