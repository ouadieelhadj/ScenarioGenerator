-- Catalogue frontend global ScenarioGenerator V1.
-- Migration additive et rejouable, à appliquer après 18_portal_rbac_workflow.sql.

INSERT INTO app_module(code, label_key, icon, display_order, active) VALUES
 ('SERVER_POS',    'modules.serverPos',    'pi pi-desktop',     20, TRUE),
 ('ACQUIRING',     'modules.acquiring',    'pi pi-shopping-cart',30, TRUE),
 ('CARD_ISSUING',  'modules.issuing',      'pi pi-credit-card', 40, TRUE),
 ('SWAM_MEMBER',   'modules.swamMember',   'pi pi-building',    50, TRUE),
 ('DMAS_MEMBER',   'modules.dmasMember',   'pi pi-building',    60, TRUE),
 ('DMCS_MEMBER',   'modules.dmcsMember',   'pi pi-building',    70, TRUE),
 ('LAB_SIMULATORS','modules.simulators',   'pi pi-bolt',        80, TRUE)
ON CONFLICT (code) DO UPDATE SET
 label_key=EXCLUDED.label_key, icon=EXCLUDED.icon,
 display_order=EXCLUDED.display_order, active=TRUE;

-- Les anciens modules de démonstration sont remplacés par les domaines validés.
UPDATE app_module SET active=FALSE
 WHERE code IN ('DMAS_MASTERCARD','SWAM_LIS_MEMBER','SWAM_LIS_SWITCH');

WITH screens(code, route_template) AS (VALUES
 ('SERVER_POS_OVERVIEW',       '/modules/:moduleCode/overview'),
 ('SERVER_POS_TERMINALS',      '/modules/:moduleCode/terminals'),
 ('SERVER_POS_MERCHANTS',      '/modules/:moduleCode/merchants'),
 ('SERVER_POS_ROUTING',        '/modules/:moduleCode/routing'),
 ('SERVER_POS_KEYS',           '/modules/:moduleCode/keys'),
 ('SERVER_POS_TRANSACTIONS',   '/modules/:moduleCode/transactions'),
 ('SERVER_POS_LOGS',           '/modules/:moduleCode/logs'),
 ('ACQUIRING_OVERVIEW',        '/modules/:moduleCode/overview'),
 ('ACQUIRING_CONTRACTS',       '/modules/:moduleCode/contracts'),
 ('ACQUIRING_TERMINALS',       '/modules/:moduleCode/terminals'),
 ('ACQUIRING_MERCHANTS',       '/modules/:moduleCode/merchants'),
 ('ACQUIRING_POS',             '/modules/:moduleCode/pos-transactions'),
 ('ACQUIRING_ECOMMERCE',       '/modules/:moduleCode/ecommerce-transactions'),
 ('ACQUIRING_ROUTING',         '/modules/:moduleCode/routing'),
 ('ACQUIRING_SETTLEMENT',      '/modules/:moduleCode/settlement'),
 ('ISSUING_OVERVIEW',          '/modules/:moduleCode/overview'),
 ('ISSUING_CUSTOMERS',         '/modules/:moduleCode/customers'),
 ('ISSUING_CONTRACTS',         '/modules/:moduleCode/contracts'),
 ('ISSUING_PRODUCTS',          '/modules/:moduleCode/products'),
 ('ISSUING_CARDS',             '/modules/:moduleCode/cards'),
 ('ISSUING_AUTHORIZATIONS',    '/modules/:moduleCode/authorizations'),
 ('ISSUING_CONTROLS',          '/modules/:moduleCode/card-controls'),
 ('ISSUING_STATEMENTS',        '/modules/:moduleCode/statements'),
 ('SWAM_OVERVIEW',             '/modules/:moduleCode/overview'),
 ('SWAM_NETWORK',              '/modules/:moduleCode/network'),
 ('SWAM_KEYS',                 '/modules/:moduleCode/keys'),
 ('SWAM_TRANSACTIONS',         '/modules/:moduleCode/transactions'),
 ('SWAM_RECONCILIATION',       '/modules/:moduleCode/reconciliation'),
 ('DMAS_OVERVIEW',             '/modules/:moduleCode/overview'),
 ('DMAS_NETWORK',              '/modules/:moduleCode/network'),
 ('DMAS_KEYS',                 '/modules/:moduleCode/keys'),
 ('DMAS_TRANSACTIONS',         '/modules/:moduleCode/transactions'),
 ('DMAS_RECONCILIATION',       '/modules/:moduleCode/reconciliation'),
 ('DMCS_OVERVIEW',             '/modules/:moduleCode/overview'),
 ('DMCS_NETWORK',              '/modules/:moduleCode/network'),
 ('DMCS_KEYS',                 '/modules/:moduleCode/keys'),
 ('DMCS_TRANSACTIONS',         '/modules/:moduleCode/transactions'),
 ('DMCS_RECONCILIATION',       '/modules/:moduleCode/reconciliation'),
 ('LAB_POS_SIMULATOR',         '/lab/:moduleCode/pos-simulator'),
 ('LAB_MERCHANT_LOCAL',        '/lab/:moduleCode/merchant-site-local'),
 ('LAB_MERCHANT_INTERNATIONAL','/lab/:moduleCode/merchant-site-international'),
 ('LAB_3DS_NETWORK',           '/lab/:moduleCode/3ds-network'),
 ('LAB_CARD_NETWORK',          '/lab/:moduleCode/card-network')
)
INSERT INTO screen_definition(code, route_template, component_key, shared_screen, active)
SELECT code, route_template, 'MODULE_WORKSPACE', FALSE, TRUE FROM screens
ON CONFLICT (code) DO UPDATE SET
 route_template=EXCLUDED.route_template, component_key=EXCLUDED.component_key, active=TRUE;

