package com.foodie.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SupportQueryServiceTest {
    @Test
    void likePatternWrapsPlainTerm() {
        assertThat(SupportQueryService.likePattern("pizza")).isEqualTo("%pizza%");
    }

    @Test
    void likePatternEscapesWildcardsAndEscapeChar() {
        assertThat(SupportQueryService.likePattern("50%_off!")).isEqualTo("%50!%!_off!!%");
    }
}
