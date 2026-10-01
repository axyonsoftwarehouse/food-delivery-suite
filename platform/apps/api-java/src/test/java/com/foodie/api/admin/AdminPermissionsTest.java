package com.foodie.api.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminPermissionsTest {
    @Test
    void supportPermissionsAreKnownAndInTheCatalog() {
        assertThat(AdminPermissions.SUPPORT_VIEW).isEqualTo("support.view");
        assertThat(AdminPermissions.SUPPORT_ACT).isEqualTo("support.act");
        assertThat(AdminPermissions.isKnown("support.view")).isTrue();
        assertThat(AdminPermissions.isKnown("support.act")).isTrue();
        assertThat(AdminPermissions.CATALOG)
            .extracting(AdminPermissions.Descriptor::group)
            .contains("Suporte");
    }
}
