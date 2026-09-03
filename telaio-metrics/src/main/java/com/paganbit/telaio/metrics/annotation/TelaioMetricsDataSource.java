package com.paganbit.telaio.metrics.annotation;

import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.annotation.*;

/**
 * Qualifier marking the {@link javax.sql.DataSource} the JDBC metrics store and its schema
 * initializer use.
 *
 * <p>Without it the store uses the application's single (or {@code @Primary}) DataSource. Mark a bean
 * with this annotation to persist the metrics elsewhere — typically a DataSource whose default schema
 * is dedicated to metrics, since the table name is always unqualified. Declaring it as a non-default
 * candidate keeps it invisible to the rest of the application (by-type injection, Spring Boot's
 * DataSource and JPA autoconfiguration), while this qualifier still resolves it:</p>
 * <pre>{@code
 * @Bean(defaultCandidate = false)
 * @TelaioMetricsDataSource
 * DataSource metricsDataSource() { ... }
 * }</pre>
 * <p>When several DataSources exist and none is marked or {@code @Primary}, the autoconfiguration fails
 * fast with a message pointing here rather than picking one silently.</p>
 *
 * @author Marco Pagan
 * @see TelaioMetricsTransactionManager
 * @since 2.0.0
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Qualifier
public @interface TelaioMetricsDataSource {
}
