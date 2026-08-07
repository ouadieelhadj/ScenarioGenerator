import { Type } from '@angular/core';
import { ScreenLoader } from './screen-loader';

type RegisteredScreenLoader = () => Promise<Type<unknown>>;
const clearing: RegisteredScreenLoader = () => import('../../features/clearing/clearing-workspace.component').then(m => m.ClearingWorkspaceComponent);
const moduleWorkspace: RegisteredScreenLoader = () => import('../../features/module-workspace/module-workspace.component').then(m => m.ModuleWorkspaceComponent);
const visa: RegisteredScreenLoader = () => import('../../features/visa/visa-workspace.component').then(m => m.VisaWorkspaceComponent);
const registry: Record<string, RegisteredScreenLoader> = {
  CLEARING_TRANSACTIONS: clearing,
  CLEARING_FILES: clearing,
  CLEARING_RECONCILIATION: clearing,
  CLEARING_CHARGEBACKS: clearing,
  CLEARING_EOD: clearing,
  CLEARING_ACCOUNTING: clearing,
  MODULE_WORKSPACE: moduleWorkspace,
  VISA_WORKSPACE: visa,
};

export const switchScreenLoader: ScreenLoader = componentKey => {
  const loader = componentKey ? registry[componentKey] : undefined;
  return loader ? loader() : Promise.resolve(null);
};
