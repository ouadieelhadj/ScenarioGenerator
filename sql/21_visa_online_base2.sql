-- Catalogue frontend Visa Online / Base II.
-- Migration additive et rejouable, a appliquer apres 19_frontend_global_catalog.sql.

INSERT INTO app_module(code,label_key,icon,display_order,active) VALUES
 ('VISA_ONLINE_MEMBER','modules.visaOnline','pi pi-bolt',75,TRUE),
 ('VISA_BASE2_MEMBER','modules.visaBase2','pi pi-file-export',76,TRUE)
ON CONFLICT (code) DO UPDATE SET label_key=EXCLUDED.label_key,icon=EXCLUDED.icon,
 display_order=EXCLUDED.display_order,active=TRUE;

WITH screens(code,route_template) AS (VALUES
 ('VISA_ONLINE_OVERVIEW','/modules/:moduleCode/overview'),
 ('VISA_ONLINE_TRANSACTIONS','/modules/:moduleCode/transactions'),
 ('VISA_ONLINE_ROUTING','/modules/:moduleCode/routing'),
 ('VISA_ONLINE_NETWORK','/modules/:moduleCode/network'),
 ('VISA_BASE2_OVERVIEW','/modules/:moduleCode/overview'),
 ('VISA_BASE2_PRESENTMENTS','/modules/:moduleCode/presentments'),
 ('VISA_BASE2_FILES','/modules/:moduleCode/files'),
 ('VISA_BASE2_RECONCILIATION','/modules/:moduleCode/reconciliation'),
 ('LAB_VISANET_NETWORK','/lab/:moduleCode/visanet-network'),
 ('LAB_VISA_BASE2_NETWORK','/lab/:moduleCode/visa-base2-network')
)
INSERT INTO screen_definition(code,route_template,component_key,shared_screen,active)
SELECT code,route_template,'VISA_WORKSPACE',FALSE,TRUE FROM screens
ON CONFLICT (code) DO UPDATE SET route_template=EXCLUDED.route_template,
 component_key=EXCLUDED.component_key,active=TRUE;

INSERT INTO navigation_node(module_id,node_type,code,label_key,icon,display_order,active)
SELECT m.id,'MENU',m.code||'_OPERATIONS','moduleMenus.operations','pi pi-folder',10,TRUE
FROM app_module m WHERE m.code IN ('VISA_ONLINE_MEMBER','VISA_BASE2_MEMBER')
ON CONFLICT (module_id,code) DO UPDATE SET active=TRUE;

WITH catalog(module_code,screen_code,node_code,label_key,icon,display_order) AS (VALUES
 ('VISA_ONLINE_MEMBER','VISA_ONLINE_OVERVIEW','VISA_ONLINE_OVERVIEW','screens.overview','pi pi-home',10),
 ('VISA_ONLINE_MEMBER','VISA_ONLINE_TRANSACTIONS','VISA_ONLINE_TRANSACTIONS','screens.transactions','pi pi-list',20),
 ('VISA_ONLINE_MEMBER','VISA_ONLINE_ROUTING','VISA_ONLINE_ROUTING','screens.routing','pi pi-sitemap',30),
 ('VISA_ONLINE_MEMBER','VISA_ONLINE_NETWORK','VISA_ONLINE_NETWORK','screens.network','pi pi-wifi',40),
 ('VISA_BASE2_MEMBER','VISA_BASE2_OVERVIEW','VISA_BASE2_OVERVIEW','screens.overview','pi pi-home',10),
 ('VISA_BASE2_MEMBER','VISA_BASE2_PRESENTMENTS','VISA_BASE2_PRESENTMENTS','screens.presentments','pi pi-send',20),
 ('VISA_BASE2_MEMBER','VISA_BASE2_FILES','VISA_BASE2_FILES','screens.base2Files','pi pi-file-export',30),
 ('VISA_BASE2_MEMBER','VISA_BASE2_RECONCILIATION','VISA_BASE2_RECONCILIATION','screens.reconciliation','pi pi-sync',40)
)
INSERT INTO navigation_node(module_id,parent_id,node_type,code,label_key,icon,
 screen_definition_id,display_order,active,context_json)
SELECT m.id,p.id,'SCREEN',c.node_code,c.label_key,c.icon,s.id,c.display_order,TRUE,
 jsonb_build_object('moduleCode',c.module_code)
FROM catalog c JOIN app_module m ON m.code=c.module_code
JOIN screen_definition s ON s.code=c.screen_code
JOIN navigation_node p ON p.module_id=m.id AND p.code=c.module_code||'_OPERATIONS'
ON CONFLICT (module_id,code) DO UPDATE SET parent_id=EXCLUDED.parent_id,
 label_key=EXCLUDED.label_key,icon=EXCLUDED.icon,screen_definition_id=EXCLUDED.screen_definition_id,
 display_order=EXCLUDED.display_order,active=TRUE,context_json=EXCLUDED.context_json;

WITH catalog(screen_code,node_code,label_key,icon,display_order) AS (VALUES
 ('LAB_VISANET_NETWORK','LAB_VISANET_NETWORK','screens.visaNetSimulator','pi pi-bolt',60),
 ('LAB_VISA_BASE2_NETWORK','LAB_VISA_BASE2_NETWORK','screens.base2Simulator','pi pi-file-export',70)
)
INSERT INTO navigation_node(module_id,parent_id,node_type,code,label_key,icon,
 screen_definition_id,display_order,active,context_json)
SELECT m.id,p.id,'SCREEN',c.node_code,c.label_key,c.icon,s.id,c.display_order,TRUE,
 jsonb_build_object('moduleCode','LAB_SIMULATORS')
FROM catalog c JOIN app_module m ON m.code='LAB_SIMULATORS'
JOIN screen_definition s ON s.code=c.screen_code
JOIN navigation_node p ON p.module_id=m.id AND p.code='LAB_SIMULATORS_CATALOG'
ON CONFLICT (module_id,code) DO UPDATE SET parent_id=EXCLUDED.parent_id,
 label_key=EXCLUDED.label_key,icon=EXCLUDED.icon,screen_definition_id=EXCLUDED.screen_definition_id,
 display_order=EXCLUDED.display_order,active=TRUE,context_json=EXCLUDED.context_json;

INSERT INTO profile_navigation_grant(role_id,navigation_node_id,allowed)
SELECT r.id,n.id,TRUE FROM roles r CROSS JOIN navigation_node n
JOIN app_module m ON m.id=n.module_id
WHERE r.code='ADMIN' AND m.code IN ('VISA_ONLINE_MEMBER','VISA_BASE2_MEMBER','LAB_SIMULATORS')
ON CONFLICT (role_id,navigation_node_id) DO UPDATE SET allowed=TRUE;

GRANT SELECT,INSERT,UPDATE,DELETE ON app_module,screen_definition,navigation_node,
 profile_navigation_grant TO scenario_user;
GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO scenario_user;
