package com.paganbit.telaio.showcase.config;

import com.paganbit.telaio.mongo.MongoDal;
import com.paganbit.telaio.showcase.dal.DalPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB transaction setup for the showcase: production-grade multi-document transactions for the
 * Mongo-backed DALs (the docker-compose {@code mongo} service runs as a single-node replica set, a
 * server-side requirement for transactions).
 *
 * <p>The manager is declared under the Telaio qualifier {@link MongoDal#TRANSACTION_MANAGER_BEAN_NAME}
 * with {@code defaultCandidate = false}. In this mixed jpa+mongo application a plain
 * {@code MongoTransactionManager} bean would be a default-candidate {@code TransactionManager}: Spring
 * Boot's JPA autoconfiguration would then skip its {@code JpaTransactionManager} altogether and the
 * JPA DALs would bind to the Mongo manager (and, were the JPA manager declared explicitly as well, the
 * two candidates would make the JPA DALs' qualifier-less setter ambiguous). The qualified,
 * non-default bean is invisible to Spring Boot's condition and to by-type
 * autowiring, and visible only to the Mongo DALs' qualified injection point. The autoconfigured
 * fallback backs off because a bean with that name already exists.</p>
 */
@Configuration
@EnableMongoRepositories(
    basePackageClasses = DalPackage.class,
    includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = MongoRepository.class))
public class MongoConfiguration {

    @Bean(name = MongoDal.TRANSACTION_MANAGER_BEAN_NAME, defaultCandidate = false)
    MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }
}
