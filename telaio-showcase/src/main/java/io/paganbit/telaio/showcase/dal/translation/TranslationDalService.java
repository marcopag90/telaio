package io.paganbit.telaio.showcase.dal.translation;

import io.paganbit.telaio.core.annotation.DalService;
import io.paganbit.telaio.jpa.JpaDal;
import io.paganbit.telaio.metrics.annotation.DalMetrics;

/**
 * Use case — a DAL with a <em>composite</em> id. Exposes {@link Translation} (keyed by
 * {@link TranslationId}) as a full CRUD REST resource, demonstrating that {@code @DalService} works
 * unchanged when the id type is a complex object: the framework resolves the {@code {id}} path segment as a
 * Base64 URL-safe encoded JSON of {@link TranslationId} and the JPA layer builds a multi-column WHERE clause.
 */
@DalService(name = "translations")
@DalMetrics(enabled = false)
public class TranslationDalService extends JpaDal<Translation, TranslationId> {
}
