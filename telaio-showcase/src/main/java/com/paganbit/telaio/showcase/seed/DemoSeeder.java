package com.paganbit.telaio.showcase.seed;

/**
 * Seeds the demo data of one aggregate at startup. Each DAL package contributes its own implementation
 * as a bean; {@code DataInitializer} runs them all in {@link org.springframework.core.annotation.Order}
 * order. Implementations must be idempotent — a restart against a persistent store must not duplicate
 * data (see {@link AbstractDemoSeeder} for the standard "skip when the store is not empty" guard).
 */
public interface DemoSeeder {

    /**
     * Order value for seeders that produce reference data other seeders look up (e.g. departments
     * before employees). Seeders without an explicit order run afterwards.
     */
    int REFERENCE_DATA = 0;

    /**
     * Seeds the aggregate's demo data if it is not present yet.
     */
    void seed();
}
