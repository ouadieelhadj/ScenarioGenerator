import { InjectionToken } from '@angular/core';

export type PortalProductCode = 'LEGACY' | 'SWITCHLAB' | 'SWITCH';

export interface PortalModuleGroup {
  code: string;
  labelKey: string;
  icon: string;
  moduleCodes: readonly string[];
}

export interface PortalProductConfig {
  code: PortalProductCode;
  brand: string;
  subtitleKey: string;
  allowedModuleCodes: readonly string[];
  moduleGroups: readonly PortalModuleGroup[];
  leadingMenuCodes: readonly string[];
}

export const PORTAL_PRODUCT = new InjectionToken<PortalProductConfig>('PORTAL_PRODUCT');

export const LEGACY_PRODUCT: PortalProductConfig = {
  code: 'LEGACY',
  brand: 'ScenarioGenerator',
  subtitleKey: 'login.subtitle',
  allowedModuleCodes: [],
  leadingMenuCodes: ['DASHBOARD', 'CAMPAIGNS', 'WORKFLOW', 'ADMINISTRATION', 'HELP'],
  moduleGroups: [{
    code: 'BUSINESS_MODULES',
    labelKey: 'menu.businessModules',
    icon: 'pi pi-box',
    moduleCodes: [],
  }],
};

export const SWITCHLAB_PRODUCT: PortalProductConfig = {
  code: 'SWITCHLAB',
  brand: 'FuturPayment SwitchLab',
  subtitleKey: 'product.switchLabSubtitle',
  allowedModuleCodes: ['LAB_SIMULATORS'],
  leadingMenuCodes: ['DASHBOARD'],
  moduleGroups: [{
    code: 'SIMULATORS',
    labelKey: 'menu.simulators',
    icon: 'pi pi-bolt',
    moduleCodes: ['LAB_SIMULATORS'],
  }],
};

export const SWITCH_PRODUCT: PortalProductConfig = {
  code: 'SWITCH',
  brand: 'FuturPayment Switch',
  subtitleKey: 'product.switchSubtitle',
  allowedModuleCodes: [
    'SERVER_POS',
    'ACQUIRING',
    'CARD_ISSUING',
    'DMAS_MEMBER',
    'DMCS_MEMBER',
    'SWAM_MEMBER',
    'VISA_ONLINE_MEMBER',
    'VISA_BASE2_MEMBER',
  ],
  leadingMenuCodes: ['DASHBOARD', 'TRANSACTIONS'],
  moduleGroups: [
    {
      code: 'ACQUIRING',
      labelKey: 'menu.acquiring',
      icon: 'pi pi-shopping-cart',
      moduleCodes: ['SERVER_POS', 'ACQUIRING'],
    },
    {
      code: 'ISSUING',
      labelKey: 'menu.issuing',
      icon: 'pi pi-credit-card',
      moduleCodes: ['CARD_ISSUING'],
    },
    {
      code: 'NETWORKS_CLEARING',
      labelKey: 'menu.networksClearing',
      icon: 'pi pi-sitemap',
      moduleCodes: [
        'DMAS_MEMBER',
        'DMCS_MEMBER',
        'SWAM_MEMBER',
        'VISA_ONLINE_MEMBER',
        'VISA_BASE2_MEMBER',
      ],
    },
  ],
};
