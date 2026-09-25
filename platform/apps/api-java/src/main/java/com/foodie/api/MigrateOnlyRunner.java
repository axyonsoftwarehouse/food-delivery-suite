package com.foodie.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class MigrateOnlyRunner implements ApplicationRunner {
    private final ApplicationContext context;
    private final boolean migrateOnly;

    public MigrateOnlyRunner(ApplicationContext context, @Value("${app.migrate-only:false}") boolean migrateOnly) {
        this.context = context;
        this.migrateOnly = migrateOnly;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (migrateOnly) System.exit(SpringApplication.exit(context, () -> 0));
    }
}
