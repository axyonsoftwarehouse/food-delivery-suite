package com.foodie.api.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Corpos das rotas de suporte. O motivo é validado em {@link SupportActionService#normalizeReason}. */
public final class SupportRequests {
    private SupportRequests() {}

    /** Escrita com dados: o {@code data} reaproveita os records de validação das rotas da loja. */
    public record SupportRequest<T>(String reason, @Valid @NotNull T data) {}

    /** Escrita sem dados (exclusões, retomada de pausa). */
    public record ReasonRequest(String reason) {}

    public record PauseRequest(@Min(15) @Max(4320) int minutes, String reason) {}
}
