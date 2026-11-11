package com.foodie.api.catalog;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Chave de comparação de bairro e cidade: minúscula, sem acento e com espaços únicos. É a mesma regra
 * da migration V080, para "Aldeota" e "Aldeôta" serem o mesmo bairro.
 */
public final class NeighborhoodKey {
    private NeighborhoodKey() {}

    public static String of(String value) {
        if (value == null) return "";
        String plain = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /** Identificador legível e único do bairro: "ce-fortaleza-aldeota". */
    public static String slug(String state, String city, String neighborhood) {
        String joined = (of(state) + " " + of(city) + " " + of(neighborhood)).replaceAll("[^a-z0-9]+", "-");
        String slug = joined.replaceAll("^-+|-+$", "");
        return slug.length() > 140 ? slug.substring(0, 140).replaceAll("-+$", "") : slug;
    }
}
