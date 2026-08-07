import { Type } from '@angular/core';

type RegisteredScreenLoader = () => Promise<Type<unknown>>;

const clearingWorkspace: RegisteredScreenLoader = () => import('../../features/clearing/clearing-workspace.component')
  .then(module => module.ClearingWorkspaceComponent);

const moduleWorkspace: RegisteredScreenLoader = () => import('../../features/module-workspace/module-workspace.component')
  .then(module => module.ModuleWorkspaceComponent);

const visaWorkspace: RegisteredScreenLoader = () => import('../../features/visa/visa-workspace.component')
  .then(module => module.VisaWorkspaceComponent);

const REGISTRY: Record<string, RegisteredScreenLoader> = {
  CLEARING_TRANSACTIONS: clearingWorkspace,
  CLEARING_FILES: clearingWorkspace,
  CLEARING_RECONCILIATION: clearingWorkspace,
  CLEARING_CHARGEBACKS: clearingWorkspace,
  CLEARING_EOD: clearingWorkspace,
  CLEARING_ACCOUNTING: clearingWorkspace,
  MODULE_WORKSPACE: moduleWorkspace,
  VISA_WORKSPACE: visaWorkspace,
};

export function loadRegisteredScreen(componentKey?: string): Promise<Type<unknown> | null> {
  const loader = componentKey ? REGISTRY[componentKey] : undefined;
  return loader ? loader() : Promise.resolve(null);
}

export const legacyScreenLoader = loadRegisteredScreen;
