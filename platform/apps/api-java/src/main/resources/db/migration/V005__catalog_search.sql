CREATE INDEX ix_restaurant_zones_zone_restaurant ON restaurant_zones (zone_id, restaurant_id);
CREATE INDEX ix_products_restaurant_id ON products (restaurant_id, id);
