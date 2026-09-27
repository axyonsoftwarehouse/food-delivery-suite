package com.foodie.api.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsServiceTest {
    private final SettingsRepository repository = mock(SettingsRepository.class);
    private final SettingsService service = new SettingsService(repository);

    @Test
    void usesDefaultsWhenNothingStored() {
        when(repository.storedValues()).thenReturn(Map.of());
        assertThat(service.text(SettingsCatalog.BUSINESS_NAME)).isEqualTo("Foodie");
        assertThat(service.bool(SettingsCatalog.ORDER_DELIVERY)).isTrue();
        assertThat(service.bool(SettingsCatalog.ORDER_GUEST_CHECKOUT)).isFalse();
    }

    @Test
    void storedValueOverridesDefault() {
        when(repository.storedValues()).thenReturn(Map.of(SettingsCatalog.BUSINESS_NAME, "Minha Loja"));
        assertThat(service.text(SettingsCatalog.BUSINESS_NAME)).isEqualTo("Minha Loja");
    }

    @Test
    void maintenanceActiveWhenEnabled() {
        when(repository.storedValues()).thenReturn(Map.of(SettingsCatalog.MAINTENANCE_ENABLED, "true"));
        assertThat(service.maintenanceActive()).isTrue();
        assertThat(service.publicConfig()).containsKey("maintenance");
    }

    @Test
    void maintenanceActiveDuringWindow() {
        when(repository.storedValues()).thenReturn(Map.of());
        when(repository.hasActiveWindow()).thenReturn(true);
        assertThat(service.maintenanceActive()).isTrue();
    }

    @Test
    void updateNormalizesBooleanAndRejectsUnknownKey() {
        when(repository.storedValues()).thenReturn(Map.of());
        service.update(Map.of(SettingsCatalog.ORDER_DELIVERY, "on"));
        verify(repository).upsert(SettingsCatalog.ORDER_DELIVERY, "true");

        assertThatThrownBy(() -> service.update(Map.of("nope.key", "x")))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(400));
    }

    @Test
    void updateRejectsInvalidBoolean() {
        when(repository.storedValues()).thenReturn(Map.of());
        assertThatThrownBy(() -> service.update(Map.of(SettingsCatalog.ORDER_DELIVERY, "talvez")))
            .isInstanceOf(ApiException.class);
    }
}
