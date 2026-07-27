package com.staging.sg.acquirer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserNavigationService {

    private static final String SQL = """
        WITH RECURSIVE effective_profiles AS (
            SELECT up.role_id
              FROM users u JOIN user_profiles up ON up.user_id=u.id
             WHERE u.login=?
            UNION
            SELECT r.id
              FROM users u JOIN roles r ON r.code=u.role
             WHERE u.login=?
               AND NOT EXISTS (SELECT 1 FROM user_profiles up WHERE up.user_id=u.id)
        ), granted AS (
            SELECT png.navigation_node_id
              FROM profile_navigation_grant png
             WHERE png.role_id IN (SELECT role_id FROM effective_profiles)
               AND png.allowed
            UNION
            SELECT uno.navigation_node_id
              FROM user_navigation_override uno JOIN users u ON u.id=uno.user_id
             WHERE u.login=? AND uno.allowed
               AND (uno.valid_from IS NULL OR uno.valid_from<=now())
               AND (uno.valid_until IS NULL OR uno.valid_until>now())
        ), denied AS (
            SELECT uno.navigation_node_id
              FROM user_navigation_override uno JOIN users u ON u.id=uno.user_id
             WHERE u.login=? AND NOT uno.allowed
               AND (uno.valid_from IS NULL OR uno.valid_from<=now())
               AND (uno.valid_until IS NULL OR uno.valid_until>now())
        ), visible(id) AS (
            SELECT navigation_node_id FROM granted
             WHERE navigation_node_id NOT IN (SELECT navigation_node_id FROM denied)
            UNION
            SELECT n.parent_id FROM navigation_node n JOIN visible v ON n.id=v.id
             WHERE n.parent_id IS NOT NULL
        )
        SELECT m.code module_code,m.label_key module_label,m.icon module_icon,
               m.display_order module_order,n.id,n.parent_id,n.node_type,n.code,
               n.label_key,n.icon,n.display_order,s.route_template,s.component_key,
               n.context_json::text
          FROM visible v
          JOIN navigation_node n ON n.id=v.id AND n.active
          JOIN app_module m ON m.id=n.module_id AND m.active
          LEFT JOIN screen_definition s ON s.id=n.screen_definition_id AND s.active
         ORDER BY m.display_order,n.display_order,n.id
        """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public UserNavigationService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public NavigationResponse forLogin(String login) {
        try {
            List<Row> rows = jdbc.query(SQL, (rs, rowNum) -> new Row(
                    rs.getString("module_code"), rs.getString("module_label"),
                    rs.getString("module_icon"), rs.getInt("module_order"),
                    rs.getLong("id"), (Long) rs.getObject("parent_id"),
                    rs.getString("node_type"), rs.getString("code"),
                    rs.getString("label_key"), rs.getString("icon"),
                    rs.getInt("display_order"), rs.getString("route_template"),
                    rs.getString("component_key"), parseContext(rs.getString("context_json"))
            ), login, login, login, login);
            return buildTree(rows);
        } catch (DataAccessException ex) {
            // Compatibilité pendant le déploiement : le frontend utilisera son menu
            // historique tant que la migration 18 n'est pas appliquée.
            return new NavigationResponse(List.of(), true);
        }
    }

    private Map<String, Object> parseContext(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private NavigationResponse buildTree(List<Row> rows) {
        Map<Long, MutableNode> nodes = new LinkedHashMap<>();
        for (Row row : rows) nodes.put(row.id(), new MutableNode(row));
        Map<String, MutableModule> modules = new LinkedHashMap<>();
        for (Row row : rows) {
            MutableNode node = nodes.get(row.id());
            if (row.parentId() != null && nodes.containsKey(row.parentId())) {
                nodes.get(row.parentId()).children.add(node);
            } else {
                modules.computeIfAbsent(row.moduleCode(), ignored ->
                        new MutableModule(row.moduleCode(), row.moduleLabel(), row.moduleIcon(),
                                row.moduleOrder())).children.add(node);
            }
        }
        List<ModuleNavigation> result = modules.values().stream()
                .sorted(Comparator.comparingInt(m -> m.order))
                .map(MutableModule::freeze).toList();
        return new NavigationResponse(result, false);
    }

    public record NavigationResponse(List<ModuleNavigation> modules, boolean legacyFallback) {}
    public record ModuleNavigation(String code, String labelKey, String icon,
                                   List<NavigationItem> children) {}
    public record NavigationItem(long id, String type, String code, String labelKey,
                                 String icon, String route, String componentKey,
                                 Map<String, Object> context, List<NavigationItem> children) {}

    private record Row(String moduleCode, String moduleLabel, String moduleIcon, int moduleOrder,
                       long id, Long parentId, String type, String code, String labelKey,
                       String icon, int order, String route, String componentKey,
                       Map<String, Object> context) {}

    private static final class MutableNode {
        private final Row row;
        private final List<MutableNode> children = new ArrayList<>();
        private MutableNode(Row row) { this.row = row; }
        private NavigationItem freeze() {
            return new NavigationItem(row.id(), row.type(), row.code(), row.labelKey(),
                    row.icon(), resolveRoute(row.route(), row.context()), row.componentKey(),
                    row.context(), children.stream()
                    .sorted(Comparator.comparingInt(n -> n.row.order())).map(MutableNode::freeze).toList());
        }
        private static String resolveRoute(String template, Map<String, Object> context) {
            if (template == null) return null;
            Object moduleCode = context.get("moduleCode");
            return moduleCode == null ? template
                    : template.replace(":moduleCode", moduleCode.toString());
        }
    }

    private static final class MutableModule {
        private final String code;
        private final String label;
        private final String icon;
        private final int order;
        private final List<MutableNode> children = new ArrayList<>();
        private MutableModule(String code, String label, String icon, int order) {
            this.code=code; this.label=label; this.icon=icon; this.order=order;
        }
        private ModuleNavigation freeze() {
            return new ModuleNavigation(code,label,icon,children.stream()
                    .sorted(Comparator.comparingInt(n -> n.row.order()))
                    .map(MutableNode::freeze).toList());
        }
    }
}
