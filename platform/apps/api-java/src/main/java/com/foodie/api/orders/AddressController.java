package com.foodie.api.orders;

import com.foodie.api.auth.AuthService;
import com.foodie.api.catalog.PostalCoverageService;
import com.foodie.api.routing.GeocodingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AddressController {
    private final AuthService auth;
    private final JdbcTemplate jdbc;
    private final PostalCoverageService postalCoverage;
    private final GeocodingService geocoding;

    public AddressController(AuthService auth, JdbcTemplate jdbc, PostalCoverageService postalCoverage, GeocodingService geocoding) {
        this.auth = auth;
        this.jdbc = jdbc;
        this.postalCoverage = postalCoverage;
        this.geocoding = geocoding;
    }

    @GetMapping("/addresses")
    public List<Map<String, Object>> addresses(@CookieValue(value = "foodie_session", required = false) String token) {
        long userId = auth.requireUser(token, "customer").id();
        return jdbc.queryForList("SELECT a.id, a.zone_id, a.postal_code, a.label, a.street, a.number, a.neighborhood, a.complement, a.latitude, a.longitude, z.name AS zone_name, z.city, z.state FROM addresses a JOIN zones z ON z.id = a.zone_id WHERE a.user_id = ? ORDER BY a.id DESC", userId);
    }

    @PostMapping("/addresses")
    public ResponseEntity<Map<String, Object>> create(@CookieValue(value = "foodie_session", required = false) String token,
                                                       @Valid @RequestBody AddressRequest request) {
        long userId = auth.requireUser(token, "customer").id();
        String postalCode = postalCoverage.normalize(request.postalCode());
        Map<String, Object> zone = postalCoverage.resolve(postalCode);
        long zoneId = ((Number) zone.get("id")).longValue();
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        String complement = request.complement() == null ? "" : request.complement().trim();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO addresses (user_id, zone_id, postal_code, label, street, number, neighborhood, complement) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, userId);
            statement.setLong(2, zoneId);
            statement.setString(3, postalCode);
            statement.setString(4, request.label().trim());
            statement.setString(5, request.street().trim());
            statement.setString(6, request.number().trim());
            statement.setString(7, request.neighborhood().trim());
            statement.setString(8, complement);
            return statement;
        }, key);
        long addressId = key.getKey().longValue();
        String query = request.street().trim() + ", " + request.number().trim() + ", " + request.neighborhood().trim()
            + ", " + zone.get("city") + " - " + zone.get("state") + ", " + postalCode + ", Brasil";
        var coordinate = geocoding.geocode(query);
        coordinate.ifPresent(value -> jdbc.update("UPDATE addresses SET latitude = ?, longitude = ? WHERE id = ?",
            value.latitude(), value.longitude(), addressId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", addressId);
        result.put("zoneId", zoneId);
        result.put("postalCode", postalCode);
        result.put("label", request.label().trim());
        result.put("street", request.street().trim());
        result.put("number", request.number().trim());
        result.put("neighborhood", request.neighborhood().trim());
        result.put("complement", complement);
        result.put("latitude", coordinate.map(GeocodingService.Coordinate::latitude).orElse(null));
        result.put("longitude", coordinate.map(GeocodingService.Coordinate::longitude).orElse(null));
        return ResponseEntity.status(201).body(result);
    }

    public record AddressRequest(@NotBlank String postalCode,
                                 @NotBlank @Size(min = 2, max = 60) String label,
                                 @NotBlank @Size(min = 3, max = 180) String street,
                                 @NotBlank @Size(min = 1, max = 30) String number,
                                 @NotBlank @Size(min = 2, max = 120) String neighborhood,
                                 @Size(max = 120) String complement) {}
}
