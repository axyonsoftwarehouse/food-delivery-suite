package com.foodie.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AddonServiceTest {
    @Test
    void keySortsAndDeduplicates() {
        assertThat(AddonService.key(List.of(3L, 1L, 3L, 2L))).isEqualTo("1,2,3");
        assertThat(AddonService.key(List.of())).isEmpty();
        assertThat(AddonService.key(null)).isEmpty();
    }

    @Test
    void parseKeyRoundTrips() {
        assertThat(AddonService.parseKey("2,10,4")).containsExactly(2L, 10L, 4L);
        assertThat(AddonService.parseKey("")).isEmpty();
        assertThat(AddonService.parseKey(null)).isEmpty();
        assertThat(AddonService.parseKey("a,5")).containsExactly(5L);
    }
}
