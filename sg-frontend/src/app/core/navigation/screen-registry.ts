import { Type } from '@angular/core';

type ScreenLoader = () => Promise<Type<unknown>>;

const clearingWorkspace: ScreenLoader = () => import('../../features/clearing/clearing-workspace.component')
  .then(module => module.ClearingWorkspaceComponent);

const moduleWorkspace: ScreenLoader = () => import('../../features/module-workspace/module-workspace.component')
  .then(module => module.ModuleWorkspaceComponent);

const REGISTRY: Record<string, ScreenLoader> = {
  CLEARING_TRANSACTIONS: clearingWorkspace,
  CLEARING_FILES: clearingWorkspace,
  CLEARING_RECONCILIATION: clearingWorkspace,
  CLEARING_CHARGEBACKS: clearingWorkspace,
  CLEARING_EOD: clearingWorkspace,
  CLEARING_ACCOUNTING: clearingWorkspace,
  MODULE_WORKSPACE: moduleWorkspace,
};

export function loadRegisteredScreen(componentKey?: string): Promise<Type<unknown> | null> {
  const loader = componentKey ? REGISTRY[componentKey] : undefined;
  return loader ? loader() : Promise.resolve(null);
}
