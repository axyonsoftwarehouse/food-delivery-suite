package com.foodie.api.auth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionCleanup {
    private final AuthRepository repository;

    public SessionCleanup(AuthRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void purgeExpiredSessions() {
        repository.deleteExpiredSessions();
    }
}
