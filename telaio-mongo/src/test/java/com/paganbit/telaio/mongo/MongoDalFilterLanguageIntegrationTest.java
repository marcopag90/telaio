package com.paganbit.telaio.mongo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.core.exception.DalFailureKind;
import com.paganbit.telaio.core.exception.DalInvalidFilterException;
import com.paganbit.telaio.core.transaction.PassThroughTransactionManager;
import com.paganbit.telaio.mongo.filter.JsonAwareFilterQueryConverter;
import com.turkraft.springfilter.converter.FilterQueryConverter;
import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.InvalidSyntaxException;
import jakarta.validation.Validator;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.testcontainers.mongodb.MongoDBContainer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Executes every operator the Turkraft Spring Filter language offers to the Mongo backend through the
 * real {@link MongoDal} read path (autoconfigured {@code @Primary} converter, real {@code FilterBuilder},
 * the qualified {@code telaioMongoTransactionManager}) against a live server (Testcontainers) seeded with a
 * fixed six-document dataset, and asserts the documents each filter selects.
 *
 * <p>The vocabulary under test is the 21 shared core definitions — the upstream {@code mongo-language}
 * artifact is empty, so no extra functions exist here — plus the Mongo-specific surface: {@code Map} key
 * access, {@code @DBRef} reference keys ({@code $id}/{@code $ref}), {@code ObjectId} identifiers and
 * {@code UUID} fields. Backend-specific semantics are pinned as they are — {@code today()} is a BSON date
 * at local midnight, {@code ~} escapes everything but {@code *}, an array compared with a scalar never
 * matches ({@code 'red' in tags} is the portable form), {@code is empty} applies to arrays only, a
 * {@code @DBRef} is addressed through its {@code $id}/{@code $ref} keys and never dereferenced — so a
 * change in Turkraft's behavior fails here first. Error classification is pinned too: unknown fields and
 * unknown functions are client faults, unconvertible literals are server faults, the same way on every
 * backend.</p>
 *
 * <p>Uses a plain {@code @SpringBootTest} because {@code @DataMongoTest} excludes Turkraft's and telaio's
 * autoconfigurations. Every list and map field is initialised non-null: Spring Data omits null properties
 * and {@code $size}/{@code $objectToArray} fail server-side on a missing field.</p>
 */