-- Un groupe métier racine par module permet de conserver une navigation hiérarchique.
INSERT INTO navigation_node(module_id,node_type,code,label_key,icon,display_order,active)
SELECT m.id,'MENU',m.code||'_OPERATIONS','moduleMenus.operations','pi pi-folder',10,TRUE
  FROM app_module m
 WHERE m.code IN ('SERVER_POS','ACQUIRING','CARD_ISSUING','SWAM_MEMBER','DMAS_MEMBER','DMCS_MEMBER')
ON CONFLICT (module_id,code) DO UPDATE SET active=TRUE;

INSERT INTO navigation_node(module_id,node_type,code,label_key,icon,display_order,active)
SELECT m.id,'MENU','LAB_SIMULATORS_CATALOG','moduleMenus.simulators','pi pi-bolt',10,TRUE
  FROM app_module m WHERE m.code='LAB_SIMULATORS'
ON CONFLICT (module_id,code) DO UPDATE SET active=TRUE;

WITH catalog(module_code,screen_code,node_code,label_key,icon,display_order) AS (VALUES
 ('SERVER_POS','SERVER_POS_OVERVIEW','SERVER_POS_OVERVIEW','screens.overview','pi pi-home',10),
 ('SERVER_POS','SERVER_POS_TERMINALS','SERVER_POS_TERMINALS','screens.terminals','pi pi-desktop',20),
 ('SERVER_POS','SERVER_POS_MERCHANTS','SERVER_POS_MERCHANTS','screens.merchants','pi pi-shop',30),
 ('SERVER_POS','SERVER_POS_ROUTING','SERVER_POS_ROUTING','screens.routing','pi pi-sitemap',40),
 ('SERVER_POS','SERVER_POS_KEYS','SERVER_POS_KEYS','screens.keys','pi pi-key',50),
 ('SERVER_POS','SERVER_POS_TRANSACTIONS','SERVER_POS_TRANSACTIONS','screens.transactions','pi pi-list',60),
 ('SERVER_POS','SERVER_POS_LOGS','SERVER_POS_LOGS','screens.logs','pi pi-file',70),
 ('ACQUIRING','ACQUIRING_OVERVIEW','ACQUIRING_OVERVIEW','screens.overview','pi pi-home',10),
 ('ACQUIRING','ACQUIRING_CONTRACTS','ACQUIRING_CONTRACTS','screens.contracts','pi pi-file-edit',20),
 ('ACQUIRING','ACQUIRING_TERMINALS','ACQUIRING_TERMINALS','screens.terminals','pi pi-desktop',30),
 ('ACQUIRING','ACQUIRING_MERCHANTS','ACQUIRING_MERCHANTS','screens.merchants','pi pi-shop',40),
 ('ACQUIRING','ACQUIRING_POS','ACQUIRING_POS','screens.posTransactions','pi pi-credit-card',50),
 ('ACQUIRING','ACQUIRING_ECOMMERCE','ACQUIRING_ECOMMERCE','screens.ecommerceTransactions','pi pi-globe',60),
 ('ACQUIRING','ACQUIRING_ROUTING','ACQUIRING_ROUTING','screens.routing','pi pi-sitemap',70),
 ('ACQUIRING','ACQUIRING_SETTLEMENT','ACQUIRING_SETTLEMENT','screens.settlement','pi pi-calculator',80),
 ('CARD_ISSUING','ISSUING_OVERVIEW','ISSUING_OVERVIEW','screens.overview','pi pi-home',10),
 ('CARD_ISSUING','ISSUING_CUSTOMERS','ISSUING_CUSTOMERS','screens.customers','pi pi-users',20),
 ('CARD_ISSUING','ISSUING_CONTRACTS','ISSUING_CONTRACTS','screens.contracts','pi pi-file-edit',30),
 ('CARD_ISSUING','ISSUING_PRODUCTS','ISSUING_PRODUCTS','screens.products','pi pi-box',40),
 ('CARD_ISSUING','ISSUING_CARDS','ISSUING_CARDS','screens.cards','pi pi-credit-card',50),
 ('CARD_ISSUING','ISSUING_AUTHORIZATIONS','ISSUING_AUTHORIZATIONS','screens.authorizations','pi pi-check-circle',60),
 ('CARD_ISSUING','ISSUING_CONTROLS','ISSUING_CONTROLS','screens.cardControls','pi pi-sliders-h',70),
 ('CARD_ISSUING','ISSUING_STATEMENTS','ISSUING_STATEMENTS','screens.statements','pi pi-book',80),
 ('SWAM_MEMBER','SWAM_OVERVIEW','SWAM_OVERVIEW','screens.overview','pi pi-home',10),
 ('SWAM_MEMBER','SWAM_NETWORK','SWAM_NETWORK','screens.network','pi pi-wifi',20),
 ('SWAM_MEMBER','SWAM_KEYS','SWAM_KEYS','screens.keys','pi pi-key',30),
 ('SWAM_MEMBER','SWAM_TRANSACTIONS','SWAM_TRANSACTIONS','screens.transactions','pi pi-list',40),
 ('SWAM_MEMBER','SWAM_RECONCILIATION','SWAM_RECONCILIATION','screens.reconciliation','pi pi-sync',50),
 ('DMAS_MEMBER','DMAS_OVERVIEW','DMAS_OVERVIEW','screens.overview','pi pi-home',10),
 ('DMAS_MEMBER','DMAS_NETWORK','DMAS_NETWORK','screens.network','pi pi-wifi',20),
 ('DMAS_MEMBER','DMAS_KEYS','DMAS_KEYS','screens.keys','pi pi-key',30),
 ('DMAS_MEMBER','DMAS_TRANSACTIONS','DMAS_TRANSACTIONS','screens.transactions','pi pi-list',40),
 ('DMAS_MEMBER','DMAS_RECONCILIATION','DMAS_RECONCILIATION','screens.reconciliation','pi pi-sync',50),
 ('DMCS_MEMBER','DMCS_OVERVIEW','DMCS_OVERVIEW','screens.overview','pi pi-home',10),
 ('DMCS_MEMBER','DMCS_NETWORK','DMCS_NETWORK','screens.network','pi pi-wifi',20),
 ('DMCS_MEMBER','DMCS_KEYS','DMCS_KEYS','screens.keys','pi pi-key',30),
 ('DMCS_MEMBER','DMCS_TRANSACTIONS','DMCS_TRANSACTIONS','screens.transactions','pi pi-list',40),
 ('DMCS_MEMBER','DMCS_RECONCILIATION','DMCS_RECONCILIATION','screens.reconciliation','pi pi-sync',50),
 ('LAB_SIMULATORS','LAB_POS_SIMULATOR','LAB_POS_SIMULATOR','screens.posSimulator','pi pi-desktop',10),
 ('LAB_SIMULATORS','LAB_MERCHANT_LOCAL','LAB_MERCHANT_LOCAL','screens.merchantSiteLocal','pi pi-shop',20),
 ('LAB_SIMULATORS','LAB_MERCHANT_INTERNATIONAL','LAB_MERCHANT_INTERNATIONAL','screens.merchantSiteInternational','pi pi-globe',30),
 ('LAB_SIMULATORS','LAB_3DS_NETWORK','LAB_3DS_NETWORK','screens.threeDsNetwork','pi pi-shield',40),
 ('LAB_SIMULATORS','LAB_CARD_NETWORK','LAB_CARD_NETWORK','screens.cardNetwork','pi pi-sitemap',50)
)
INSERT INTO navigation_node(module_id,parent_id,node_type,code,label_key,icon,
                            screen_definition_id,display_order,active,context_json)
