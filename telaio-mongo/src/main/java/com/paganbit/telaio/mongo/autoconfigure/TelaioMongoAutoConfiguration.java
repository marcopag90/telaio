package com.paganbit.telaio.mongo.autoconfigure;

import com.paganbit.telaio.core.autoconfigure.TelaioCoreAutoConfiguration;
import com.paganbit.telaio.core.transaction.PassThroughTransactionManager;
import com.paganbit.telaio.mongo.MongoDal;
import com.paganbit.telaio.mongo.filter.FilterQueryConverter;
import com.paganbit.telaio.mongo.filter.JsonAwareFilterQueryConverter;
import com.turkraft.springfilter.helper.FieldTypeResolver;
import com.turkraft.springfilter.helper.JsonNodeHelper;
import com.turkraft.springfilter.helper.JsonNodeHelperImpl;
import com.turkraft.springfilter.transformer.FilterJsonNodeTransformer;
import com.turkraft.springfilter.transformer.processor.factory.FilterNodeProcessorFactories;
import com.turkraft.springfilter.transformer.processor.factory.FilterNodeProcessorFactoriesImpl;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Telaio Mongo.
 *
 * <p>Most Mongo DAL setup is performed by user-declared {@code @DalService}-annotated classes; their
 * registration and proxy wiring are driven by the post-processors contributed by
 * {@link TelaioCoreAutoConfiguration}. This autoconfiguration enforces the ordering and classpath guard
 * ({@code @ConditionalOnClass(MongoOperations.class)}) that must be satisfied before any Mongo DAL is
 * processed, and contributes the Mongo-specific beans below.</p>
 *
 * <p>It registers a {@link JsonAwareFilterQueryConverter} as the default {@link FilterQueryConverter},
 * built on Turkraft's mongo transformer beans. This lets filter queries reference {@code @JsonProperty}
 * wire names; {@code MongoDal} picks it up automatically through its {@code @Autowired} setter. It also
 * registers the {@link PlatformTransactionManager} routed to every {@link MongoDal} under the
 * {@link MongoDal#TRANSACTION_MANAGER_BEAN_NAME} qualifier, plus a transitional Jackson 2
 * {@code ObjectMapper} required by the upstream Turkraft mongo artifact. All beans are purely additive
 * and overridable — define your own {@link FilterQueryConverter}, or your own
 * {@link PlatformTransactionManager} named {@link MongoDal#TRANSACTION_MANAGER_BEAN_NAME}, and the
 * corresponding autoconfigured bean backs off.</p>
 *
 * @author Marco Pagan
 * @since 1.2.0
 */
@AutoConfiguration(
    after = {
        TelaioCoreAutoConfiguration.class,
        DataMongoAutoConfiguration.class,
        FilterNodeProcessorFactoriesImpl.class,
        JsonNodeHelperImpl.class
    },
    afterName = "com.turkraft.springfilter.helper.FieldTypeResolverImpl"
)
@ConditionalOnClass({MongoOperations.class, FilterJsonNodeTransformer.class})
public class TelaioMongoAutoConfiguration {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(TelaioMongoAutoConfiguration.class);

    /**
     * Jackson 2 {@code ObjectMapper} required at startup by the Turkraft mongo autoconfiguration
     * ({@code JsonNodeHelperImpl} constructor-injects one; Spring Boot 4 no longer provides it).
     * Backs off when the application declares its own. Necessarily a regular autowire candidate,
     * so it is visible to any bean injecting a Jackson 2 mapper by type.
     */
    // TODO(roadmap): Jackson 2 containment — remove together with the private mapper in
    //  JsonAwareFilterQueryConverter once the upstream Turkraft mongo artifact drops com.fasterxml.
    //  See docs/roadmap.md.
    @Bean
    @ConditionalOnMissingBean(com.fasterxml.jackson.databind.ObjectMapper.class)
    com.fasterxml.jackson.databind.ObjectMapper telaioMongoJackson2ObjectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    }

    /**
     * Default {@link FilterQueryConverter}, built on Turkraft's mongo transformer beans. The
     * {@link ConversionService} and Jackson 3 {@link ObjectMapper} fall back to defaults in
     * contexts that define none.
     */
    @Bean
    @ConditionalOnMissingBean(FilterQueryConverter.class)
    @ConditionalOnBean({FilterNodeProcessorFactories.class, FieldTypeResolver.class, JsonNodeHelper.class})
    FilterQueryConverter jsonAwareFilterQueryConverter(
        ObjectProvider<ConversionService> conversionService,
        FilterNodeProcessorFactories processorFactories,
        FieldTypeResolver fieldTypeResolver,
        JsonNodeHelper jsonNodeHelper,
        ObjectProvider<ObjectMapper> objectMapper
    ) {
        return new JsonAwareFilterQueryConverter(
            conversionService.getIfAvailable(ApplicationConversionService::getSharedInstance),
            processorFactories,
            fieldTypeResolver,
            jsonNodeHelper,
            objectMapper.getIfAvailable(() -> JsonMapper.builder().build())
        );
    }

    /**
     * Transaction manager routed to every {@link MongoDal} via its qualified setter.
     * {@code defaultCandidate = false} keeps it out of plain by-type autowiring, so multi-backend
     * contexts stay ambiguity-free. A user-declared {@link MongoTransactionManager} takes
     * precedence over the no-op fallback; a bean declared under this name replaces the arrangement
     * entirely.
     */
    @Bean(name = MongoDal.TRANSACTION_MANAGER_BEAN_NAME, defaultCandidate = false)
    @ConditionalOnMissingBean(name = MongoDal.TRANSACTION_MANAGER_BEAN_NAME)
    PlatformTransactionManager telaioMongoTransactionManager(
        ObjectProvider<MongoTransactionManager> mongoTransactionManager
    ) {
        PlatformTransactionManager userDeclared = mongoTransactionManager.getIfUnique();
        if (userDeclared != null) {
            return userDeclared;
        }
        if (mongoTransactionManager.stream().findAny().isPresent()) {
            log.warn("Multiple MongoTransactionManager beans found: falling back to the no-op "
                + "transaction manager for Mongo DALs. Declare a PlatformTransactionManager bean "
                + "named '{}' to resolve the ambiguity.", MongoDal.TRANSACTION_MANAGER_BEAN_NAME);
        }
        return new PassThroughTransactionManager();
    }
}
