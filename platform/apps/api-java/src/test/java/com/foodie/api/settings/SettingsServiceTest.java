package com.foodie.api.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Test
    void adminViewMascaraChaveSecretaESomeComOSegredoDoRecaptcha() {
        when(repository.storedValues()).thenReturn(Map.of(SettingsCatalog.OPENAI_API_KEY, "sk-teste-1234567890abcd"));

        var entradas = service.adminView();
        var chaves = entradas.stream().map(entrada -> (String) entrada.get("key")).toList();
        var openai = entradas.stream()
            .filter(entrada -> SettingsCatalog.OPENAI_API_KEY.equals(entrada.get("key")))
            .findFirst()
            .orElseThrow();

        assertThat(chaves).doesNotContain("integrations.recaptcha_secret");
        assertThat(chaves).contains(SettingsCatalog.RECAPTCHA_SITE_KEY, SettingsCatalog.OPENAI_API_KEY);
        // Só os quatro últimos caracteres, com o resto mascarado: nunca a chave inteira.
        assertThat((String) openai.get("value")).isEqualTo(SettingsService.MASK + "abcd").doesNotContain("sk-teste");
        assertThat(openai.get("secret")).isEqualTo(true);
    }

    @Test
    void salvarOFormularioComOValorMascaradoNaoApagaAChave() {
        when(repository.storedValues()).thenReturn(Map.of());
        service.update(Map.of(SettingsCatalog.OPENAI_API_KEY, SettingsService.MASK + "abcd"));
        verify(repository, never()).upsert(anyString(), anyString());
    }
}
