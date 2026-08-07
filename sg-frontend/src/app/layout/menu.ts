import { Permission } from '../core/models/auth.models';
import { PortalProductCode } from '../core/product/product.config';

export interface MenuItem {
  code: string;
  labelKey: string;
  icon: string;
  route?: string;
  permissions?: string[];
  roles?: string[];
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

function commonItem(code: string): MenuItem {
  const item = COMMON_MENU_ITEMS.find(candidate => candidate.code === code);
  if (!item) throw new Error(`Missing common menu item: ${code}`);
  return item;
}

const SWITCHLAB_MENU_ITEMS: MenuItem[] = [
  commonItem('DASHBOARD'),
  { code: 'SWITCHLAB_POS', labelKey: 'menu.switchLabPos', icon: 'pi pi-desktop', route: '/lab/pos' },
  { code: 'SWITCHLAB_TEST_CENTER', labelKey: 'menu.switchLabTestCenter', icon: 'pi pi-chart-bar', route: '/lab/test-center' },
  { code: 'SWITCHLAB_ONLINE', labelKey: 'menu.switchLabOnline', icon: 'pi pi-globe', route: '/lab/online' },
  { code: 'SWITCHLAB_CLEARING', labelKey: 'menu.switchLabClearing', icon: 'pi pi-file-import', route: '/lab/clearing' },
  { code: 'SWITCHLAB_ECOMMERCE', labelKey: 'menu.switchLabEcommerce', icon: 'pi pi-shopping-cart', route: '/lab/ecommerce' },
  { code: 'SWITCHLAB_INDUSTRIALIZATION', labelKey: 'menu.switchLabIndustrialization', icon: 'pi pi-shield', route: '/lab/industrialization' },
  { ...commonItem('CAMPAIGNS'), labelKey: 'menu.campaignsTests' },
  { code: 'OPERATIONS', labelKey: 'menu.operations', icon: 'pi pi-desktop', route: '/product/operations' },
  commonItem('ADMINISTRATION'),
  commonItem('HELP'),
];

const switchAdministration: MenuItem = {
  ...commonItem('ADMINISTRATION'),
  children: [
    {
      code: 'INTERFACES',
      labelKey: 'menu.interfaces',
      icon: 'pi pi-link',
      route: '/product/interfaces',
      permissions: [Permission.CATALOG_MANAGE],
    },
    ...(commonItem('ADMINISTRATION').children ?? []),
  ],
};

const SWITCH_MENU_ITEMS: MenuItem[] = [
  commonItem('DASHBOARD'),
  { code: 'ACQUIRING', labelKey: 'menu.acquiring', icon: 'pi pi-shopping-cart', route: '/product/acquiring' },
  { code: 'ISSUING', labelKey: 'menu.issuing', icon: 'pi pi-credit-card', route: '/product/issuing' },
  { code: 'NETWORKS_CLEARING', labelKey: 'menu.networksClearing', icon: 'pi pi-sitemap', children: [
    { code: 'NETWORKS', labelKey: 'menu.switchLabOnline', icon: 'pi pi-globe', route: '/product/networks' },
    { code: 'CLEARING', labelKey: 'menu.switchLabClearing', icon: 'pi pi-file-import', route: '/product/clearing' },
  ] },
  { code: 'ECOMMERCE', labelKey: 'menu.switchLabEcommerce', icon: 'pi pi-shopping-bag', route: '/product/ecommerce' },
  { code: 'INDUSTRIALIZATION', labelKey: 'menu.switchLabIndustrialization', icon: 'pi pi-shield', route: '/product/industrialization' },
  { code: 'TRANSACTIONS', labelKey: 'menu.transactions', icon: 'pi pi-arrow-right-arrow-left', route: '/product/transactions' },
  { code: 'OPERATIONS', labelKey: 'menu.operations', icon: 'pi pi-desktop', route: '/product/operations' },
  switchAdministration,
  commonItem('HELP'),
];

const MERCHANT_PORTAL_MENU_ITEMS: MenuItem[] = [
  { code: 'MERCHANT_DASHBOARD', labelKey: 'merchantPortal.menu.dashboard', icon: 'pi pi-home', route: '/merchant/dashboard' },
  { code: 'COMMERCIAL_PROSPECTS', labelKey: 'merchantPortal.menu.newProspect', icon: 'pi pi-user-plus', route: '/commercial/prospects/new', permissions: [Permission.ONBOARDING_PROSPECT_CREATE], roles: ['COMMERCIAL', 'ADMIN'] },
  { ...commonItem('WORKFLOW'), permissions: [Permission.ONBOARDING_APPROVE, Permission.ONBOARDING_KYC_REVIEW], roles: ['CHECKER', 'BACK_OFFICE', 'ADMIN'] },
  commonItem('HELP'),
];

export function menuItemsFor(product: PortalProductCode): MenuItem[] {
  if (product === 'MERCHANT_PORTAL') return MERCHANT_PORTAL_MENU_ITEMS;
  if (product === 'SWITCHLAB') return SWITCHLAB_MENU_ITEMS;
  if (product === 'SWITCH') return SWITCH_MENU_ITEMS;
  return COMMON_MENU_ITEMS;
}
