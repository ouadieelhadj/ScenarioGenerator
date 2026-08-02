import { Permission } from '../core/models/auth.models';

export interface MenuItem {
  code: string;
  labelKey: string;
  icon: string;
  route?: string;
  permissions?: string[];
  children?: MenuItem[];
}

/**
 * Navigation transverse toujours disponible. Les modules métier chargés par
 * /api/me/navigation complètent ce menu sans le remplacer.
 */
export const COMMON_MENU_ITEMS: MenuItem[] = [
  {
    code: 'DASHBOARD',
    labelKey: 'menu.dashboard',
    icon: 'pi pi-home',
    route: '/dashboard',
  },
  {
    code: 'CAMPAIGNS',
    labelKey: 'menu.campaigns',
    icon: 'pi pi-chart-bar',
    children: [
      {
        code: 'CAMPAIGN_GENERATION',
        labelKey: 'menu.campaignGeneration',
        icon: 'pi pi-plus-circle',
        route: '/campaign-generation',
        permissions: [Permission.CAMPAIGN_CREATE, Permission.CAMPAIGN_GENERATE],
      },
      {
        code: 'CAMPAIGN_ORCHESTRATION',
        labelKey: 'menu.campaignOrchestration',
        icon: 'pi pi-play',
        route: '/campaign-orchestration',
        permissions: [Permission.TPS_RUN, Permission.CAMPAIGN_REPLAY],
      },
      {
        code: 'EXECUTIONS',
        labelKey: 'menu.executions',
        icon: 'pi pi-chart-line',
        route: '/executions',
        permissions: [Permission.EXECUTION_VIEW, Permission.CAMPAIGN_VIEW],
      },
    ],
  },
  {
    code: 'WORKFLOW',
    labelKey: 'menu.workflow',
    icon: 'pi pi-check-square',
    children: [
      {
        code: 'MY_OPERATIONS',
        labelKey: 'menu.myOperations',
        icon: 'pi pi-send',
        route: '/workflow/my-operations',
      },
      {
        code: 'MY_APPROVALS',
        labelKey: 'menu.myApprovals',
        icon: 'pi pi-verified',
        route: '/workflow/my-approvals',
      },
    ],
  },
  {
    code: 'ADMINISTRATION',
    labelKey: 'menu.admin',
    icon: 'pi pi-cog',
    permissions: [Permission.USER_MANAGE, Permission.ROLE_MANAGE, Permission.CATALOG_MANAGE],
    children: [
      {
        code: 'USERS',
        labelKey: 'menu.users',
        icon: 'pi pi-users',
        route: '/administration/users',
        permissions: [Permission.USER_MANAGE],
      },
      {
        code: 'ROLES',
        labelKey: 'menu.roles',
        icon: 'pi pi-shield',
        route: '/administration/roles',
        permissions: [Permission.ROLE_MANAGE],
      },
      {
        code: 'CONFIGURATION',
        labelKey: 'menu.config',
        icon: 'pi pi-sliders-h',
        route: '/config',
        permissions: [Permission.USER_MANAGE],
      },
    ],
  },
  {
    code: 'HELP',
    labelKey: 'menu.help',
    icon: 'pi pi-question-circle',
    route: '/help',
  },
];
