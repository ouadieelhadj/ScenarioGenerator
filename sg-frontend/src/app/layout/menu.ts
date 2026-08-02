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
    permissions: [Permission.USER_MANAGE, Permission.ROLE_MANAGE, Permission.CATALOG_MANAGE, Permission.DEPLOYMENT_VIEW],
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
      {
        code: 'DEPLOYMENTS',
        labelKey: 'menu.deployments',
        icon: 'pi pi-cloud-upload',
        permissions: [Permission.DEPLOYMENT_VIEW],
        children: [
          { code: 'DEPLOYMENT_OVERVIEW', labelKey: 'deployment.menu.overview', icon: 'pi pi-home',
            route: '/administration/deployments', permissions: [Permission.DEPLOYMENT_VIEW] },
          { code: 'DEPLOYMENT_CLIENTS', labelKey: 'deployment.menu.clients', icon: 'pi pi-building',
            route: '/administration/deployments/clients', permissions: [Permission.DEPLOYMENT_VIEW] },
          { code: 'DEPLOYMENT_ENVIRONMENTS', labelKey: 'deployment.menu.environments', icon: 'pi pi-server',
            route: '/administration/deployments/environments', permissions: [Permission.DEPLOYMENT_VIEW] },
          { code: 'DEPLOYMENT_MODULES', labelKey: 'deployment.menu.modules', icon: 'pi pi-box',
            route: '/administration/deployments/modules', permissions: [Permission.DEPLOYMENT_VIEW] },
          { code: 'DEPLOYMENT_LICENSES', labelKey: 'deployment.menu.licenses', icon: 'pi pi-file-pdf',
            route: '/administration/deployments/licenses', permissions: [Permission.DEPLOYMENT_VIEW] },
          { code: 'DEPLOYMENT_EXECUTIONS', labelKey: 'deployment.menu.executions', icon: 'pi pi-list-check',
            route: '/administration/deployments/executions', permissions: [Permission.DEPLOYMENT_VIEW] },
        ],
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
