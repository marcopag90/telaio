package com.paganbit.telaio.showcase.config;

import com.paganbit.telaio.showcase.dal.DalPackage;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA setup for the showcase: auditing plus an explicitly scoped repository scan.
 *
 * <p>This application runs two Spring Data stores (JPA and MongoDB). Spring Boot's autoconfigured scans
 * would both cover the whole application package in strict multi-store mode, and each store then
 * logs an INFO line for every repository candidate that belongs to the other store. The explicit
 * {@code includeFilters} below bypasses strict matching: this scan only ever sees
 * {@link JpaRepository} subtypes, so no cross-store candidates are reported. Boot's
 * {@code DataJpaRepositoriesAutoConfiguration} backs off in favor of this declaration.</p>
 *
 * <p>The filter targets {@link JpaRepository} rather than Telaio's {@code JpaDalRepository} on
 * purpose: the showcase also declares plain Spring Data repositories (e.g.
 * {@code ProductPriceHistoryRepository}).</p>
 */
@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(
    basePackageClasses = DalPackage.class,
    includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JpaRepository.class))
public class JpaConfiguration {
}
