package com.foodie.api.catalog;

import com.foodie.api.ApiException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Valida e precifica adicionais de um prato conforme os grupos vinculados (mín/máx/obrigatório). */
@Service
public class AddonService {
    private final JdbcTemplate jdbc;

    public AddonService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> validate(long productId, long variationId, List<Long> requested) {
        List<Long> addonIds = normalize(requested);
        List<Map<String, Object>> groupRows = jdbc.queryForList(
            "SELECT g.id, g.name, g.min_select, g.max_select, g.required FROM addon_groups g "
                + "JOIN product_addon_groups pag ON pag.addon_group_id = g.id AND pag.product_id = ? AND (pag.variation_id = 0 OR pag.variation_id = ?) "
                + "ORDER BY pag.variation_id DESC, pag.sort, g.sort",
            productId, variationId);
        Map<Long, Map<String, Object>> unique = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : groupRows) unique.putIfAbsent(number(row, "id"), row);
        List<Map<String, Object>> groups = new ArrayList<>(unique.values());
        if (groups.isEmpty()) {
            if (!addonIds.isEmpty()) throw new ApiException(400, "Este prato não aceita adicionais");
            return List.of();
        }
        Set<Long> validGroups = new HashSet<>();
        for (Map<String, Object> group : groups) validGroups.add(number(group, "id"));
        Map<Long, Long> groupByAddon = new HashMap<>();
        for (Long addonId : addonIds) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT addon_group_id FROM addons WHERE id = ? AND available = TRUE", addonId);
            if (rows.isEmpty()) throw new ApiException(400, "Adicional indisponível");
            long groupId = number(rows.getFirst(), "addon_group_id");
            if (!validGroups.contains(groupId)) throw new ApiException(400, "Adicional não pertence a este prato");
            groupByAddon.put(addonId, groupId);
        }
        for (Map<String, Object> group : groups) {
            long groupId = number(group, "id");
            int count = 0;
            for (Long addonId : addonIds) if (groupByAddon.get(addonId) == groupId) count++;
            int min = ((Number) group.get("min_select")).intValue();
            int max = ((Number) group.get("max_select")).intValue();
            Object requiredValue = group.get("required");
            boolean required = requiredValue instanceof Boolean value ? value : ((Number) requiredValue).intValue() != 0;
            int effectiveMin = required ? Math.max(1, min) : min;
            if (count < effectiveMin) throw new ApiException(400, "Escolha ao menos " + effectiveMin + " opção(ões) em \"" + group.get("name") + "\"");
            if (count > max) throw new ApiException(400, "Escolha no máximo " + max + " opção(ões) em \"" + group.get("name") + "\"");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long addonId : addonIds) result.add(jdbc.queryForMap("SELECT id, name, price_cents FROM addons WHERE id = ?", addonId));
        return result;
    }

    public long priceCents(List<Map<String, Object>> addons) {
        long total = 0;
        for (Map<String, Object> addon : addons) total += number(addon, "price_cents");
        return total;
    }

    public static String key(List<Long> addonIds) {
        return normalize(addonIds).stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
    }

    public static List<Long> parseKey(String key) {
        if (key == null || key.isBlank()) return List.of();
        List<Long> ids = new ArrayList<>();
        for (String part : key.split(",")) {
            try { ids.add(Long.parseLong(part.trim())); } catch (NumberFormatException ignored) { }
        }
        return ids;
    }

    private static List<Long> normalize(List<Long> requested) {
        if (requested == null) return List.of();
        return requested.stream().filter(id -> id != null && id > 0).distinct().sorted().toList();
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
