import { Permission } from '../core/models/auth.models';

export interface MenuItem {
  labelKey: string;      // cle de traduction (ex. 'menu.dashboard')
  icon: string;
  route: string;
  permissions?: string[];
}

export const MENU_ITEMS: MenuItem[] = [
  { labelKey: 'menu.dashboard', icon: 'pi pi-home', route: '/dashboard' },
  { labelKey: 'menu.campaignGeneration', icon: 'pi pi-plus-circle', route: '/campaign-generation',
    permissions: [Permission.CAMPAIGN_CREATE, Permission.CAMPAIGN_GENERATE] },
  { labelKey: 'menu.campaignOrchestration', icon: 'pi pi-play', route: '/campaign-orchestration',
    permissions: [Permission.TPS_RUN, Permission.CAMPAIGN_REPLAY] },
  { labelKey: 'menu.executions', icon: 'pi pi-chart-line', route: '/executions',
    permissions: [Permission.EXECUTION_VIEW, Permission.CAMPAIGN_VIEW] },
  { labelKey: 'menu.dmas', icon: 'pi pi-credit-card', route: '/dmas',
    permissions: [Permission.CARD_PROVISION] },
  { labelKey: 'menu.admin', icon: 'pi pi-cog', route: '/admin',
    permissions: [Permission.USER_MANAGE, Permission.ROLE_MANAGE, Permission.CATALOG_MANAGE] },
  { labelKey: 'menu.config', icon: 'pi pi-sliders-h', route: '/config',
    permissions: [Permission.USER_MANAGE] },
  { labelKey: 'menu.help', icon: 'pi pi-question-circle', route: '/help' },
];

