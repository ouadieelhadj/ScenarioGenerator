import { Type } from '@angular/core';
import { ScreenLoader } from './screen-loader';

type RegisteredScreenLoader = () => Promise<Type<unknown>>;
const moduleWorkspace: RegisteredScreenLoader = () => import('../../features/module-workspace/module-workspace.component').then(m => m.ModuleWorkspaceComponent);
const registry: Record<string, RegisteredScreenLoader> = { MODULE_WORKSPACE: moduleWorkspace };

export const switchLabScreenLoader: ScreenLoader = componentKey => {
  const loader = componentKey ? registry[componentKey] : undefined;
  return loader ? loader() : Promise.resolve(null);
};
