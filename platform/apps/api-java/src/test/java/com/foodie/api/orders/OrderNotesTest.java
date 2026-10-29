package com.foodie.api.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OrderNotesTest {
    @Test
    void itemNoteHasASingleFormSoTheCartLineMatches() {
        assertThat(OrderNotes.item(null)).isEmpty();
        assertThat(OrderNotes.item("   ")).isEmpty();
        assertThat(OrderNotes.item("  sem   cebola \n")).isEqualTo("sem cebola");
        assertThat(OrderNotes.item("x".repeat(200))).hasSize(OrderNotes.ITEM_MAX);
    }

    @Test
    void emptyOrderNoteIsNotStored() {
        assertThat(OrderNotes.order(null)).isNull();
        assertThat(OrderNotes.order(" ")).isNull();
        assertThat(OrderNotes.order(" tocar  o interfone ")).isEqualTo("tocar o interfone");
    }

    @Test
    void sameDishWithAnotherNoteIsAnotherLine() {
        var plain = new OrderController.Item(4L, null, 1, List.of(2L, 1L));
        var noted = new OrderController.Item(4L, null, 1, List.of(1L, 2L), " sem cebola ");
        assertThat(plain.lineKey()).isNotEqualTo(noted.lineKey());
        assertThat(noted.lineKey()).isEqualTo(new OrderController.Item(4L, null, 1, List.of(1L, 2L), "sem  cebola").lineKey());
    }
}
