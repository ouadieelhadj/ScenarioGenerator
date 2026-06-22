-- ============================================================
-- RBAC dynamique : roles, permissions, matrice role->permission
-- Etape 1 : tables + seed. NE TOUCHE PAS a la table users.
-- ============================================================

CREATE TABLE IF NOT EXISTS roles (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL UNIQUE,
    label       VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS permissions (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    label       VARCHAR(100) NOT NULL,
    category    VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles(id)       ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

INSERT INTO roles (code, label, description) VALUES
  ('ADMIN',        'Administrateur', 'Acces complet a toutes les fonctions'),
  ('TESTEUR',      'Testeur',        'Cree campagnes et tests de charge, execute'),
  ('OBSERVATEUR',  'Observateur',    'Lecture seule : consultation et export'),
  ('EXPLOITATION', 'Exploitation',   'Equivalent testeur')
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code, label, category) VALUES
  ('USER_MANAGE',       'Gerer les utilisateurs',         'GESTION'),
  ('ROLE_MANAGE',       'Gerer roles et permissions',     'GESTION'),
  ('CATALOG_MANAGE',    'Gerer les catalogues',           'GESTION'),
  ('CAMPAIGN_VIEW',     'Consulter les campagnes',        'CAMPAGNE'),
  ('CAMPAIGN_CREATE',   'Creer/editer une campagne',      'CAMPAGNE'),
  ('CAMPAIGN_GENERATE', 'Generer les transactions',       'CAMPAGNE'),
  ('CAMPAIGN_EXPORT',   'Exporter (JSON/CSV)',            'CAMPAGNE'),
  ('CARD_PROVISION',    'Provisionner les cartes',        'ORCHESTRATION'),
  ('CAMPAIGN_REPLAY',   'Rejouer une campagne',           'ORCHESTRATION'),
  ('TPS_CREATE',        'Creer/editer un test de charge', 'CHARGE'),
  ('TPS_RUN',           'Lancer/arreter une execution',   'CHARGE'),
  ('EXECUTION_VIEW',    'Consulter executions/rapports',  'CHARGE')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
  'CAMPAIGN_VIEW','CAMPAIGN_CREATE','CAMPAIGN_GENERATE','CAMPAIGN_EXPORT',
  'CAMPAIGN_REPLAY','TPS_CREATE','TPS_RUN','EXECUTION_VIEW'
)
WHERE r.code = 'TESTEUR'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
  'CAMPAIGN_VIEW','CAMPAIGN_CREATE','CAMPAIGN_GENERATE','CAMPAIGN_EXPORT',
  'CAMPAIGN_REPLAY','TPS_CREATE','TPS_RUN','EXECUTION_VIEW'
)
WHERE r.code = 'EXPLOITATION'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
  'CAMPAIGN_VIEW','CAMPAIGN_EXPORT','EXECUTION_VIEW'
)
WHERE r.code = 'OBSERVATEUR'
ON CONFLICT DO NOTHING;

ALTER TABLE roles            OWNER TO scenario_user;
ALTER TABLE permissions      OWNER TO scenario_user;
ALTER TABLE role_permissions OWNER TO scenario_user;
GRANT ALL ON SEQUENCE roles_id_seq       TO scenario_user;
GRANT ALL ON SEQUENCE permissions_id_seq TO scenario_user;
