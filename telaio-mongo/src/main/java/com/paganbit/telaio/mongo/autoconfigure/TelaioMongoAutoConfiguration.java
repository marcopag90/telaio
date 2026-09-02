package com.paganbit.telaio.mongo.autoconfigure;

import com.paganbit.telaio.core.autoconfigure.TelaioCoreAutoConfiguration;
import com.paganbit.telaio.core.json.JsonPropertyPathResolver;
import com.paganbit.telaio.core.transaction.PassThroughTransactionManager;
import com.paganbit.telaio.introspection.SimpleTypeContributor;
import com.paganbit.telaio.mongo.MongoDal;
import com.paganbit.telaio.mongo.filter.JsonAwareFilterQueryConverter;
import com.paganbit.telaio.mongo.jackson.ObjectIdJacksonModule;
import com.turkraft.springfilter.converter.FilterQueryConverter;
import com.turkraft.springfilter.converter.FilterQueryConverterImpl;
import com.turkraft.springfilter.converter.FilterStringConverter;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Set;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Telaio Mongo.
 *
 * <p>Most Mongo DAL setup is performed by user-declared {@code @DalService}-annotated classes; their
 * registration and proxy wiring are driven by the post-processors contributed by
 * {@link TelaioCoreAutoConfiguration}. This autoconfiguration enforces the ordering and classpath guard
 * ({@code @ConditionalOnClass(MongoOperations.class)}) that must be satisfied before any Mongo DAL is
 * processed, and contributes the Mongo-specific beans below.</p>
 *
 * <p>It decorates Turkraft's {@link FilterQueryConverter} with a {@link JsonAwareFilterQueryConverter},
 * marked primary so it is the converter injected into {@code MongoDal}. This lets filter queries
 * reference {@code @JsonProperty} wire names. It also registers the {@link PlatformTransactionManager}
 * routed to every {@link MongoDal} under the {@link MongoDal#TRANSACTION_MANAGER_BEAN_NAME} qualifier.
 * All beans are purely additive and overridable — declare your own {@link FilterQueryConverter} bean (marked
 * {@code @Primary}, since the framework's own converter stays in the context), or your own
 * {@link PlatformTransactionManager} named {@link MongoDal#TRANSACTION_MANAGER_BEAN_NAME}, and the
 * corresponding autoconfigured bean backs off.</p>
 *
 * @author Marco Pagan
 * @since 2.0.0
 */
@AutoConfiguration(
    after = {
        TelaioCoreAutoConfiguration.class,
        DataMongoAutoConfiguration.class,
        FilterQueryConverterImpl.class
    }
)
@ConditionalOnClass({MongoOperations.class, FilterQueryConverter.class})
public class TelaioMongoAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TelaioMongoAutoConfiguration.class);

    /**
     * Jackson 3 module mapping {@link ObjectId} to its hexadecimal string form.
     */
    @Bean
    @ConditionalOnMissingBean
    ObjectIdJacksonModule telaioObjectIdJacksonModule() {
        return new ObjectIdJacksonModule();
    }

    /**
     * Contributes {@link ObjectId} to the framework's simple-type classification, so ObjectId
     * identifiers travel raw (not Base64-JSON) in the {@code {id}} path segment.
     */
    @Bean
    SimpleTypeContributor telaioMongoSimpleTypeContributor() {
        return () -> Set.of(ObjectId.class);
    }

    /**
     * Decorates Turkraft's converter so JSON wire names resolve to their underlying Java properties and
     * filters on fields the document does not expose or store are rejected as client faults.
     * Conditional on Turkraft's converter being present and on no other {@link FilterQueryConverter}
     * bean being declared (Turkraft's own is ignored), and marked {@link Primary @Primary} so it is the
     * converter injected into {@code MongoDal}. An application declaring its own converter takes over
     * both concerns: the framework's field checks are not applied around a replacement.
     *
     * @param delegate              Turkraft's autoconfigured converter to delegate the actual conversion to
     * @param filterStringConverter Turkraft's parser, used for the string form of a filter
     * @param pathResolver          the path resolver, used to translate {@code @JsonProperty} renames
     * @param mongoOperations       the template every {@code MongoDal} persists through; its converter's
     *                              mapping context is the authority on which properties a document stores,
     *                              so filters on non-persistent properties are rejected
     * @return the JSON-aware primary converter
     */
    @Bean
    @Primary
    @ConditionalOnBean(FilterQueryConverterImpl.class)
    @ConditionalOnMissingBean(value = FilterQueryConverter.class, ignored = FilterQueryConverterImpl.class)
    FilterQueryConverter jsonAwareFilterQueryConverter(
        FilterQueryConverterImpl delegate,
        FilterStringConverter filterStringConverter,
        JsonPropertyPathResolver pathResolver,
        MongoOperations mongoOperations
    ) {
        return new JsonAwareFilterQueryConverter(
            delegate,
            filterStringConverter,
            pathResolver,
            mongoOperations.getConverter().getMappingContext()
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