SELECT m.id,p.id,'SCREEN',c.node_code,c.label_key,c.icon,s.id,c.display_order,TRUE,
       jsonb_build_object('moduleCode',c.module_code)
  FROM catalog c
  JOIN app_module m ON m.code=c.module_code
  JOIN screen_definition s ON s.code=c.screen_code
  JOIN navigation_node p ON p.module_id=m.id
   AND p.code=CASE WHEN c.module_code='LAB_SIMULATORS'
                   THEN 'LAB_SIMULATORS_CATALOG' ELSE c.module_code||'_OPERATIONS' END
ON CONFLICT (module_id,code) DO UPDATE SET
 parent_id=EXCLUDED.parent_id,label_key=EXCLUDED.label_key,icon=EXCLUDED.icon,
 screen_definition_id=EXCLUDED.screen_definition_id,display_order=EXCLUDED.display_order,
 active=TRUE,context_json=EXCLUDED.context_json;

-- Le profil ADMIN reçoit le nouveau catalogue. Les profils métier seront
-- configurés explicitement depuis l'administration RBAC.
INSERT INTO profile_navigation_grant(role_id,navigation_node_id,allowed)
SELECT r.id,n.id,TRUE FROM roles r CROSS JOIN navigation_node n
JOIN app_module m ON m.id=n.module_id
WHERE r.code='ADMIN'
  AND m.code IN ('SERVER_POS','ACQUIRING','CARD_ISSUING','SWAM_MEMBER',
                 'DMAS_MEMBER','DMCS_MEMBER','LAB_SIMULATORS')
ON CONFLICT (role_id,navigation_node_id) DO UPDATE SET allowed=TRUE;

GRANT SELECT,INSERT,UPDATE,DELETE ON app_module,screen_definition,navigation_node,
 profile_navigation_grant TO scenario_user;
GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO scenario_user;
