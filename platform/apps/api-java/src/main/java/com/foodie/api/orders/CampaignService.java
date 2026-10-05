package com.foodie.api.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CampaignService {
    private final JdbcTemplate jdbc;

    public CampaignService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Applied best(long restaurantId, List<Line> lines) {
        long subtotal = lines.stream().mapToLong(Line::totalCents).sum();
        if (subtotal <= 0) return null;
        List<Map<String, Object>> campaigns = jdbc.queryForList(
            "SELECT id, name, type, percent, product_id FROM campaigns WHERE active = TRUE "
                + "AND restaurant_id = ? "
                + "AND (starts_at IS NULL OR starts_at <= CURRENT_DATE()) "
                + "AND (ends_at IS NULL OR ends_at >= CURRENT_DATE()) ORDER BY id",
            restaurantId);
        Applied best = null;
        for (Map<String, Object> campaign : campaigns) {
            long basis = "basic".equals(campaign.get("type")) ? subtotal : lines.stream()
                .filter(line -> campaign.get("product_id") != null
                    && line.productId() == ((Number) campaign.get("product_id")).longValue())
                .mapToLong(Line::totalCents).sum();
            if (basis <= 0) continue;
            BigDecimal percent = (BigDecimal) campaign.get("percent");
            long discount = BigDecimal.valueOf(basis).multiply(percent)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValue();
            discount = Math.min(subtotal, Math.max(0, discount));
            if (discount > 0 && (best == null || discount > best.discountCents())) {
                best = new Applied(((Number) campaign.get("id")).longValue(),
                    (String) campaign.get("name"), discount);
            }
        }
        return best;
    }

    public record Line(long productId, long totalCents) {}
    public record Applied(long campaignId, String name, long discountCents) {}
}
