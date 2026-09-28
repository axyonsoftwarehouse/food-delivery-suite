-- Existing stores had access to these tools before module entitlements were introduced.
-- Preserve explicit admin choices already recorded in restaurant_modules.
INSERT IGNORE INTO restaurant_modules (restaurant_id, module_key, enabled)
SELECT r.id, m.module_key, TRUE
FROM restaurants r
CROSS JOIN modules m;
