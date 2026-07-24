import { Routes } from '@angular/router';

export const SCENARIO_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./scenario-list/scenario-list.component')
        .then(m => m.ScenarioListComponent)
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./wizard/scenario-wizard.component')
        .then(m => m.ScenarioWizardComponent)
  },
  {
    path: ':id/edit',
    loadComponent: () =>
      import('./wizard/scenario-wizard.component')
        .then(m => m.ScenarioWizardComponent)
  },
  {
    path: ':id/execute',
    loadComponent: () =>
      import('./execution/scenario-execution.component')
        .then(m => m.ScenarioExecutionComponent)
  },
  {
    path: ':id/results',
    loadComponent: () =>
      import('./results/scenario-results.component')
        .then(m => m.ScenarioResultsComponent)
  },
  {
    path: 'library',
    loadComponent: () =>
      import('./library/scenario-library.component')
        .then(m => m.ScenarioLibraryComponent)
  }
];
