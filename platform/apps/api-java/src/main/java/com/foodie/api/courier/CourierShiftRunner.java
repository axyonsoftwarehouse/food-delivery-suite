package com.foodie.api.courier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Fecha turnos esquecidos (abertos há mais de 12 h) e apaga posições sem turno nem entrega, a cada 5 minutos. */
@Service
public class CourierShiftRunner {
    private static final Logger log = LoggerFactory.getLogger(CourierShiftRunner.class);
    private final CourierShiftService shifts;

    public CourierShiftRunner(CourierShiftService shifts) {
        this.shifts = shifts;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void run() {
        try {
            int closed = shifts.closeStale();
            if (closed > 0) log.info("Turnos esquecidos fechados: {}", closed);
        } catch (RuntimeException error) {
            // Falha da rodada (banco fora, por exemplo): a próxima tenta de novo.
            log.warn("Fechamento de turnos esquecidos falhou nesta rodada: {}", error.getMessage());
        }
    }
}
