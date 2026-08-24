package com.paganbit.telaio.mongo.filter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.node.FilterNode;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for {@link JsonAwareFilterQueryConverter}: a {@code q}-string filter
 * using the {@code @JsonProperty} wire name is parsed by the real Turkraft
 * {@link FilterStringConverter}, converted by the autoconfigured {@link FilterQueryConverter}, and
 * actually matches persisted documents on a live server (Testcontainers).
 *
 * <p>Uses a plain {@code @SpringBootTest} because {@code @DataMongoTest} excludes Turkraft's (and
 * telaio's) autoconfigurations. The {@code TestApp} must declare a {@link ConversionService} bean:
 * Turkraft's {@code FilterConversionServiceConfiguration} requires a pre-existing one (normally
 * {@code mvcConversionService}) and a bare non-web context has none.</p>
 */
@SpringBootTest(
    classes = JsonAwareFilterQueryConverterIntegrationTest.TestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class JsonAwareFilterQueryConverterIntegrationTest {

    @Autowired
    private FilterStringConverter filterStringConverter;

    @Autowired
    private FilterQueryConverter queryConverter;

    @Autowired
    private MongoOperations mongoOperations;

    @BeforeEach
    void setUp() {
        mongoOperations.remove(new Query(), Widget.class);
        Widget cheap = new Widget();
        cheap.setName("cheap");
        cheap.setStockCount(50);
        Widget big = new Widget();
        big.setName("big");
        big.setStockCount(150);
        mongoOperations.save(cheap);
        mongoOperations.save(big);
    }

    @Test
    void autoconfiguredConverterIsTheJsonAwareImplementation() {
        assertThat(queryConverter).isInstanceOf(JsonAwareFilterQueryConverter.class);
    }

    @Test
    void jsonWireNameFilterMatchesPersistedDocuments() {
        FilterNode node = filterStringConverter.convert("stock_count > 100");

        List<Widget> found = mongoOperations.find(queryConverter.convert(node, Widget.class), Widget.class);

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getName()).isEqualTo("big");
    }

    @Test
    void javaPropertyNameFilterStillMatchesPersistedDocuments() {
        FilterNode node = filterStringConverter.convert("stockCount > 100");

        List<Widget> found = mongoOperations.find(queryConverter.convert(node, Widget.class), Widget.class);

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getName()).isEqualTo("big");
    }

    @Test
    void objectIdFieldFilterMatchesPersistedDocuments() {
        // The ObjectIdAwareFieldTypeResolver decorator maps the ObjectId-typed @Id to Turkraft's
        // CustomObjectId, whose {"$oid": <hex>} shape parses into a real BSON ObjectId.
        mongoOperations.remove(new Query(), Gadget.class);
        Gadget first = new Gadget();
        first.setLabel("first");
        Gadget second = new Gadget();
        second.setLabel("second");
        mongoOperations.save(first);
        mongoOperations.save(second);

        FilterNode node = filterStringConverter.convert("id : '" + first.getId().toHexString() + "'");

        List<Gadget> found = mongoOperations.find(queryConverter.convert(node, Gadget.class), Gadget.class);

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getLabel()).isEqualTo("first");
    }

    @Getter
    @Setter
    static class Widget {

        @Id
        private String id;

        private String name;

        @JsonProperty("stock_count")
        private int stockCount;
    }

    @Getter
    @Setter
    static class Gadget {

        @Id
        private ObjectId id;

        private String label;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {

        @Bean
        ConversionService conversionService() {
            return new DefaultFormattingConversionService();
        }

        @Bean
        @ServiceConnection
        MongoDBContainer mongoContainer() {
            return new MongoDBContainer(System.getProperty("testcontainers.image.mongodb", "mongo:8"));
        }
    }
}
