package com.paganbit.telaio.showcase.seed;

import org.springframework.data.repository.CrudRepository;

import java.util.Objects;

/**
 * {@link DemoSeeder} base guarding the population step with an "is the store already seeded?" check:
 * {@link #populate()} runs only when the aggregate's repository is empty, which keeps restarts against
 * the persistent docker-compose databases from duplicating rows or documents. Works for any Spring
 * Data store — the guard only needs {@link CrudRepository#count()}.
 */
public abstract class AbstractDemoSeeder implements DemoSeeder {

    private final CrudRepository<?, ?> repository;

    protected AbstractDemoSeeder(CrudRepository<?, ?> repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public final void seed() {
        if (repository.count() > 0) {
            return;
        }
        populate();
    }

    /**
     * Writes the aggregate's demo data. Invoked at most once per empty store.
     */
    protected abstract void populate();
}
