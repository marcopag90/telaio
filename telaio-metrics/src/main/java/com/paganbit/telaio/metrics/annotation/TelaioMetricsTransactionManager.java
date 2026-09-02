package com.paganbit.telaio.metrics.annotation;

import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.annotation.*;

/**
 * Qualifier marking the {@link org.springframework.transaction.PlatformTransactionManager} the JDBC
 * metrics store uses for its (rare, background) multi-statement writes.
 *
 * <p>Optional. By default, the store builds its own JDBC transaction manager on the DataSource it
 * writes to — the application's transaction managers are never looked up, so mixed-backend contexts
 * (several DataSources, JPA next to MongoDB) stay unambiguous. Mark a bean with this annotation only
 * when a different manager must drive those writes, e.g. a JTA manager:</p>
 * <pre>{@code
 * @Bean(defaultCandidate = false)
 * @TelaioMetricsTransactionManager
 * PlatformTransactionManager metricsTransactionManager(...) { ... }
 * }</pre>
 * <p>The marked manager must manage the metrics {@link javax.sql.DataSource}
 * (see {@link TelaioMetricsDataSource}); otherwise the store's statements do not take part in its
 * transactions.</p>
 *
 * @author Marco Pagan
 * @see TelaioMetricsDataSource
 * @since 2.0.0
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Qualifier
public @interface TelaioMetricsTransactionManager {
}
