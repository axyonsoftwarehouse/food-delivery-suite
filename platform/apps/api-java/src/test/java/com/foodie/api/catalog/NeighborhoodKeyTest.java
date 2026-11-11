package com.foodie.api.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A chave do bairro precisa sair igual à da migration V080 (minúscula, sem acento, espaços únicos). */
class NeighborhoodKeyTest {
    @Test
    void removesAccentsCaseAndExtraSpaces() {
        assertEquals("edson queiroz", NeighborhoodKey.of("  Edson   Queiróz "));
        assertEquals("sao joao do tauape", NeighborhoodKey.of("São João do Tauape"));
        assertEquals("aldeota", NeighborhoodKey.of("ALDEÔTA"));
        assertEquals("cambeba", NeighborhoodKey.of("Cambeba\t"));
    }

    @Test
    void cedillaAndTildeBecomePlainLetters() {
        assertEquals("conceicao", NeighborhoodKey.of("Conceição"));
        assertEquals("joao xxiii", NeighborhoodKey.of("João XXIII"));
    }

    @Test
    void emptyOrNullIsEmpty() {
        assertEquals("", NeighborhoodKey.of(null));
        assertEquals("", NeighborhoodKey.of("   "));
    }

    @Test
    void slugJoinsStateCityAndNeighborhood() {
        assertEquals("ce-fortaleza-edson-queiroz", NeighborhoodKey.slug("CE", "Fortaleza", "Edson Queiróz"));
        assertEquals("sp-sao-paulo-se", NeighborhoodKey.slug("SP", "São Paulo", "Sé"));
    }
}