@SpringBootTest(
    classes = MongoDalFilterLanguageIntegrationTest.TestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.mongodb.representation.uuid=standard")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MongoDalFilterLanguageIntegrationTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(id|ownerId|owner|category):(\\w+)}");

    /**
     * The DAL under test, registered and wired by Spring exactly like an application DAL
     * ({@code @DalService} + setter injection + {@code afterPropertiesSet}).
     */
    @Autowired
    private GizmoDal dal;

    @Autowired
    private MongoOperations mongoOperations;

    @Autowired
    private FilterStringConverter filterStringConverter;

    @Autowired
    private FilterQueryConverter queryConverter;

    private final Map<String, String> idsByCode = new HashMap<>();
    private final Map<String, String> ownerIdsByCode = new HashMap<>();
    private final Map<String, String> ownerRefIds = new HashMap<>();
    private final Map<String, String> categoryRefIds = new HashMap<>();

    @BeforeAll
    void seedOnce() {
        seed();
    }

    @Test
    void wiringUsesTheAutoconfiguredCollaborators() {
        assertThat(queryConverter).isInstanceOf(JsonAwareFilterQueryConverter.class);
        // Standalone mongod: no MongoTransactionManager bean, so the qualified bean is core's no-op fallback.
        assertThat(dal.getTransactionManager()).isInstanceOf(PassThroughTransactionManager.class);
        assertThat(read("").getTotalElements()).isEqualTo(6);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positiveCases")
    void filterSelectsExpectedDocuments(String filter, Set<String> expectedCodes) {
        Page<Gizmo> page = read(filter);

        assertThat(page.getContent()).extracting(Gizmo::getCode)
            .containsExactlyInAnyOrderElementsOf(expectedCodes);
        assertThat(page.getTotalElements()).isEqualTo(expectedCodes.size());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFilters")
    void inapplicableFilterIsRejectedAsClientFault(String filter) {
        assertThatThrownBy(() -> read(filter)).isInstanceOf(DalInvalidFilterException.class);
    }

    @Test
    void malformedFilterIsRejectedByTheParser() {
        assertThatThrownBy(() -> filterStringConverter.convert("(((")).isInstanceOf(InvalidSyntaxException.class);
    }

    @Test
    void jpaOnlyFunctionIsRejectedByTheParserOnThisClasspath() {
        // jpa-language is not on the Mongo backend's classpath, so its functions are unknown to the parser,
        // which rejects them with an UnsupportedOperationException before any backend is reached (the web
        // layer maps that to the same 400 as an inapplicable filter).
        assertThatThrownBy(() -> filterStringConverter.convert("abs(quantity) > 1"))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("abs");
    }

    static Stream<Arguments> positiveCases() {
        return Stream.of(
            // --- comparison operators (and their aliases) ---
            c("quantity : 10", 1),
            c("quantity = 10", 1),
            c("status : 'ACTIVE'", 1, 5),
            c("active : true", 1, 3, 4, 6),
            c("price : 9.99", 1),
            c("name : 'alpha nut'", 2),
            c("quantity ! 10", 2, 3, 4, 5, 6),
            c("status <> 'DELETED'", 1, 2, 3, 5, 6),
            c("quantity > 10", 3, 5),
            c("quantity >: 10", 1, 3, 5),
            c("quantity >= 10", 1, 3, 5),
            c("quantity < 7", 2, 4),
            c("quantity <: 7", 2, 4, 6),
            c("quantity <= 7", 2, 4, 6),
            c("price > 100", 3, 5),
            c("rating >: 4.5", 1, 3),
            c("quantity between 7 and 25", 1, 3, 6),
            c("cost_price > 100", 3, 5),
            c("costPrice > 100", 3, 5),
            // --- nested (embedded) documents ---
            c("address.city : 'Rome'", 1, 2),
            c("address.zip_code : '10115'", 6),
            c("address.zipCode : '10115'", 6),
            c("address.city is null", 4),
            // --- logic, precedence, parentheses ---
            c("status : 'ACTIVE' and active : true", 1),
            c("status : 'ACTIVE' or status : 'ARCHIVED'", 1, 3, 5),
            c("quantity > 20 or active : false and status : 'PENDING'", 2, 3, 5),
            c("(quantity > 20 or active : false) and status : 'PENDING'", 2),
            c("not status : 'ACTIVE'", 2, 3, 4, 6),
            c("not (active : true or quantity > 50)", 2),
            c("active : true xor status : 'ACTIVE'", 3, 4, 5, 6),
            c("active : true and not active : true"),
            // --- string matching: regex, * is the only wildcard, % and _ are literal ---
            c("name ~ 'Alpha'", 1),
            c("name ~ 'alpha'", 2),
            c("name ~ 'Alpha*'", 1),
            c("name ~ '*gear'", 3),
            c("name ~ 'Beta%'"),
            c("name ~ 'gamma_spring'", 4),
            c("description ~ 'wild%card'", 5),
            c("name like 'Epsilon'", 6),
            c("name ~~ 'ALPHA'", 1, 2),
            c("name ~~ 'alpha*'", 1, 2),
            c("name ilike 'EPSILON'", 6),
            c("name ~ ['Alpha*', 'Beta*']", 1, 3),
            c("name ~~ ['*BOLT', '*GEAR']", 1, 3),
            c("description ~ 'padded'", 4),
            // --- arrays: element membership is spelled `literal in array` ---
            c("'red' in tags", 1, 4),
            c("tags : 'red'"),
            c("'blue' in tags or 'large' in tags", 3, 4, 5),
            c("tags in ['blue', 'large']"),
            c("tags is empty", 2, 6),
            c("tags is not empty", 1, 3, 4, 5),
            c("size(tags) > 1", 1, 4),
            c("size(tags) : 0", 2, 6),
            c("size(lines) >: 2", 1, 4, 6),
            c("'shipped' in lines.status", 1, 3, 5, 6),
            c("lines is empty", 2),
            // --- map fields: keys are dynamic, so unknown keys match nothing instead of failing ---
            c("attributes.color : 'red'", 1, 4),
            c("attributes.color in ['red', 'blue']", 1, 3, 4),
            c("attributes.color ~ 'bl*'", 3),
            c("attributes.color is null", 2, 5, 6),
            c("attributes.missingKey : 'x'"),
            c("size(attributes) : 0", 2, 6),
            c("attributes is empty", 2, 6),
            c("attributes is not empty", 1, 3, 4, 5),
            c("counts.stock > 5", 1, 5),
            // --- schemaless sub-documents (Object / List<Map>): paths below them are forwarded unchecked ---
            c("payload.kind : 'alpha'", 1),
            c("payload.nested.tag : 'deep'", 1),
            c("payload.missing : 'x'"),
            c("'boot' in events.type", 1),
            // --- renamed field with a multi-pattern like (CollectionLikeNode) ---
            c("address.zip_code ~ ['101*', '787*']", 5, 6),
            // --- null / empty ---
            c("description is null", 2, 6),
            c("description is not null", 1, 3, 4, 5),
            c("description : ''", 3),
            c("description is empty"),
            c("greeting is null", 2, 4, 5),
            c("owner is null", 4),
            c("owner is not null", 1, 2, 3, 5, 6),
            // --- temporal literals compare as BSON dates ---
            c("createdAt > '2021-01-01T00:00:00Z'", 2, 3, 5, 6),
            c("createdAt < '2020-06-01T00:00:00Z'", 1, 4),
            c("createdAt : '2020-01-15T10:00:00Z'", 1),
            c("createdAt in ['2020-01-15T10:00:00Z', '2019-03-01T00:00:00Z']", 1, 4),
            c("dueDate : '2021-06-30'", 3),
            c("dueDate between '2020-01-01' and '2022-12-31'", 1, 3, 5),
            c("seenAt >: today()", 1, 3),
            c("seenAt < today()", 2, 4, 5, 6),
            // --- identifiers: String @Id and ObjectId fields compare as ObjectIds, UUIDs as UUIDs ---
            c("id : '{id:G1}'", 1),
            c("id in ['{id:G1}', '{id:G3}']", 1, 3),
            c("id ! '{id:G1}'", 2, 3, 4, 5, 6),
            c("ownerId : '{ownerId:G2}'", 2),
            c("ownerId in ['{ownerId:G1}', '{ownerId:G4}']", 1, 4),
            c("externalId : '11111111-1111-1111-1111-111111111111'", 1),
            c("externalId in ['22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333']", 2, 3),
            // --- @DBRef references via their $id / $ref keys (no dereferencing under $expr) ---
            c("owner.$id : '{owner:O1}'", 1, 2),
            c("owner.$id in ['{owner:O2}', '{owner:O3}']", 3, 5, 6),
            c("owner.$ref : 'filter_owners'", 1, 2, 3, 5, 6),
            c("categories.$id : '{category:C2}'", 1, 3, 4),
            c("categories.$id in ['{category:C1}', '{category:C3}']", 1, 4, 5),
            c("categories.$id not in ['{category:C1}', '{category:C2}']", 2, 5, 6),
            // --- placeholder ---
            c("greeting : `hello`", 1, 3, 6)
        );
    }

    static Stream<Arguments> invalidFilters() {
        return Stream.of(
            // unknown fields: rejected by the JSON-name rewriter, never reach the server
            Arguments.of("nope : 1"),
            Arguments.of("address.nope : 'x'"),
            Arguments.of("owner.$x : 1"),
            Arguments.of("name.length : 3"),
            Arguments.of("externalId.leastSignificantBits : 1"),
            Arguments.of("createdAt.nano : 0"),
            // properties the wire exposes but the document does not store (computed getters), root and nested
            Arguments.of("profit > 10"),
            Arguments.of("lines.subtotal > 1"),
            // a stored reference is never dereferenced: only its $id/$ref/$db keys can be addressed
            Arguments.of("owner.name : 'Acme'"),
            Arguments.of("owner.$id.x : 1"),
            // a map key must not look like an operator or a reference key
            Arguments.of("attributes.$id : 'x'")
        );
    }

    /**
     * Literals that do not convert to the field's type. By decision these are <em>not</em> client faults:
     * the conversion failure ({@code ConversionFailedException}, or the {@code IllegalArgumentException} of
     * a malformed {@code ObjectId}/{@code UUID}) propagates as a server fault — exactly as on the JPA backend,
     * where the same literals fail inside the database.
     */
    static Stream<Arguments> unconvertibleLiterals() {
        return Stream.of(
            Arguments.of("quantity : 'abc'"),
            Arguments.of("status : 'BOGUS'"),
            Arguments.of("createdAt : 'yesterday'"),
            Arguments.of("externalId : 'not-a-uuid'"),
            Arguments.of("id : 'zzz'")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unconvertibleLiterals")
    void unconvertibleLiteralIsAServerFaultOnThisBackend(String filter) {
        assertThatThrownBy(() -> read(filter))
            .isNotInstanceOf(DalInvalidFilterException.class)
            .satisfies(failure -> assertThat(DalFailureKind.of(failure)).isEqualTo(DalFailureKind.SERVER_ERROR));
    }

    private static Arguments c(String filter, int... codes) {
        return Arguments.of(filter, Arrays.stream(codes).mapToObj(n -> "G" + n).collect(Collectors.toSet()));
    }

    private Page<Gizmo> read(String filter) {
        return Objects.requireNonNull(dal).read(filter.isEmpty() ? null : filterStringConverter.convert(resolve(filter)),
            PageRequest.of(0, 100));
    }

    /**
     * Replaces {@code {id:Gn}}, {@code {ownerId:Gn}}, {@code {owner:On}} and {@code {category:Cn}} tokens
     * with the identifiers generated at seed time.
     */
    private String resolve(String filter) {
        Matcher matcher = PLACEHOLDER.matcher(filter);
        return matcher.replaceAll(match -> {
            Map<String, String> ids = switch (match.group(1)) {
                case "id" -> idsByCode;
                case "ownerId" -> ownerIdsByCode;
                case "owner" -> ownerRefIds;
                default -> categoryRefIds;
            };
            return Objects.requireNonNull(ids.get(match.group(2)), () -> "unknown placeholder " + match.group());
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Seed: six documents chosen so that every case above has matching and non-matching documents.
    // ---------------------------------------------------------------------------------------------

    private void seed() {
        mongoOperations.dropCollection(Gizmo.class);
        mongoOperations.dropCollection(Owner.class);
        mongoOperations.dropCollection(Category.class);

        Owner acme = owner("O1", "Acme");
        Owner globex = owner("O2", "Globex");
        Owner initech = owner("O3", "Initech");
        Category c1 = category("C1", "tools");
        Category c2 = category("C2", "parts");
        Category c3 = category("C3", "bulk");
        Instant now = Instant.now();
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        Gizmo g1 = gizmo("G1", "Alpha bolt", "small part", 10, 9.99, 4.5, true, GizmoStatus.ACTIVE,
            "2020-01-15T10:00:00Z", 5.00, address("Rome", "00100"), List.of("red", "small"), "Hello world!", now,
            acme, List.of(c1, c2), Map.of("color", "red", "size", "small"), Map.of("stock", 10));
        g1.getLines().add(line(100, "shipped"));
        g1.getLines().add(line(50, "pending"));
        g1.setPayload(Map.of("kind", "alpha", "nested", Map.of("tag", "deep", "level", 2)));
        g1.getEvents().add(Map.of("type", "boot"));
        Gizmo g2 = gizmo("G2", "alpha nut", null, 0, 100.00, 3.0, false, GizmoStatus.PENDING,
            "2099-12-31T23:00:00Z", 60.00, address("Rome", "00184"), List.of(), null, twoDaysAgo,
            acme, List.of(), Map.of(), Map.of());
        Gizmo g3 = gizmo("G3", "Beta gear", "", 25, 250.50, 5.0, true, GizmoStatus.ARCHIVED,
            "2021-06-30T12:00:00Z", 200.00, address("Austin", "73301"), List.of("blue"), "Hello world!", now,
            globex, List.of(c2), Map.of("color", "blue"), Map.of("stock", 3));
        g3.getLines().add(line(300, "shipped"));
        Gizmo g4 = gizmo("G4", "gamma_spring", "  padded  ", -5, 1.00, 0.0, true, GizmoStatus.DELETED,
            "2019-03-01T00:00:00Z", 0.00, null, List.of("red", "blue", "green"), null, twoDaysAgo,
            null, List.of(c1, c2, c3), Map.of("color", "red", "size", "large", "grade", "A"), Map.of());
        g4.getLines().add(line(10, "pending"));
        g4.getLines().add(line(20, "pending"));
        g4.getLines().add(line(30, "cancelled"));
        Gizmo g5 = gizmo("G5", "Delta 100%", "wild%card", 100, 1000.00, 1.5, false, GizmoStatus.ACTIVE,
            "2022-11-11T09:30:00Z", 900.00, address("Austin", "78701"), List.of("large"), null, twoDaysAgo,
            globex, List.of(c3), Map.of("size", "large"), Map.of("stock", 100));
        g5.getLines().add(line(500, "shipped"));
        Gizmo g6 = gizmo("G6", "Epsilon", null, 7, 49.95, 2.25, true, GizmoStatus.PENDING,
            "2023-05-05T15:45:00Z", 40.00, address("Berlin", "10115"), List.of(), "Hello world!", twoDaysAgo,
            initech, List.of(), Map.of(), Map.of());
        g6.getLines().add(line(75, "shipped"));
        g6.getLines().add(line(25, "shipped"));

        for (Gizmo gizmo : List.of(g1, g2, g3, g4, g5, g6)) {
            dal.getRepository().save(gizmo);
            idsByCode.put(gizmo.getCode(), gizmo.getId());
            ownerIdsByCode.put(gizmo.getCode(), gizmo.getOwnerId().toHexString());
        }
    }

    private Owner owner(String code, String name) {
        Owner owner = new Owner();
        owner.setName(name);
        mongoOperations.save(owner);
        ownerRefIds.put(code, owner.getId());
        return owner;
    }

    private Category category(String code, String label) {
        Category category = new Category();
        category.setLabel(label);
        mongoOperations.save(category);
        categoryRefIds.put(code, category.getId());
        return category;
    }

    private static Address address(String city, String zipCode) {
        Address address = new Address();
        address.setCity(city);
        address.setZipCode(zipCode);
        return address;
    }

    private static Line line(double amount, String status) {
        Line line = new Line();
        line.setAmount(amount);
        line.setStatus(status);
        return line;
    }

    @SuppressWarnings("java:S107")
    private static Gizmo gizmo(
        String code, String name, @Nullable String description, int quantity, double price, double rating,
        boolean active, GizmoStatus status, String createdAt, double costPrice, @Nullable Address address,
        List<String> tags, @Nullable String greeting, Instant seenAt, @Nullable Owner owner,
        List<Category> categories, Map<String, String> attributes, Map<String, Integer> counts
    ) {
        Instant created = Instant.parse(createdAt);
        Gizmo gizmo = new Gizmo();
        gizmo.setCode(code);
        gizmo.setName(name);
        gizmo.setDescription(description);
        gizmo.setGreeting(greeting);
        gizmo.setQuantity(quantity);
        gizmo.setPrice(price);
        gizmo.setCostPrice(costPrice);
        gizmo.setRating(rating);
        gizmo.setActive(active);
        gizmo.setStatus(status);
        gizmo.setCreatedAt(created);
        gizmo.setSeenAt(seenAt);
        gizmo.setDueDate(LocalDate.ofInstant(created, ZoneOffset.UTC));
        String digit = code.substring(1);
        gizmo.setExternalId(UUID.fromString(digit.repeat(8) + "-" + digit.repeat(4) + "-" + digit.repeat(4)
            + "-" + digit.repeat(4) + "-" + digit.repeat(12)));
        gizmo.setOwnerId(new ObjectId());
        gizmo.setAddress(address);
        gizmo.setTags(new ArrayList<>(tags));
        gizmo.setOwner(owner);
        gizmo.setCategories(new ArrayList<>(categories));
        gizmo.setAttributes(new HashMap<>(attributes));
        gizmo.setCounts(new HashMap<>(counts));
        return gizmo;
    }

    // ---------------------------------------------------------------------------------------------
    // Fixture model
    // ---------------------------------------------------------------------------------------------

    @DalService(name = "gizmos")
    static class GizmoDal extends MongoDal<Gizmo, String> {
    }

    enum GizmoStatus {ACTIVE, PENDING, ARCHIVED, DELETED}

    @Document("filter_gizmos")
    @Getter
    @Setter
    static class Gizmo {

        @Id
        private String id;

        private String code;

        private String name;

        @Nullable
        private String description;

        @Nullable
        private String greeting;

        private int quantity;

        private double price;

        @JsonProperty("cost_price")
        private double costPrice;

        private double rating;

        private boolean active;

        private GizmoStatus status;

        private UUID externalId;

        private ObjectId ownerId;

        private Instant createdAt;

        private Instant seenAt;

        private LocalDate dueDate;

        @Nullable
        private Address address;

        private List<String> tags = new ArrayList<>();

        private List<Line> lines = new ArrayList<>();

        private Map<String, String> attributes = new HashMap<>();

        private Map<String, Integer> counts = new HashMap<>();

        @DBRef
        @Nullable
        private Owner owner;

        @DBRef
        private List<Category> categories = new ArrayList<>();

        /**
         * Schemaless sub-document: the mapping context cannot describe its keys, so any path below it is
         * forwarded unchecked.
         */
        private Object payload = Map.of();

        private List<Map<String, Object>> events = new ArrayList<>();

        /**
         * Serialized but not stored: visible to Jackson as {@code profit}, absent from the document.
         */
        public double getProfit() {
            return price - costPrice;
        }
    }

    @Getter
    @Setter
    static class Address {

        private String city;

        @JsonProperty("zip_code")
        private String zipCode;
    }

    @Getter
    @Setter
    static class Line {

        private double amount;

        private String status;

        public double getSubtotal() {
            return amount;
        }
    }

    @Document("filter_owners")
    @Getter
    @Setter
    static class Owner {

        @Id
        private String id;

        private String name;
    }

    @Document("filter_categories")
    @Getter
    @Setter
    static class Category {

        @Id
        private String id;

        private String label;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableMongoRepositories(considerNestedRepositories = true)
    @Import(GizmoDal.class)
    static class TestApp {

        @SuppressWarnings("unused")
        interface GizmoRepository extends MongoDalRepository<Gizmo, String> {
        }

        /**
         * Turkraft's {@code FilterConversionServiceConfiguration} requires a pre-existing
         * {@link ConversionService} bean (supplied as {@code mvcConversionService} in web apps); this bare
         * non-web context has none.
         */
        @Bean
        ConversionService conversionService() {
            return new DefaultFormattingConversionService();
        }

        /**
         * {@code AbstractDal} requires a validator adapter; no Bean Validation provider is on this module's
         * classpath (nothing is validated here — the DAL is only read).
         */
        @Bean
        SpringValidatorAdapter validatorAdapter() {
            return new SpringValidatorAdapter(mock(Validator.class));
        }

        @Bean
        @ServiceConnection
        MongoDBContainer mongoContainer() {
            return new MongoDBContainer(System.getProperty("testcontainers.image.mongodb", "mongo:8"));
        }
    }
}
