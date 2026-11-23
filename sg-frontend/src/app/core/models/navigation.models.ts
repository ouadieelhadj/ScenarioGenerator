export interface NavigationItem {
  id: number;
  type: 'MENU' | 'SUBMENU' | 'SCREEN';
  code: string;
  labelKey: string;
  icon?: string;
  route?: string;
  componentKey?: string;
  context: Record<string, unknown>;
  children: NavigationItem[];
}

export interface ModuleNavigation {
  code: string;
  labelKey: string;
  icon?: string;
  children: NavigationItem[];
}

export interface NavigationResponse {
  modules: ModuleNavigation[];
  legacyFallback: boolean;
}
