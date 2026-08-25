package com.paganbit.telaio.showcase;

import com.paganbit.telaio.showcase.seed.DemoSeeder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs every {@link DemoSeeder} bean at startup. The seeders live next to the aggregate they populate
 * (one per {@code dal/*} package) and are executed in {@link org.springframework.core.annotation.Order}
 * order, so reference data such as departments is in place before the seeders that look it up. Each
 * seeder is idempotent, so restarts against the persistent docker-compose databases never duplicate
 * the demo data.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final List<DemoSeeder> seeders;

    public DataInitializer(List<DemoSeeder> seeders) {
        this.seeders = List.copyOf(seeders);
    }

    @Override
    public void run(String... args) {
        seeders.forEach(DemoSeeder::seed);
    }
}
