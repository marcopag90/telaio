package com.paganbit.telaio.jpa;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paganbit.telaio.core.annotation.DalService;
import com.paganbit.telaio.core.exception.DalFailureKind;
import com.paganbit.telaio.core.exception.DalInvalidFilterException;
import com.paganbit.telaio.jpa.filter.JsonAwareFilterSpecificationConverter;
import com.turkraft.springfilter.converter.FilterSpecificationConverter;
import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.InvalidSyntaxException;
import jakarta.persistence.*;
import jakarta.validation.Validator;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Executes every operator and function the Turkraft Spring Filter language offers to the JPA backend
 * through the real {@link JpaDal} read path (autoconfigured {@code @Primary} converter, real
 * {@code FilterBuilder}, Boot's {@code JpaTransactionManager}) against an H2 schema seeded with a fixed
 * six-row dataset, and asserts the rows each filter selects.
 *
 * <p>The vocabulary under test is the 21 shared core definitions plus the 45 functions of
 * {@code jpa-language}, two of which cannot run here: {@code jsonText} compiles to PostgreSQL's
 * {@code jsonb_extract_path_text} (no H2 equivalent) and {@code countDistinct} has no JPA processor
 * (asserted as a rejected filter). Backend-specific semantics are pinned as they are — {@code today()} is
 * the current day <em>name</em>, {@code ~} keeps SQL {@code %}/{@code _} wildcards live, {@code is empty}
 * on a string never matches — so a change in Turkraft's behavior fails here first. Error classification
 * is pinned too: unknown fields and unsupported functions are client faults, unconvertible literals are
 * server faults, the same way on every backend.</p>
 *
 * <p>Uses a plain {@code @SpringBootTest} because {@code @DataJpaTest} excludes Turkraft's and telaio's
 * autoconfigurations; the class-level transaction rolls the seed back after each test.</p>
 */
@SpringBootTest(
    classes = JpaDalFilterLanguageIntegrationTest.TestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class JpaDalFilterLanguageIntegrationTest {

    private static final Pattern ID_PLACEHOLDER = Pattern.compile("\\{id:(W\\d)}");

    /**
     * The DAL under test, registered and wired by Spring exactly like an application DAL
     * ({@code @DalService} + setter injection + {@code afterPropertiesSet}).
     */
    @Autowired
    private WidgetDal dal;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private FilterStringConverter filterStringConverter;

    @Autowired
    private FilterSpecificationConverter specificationConverter;

    private final Map<String, Long> idsByCode = new HashMap<>();

    @BeforeEach
    void seedBeforeEachTest() {
        seed();
    }

    @Test
    void wiringUsesTheAutoconfiguredCollaborators() {
        assertThat(specificationConverter).isInstanceOf(JsonAwareFilterSpecificationConverter.class);
        assertThat(dal.getTransactionManager()).isInstanceOf(JpaTransactionManager.class);
        assertThat(read("").getTotalElements()).isEqualTo(6);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positiveCases")
    void filterSelectsExpectedRows(String filter, Set<String> expectedCodes) {
        Page<Widget> page = read(filter);

        assertThat(page.getContent()).extracting(Widget::getCode)
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
            c("dims.height_mm >: 50", 3, 5),
            c("dims.heightMm >: 50", 3, 5),
            c("dims.width : 10", 1),
            // --- logic, precedence, parentheses ---
            c("status : 'ACTIVE' and active : true", 1),
            c("status : 'ACTIVE' or status : 'ARCHIVED'", 1, 3, 5),
            c("quantity > 20 or active : false and status : 'PENDING'", 2, 3, 5),
            c("(quantity > 20 or active : false) and status : 'PENDING'", 2),
            c("not status : 'ACTIVE'", 2, 3, 4, 6),
            c("not (active : true or quantity > 50)", 2),
            c("active : true xor status : 'ACTIVE'", 3, 4, 5, 6),
            c("active : true and not active : true"),
            // --- string matching: * is the wildcard, SQL % and _ stay live, H2 is case-sensitive ---
            c("name ~ 'Alpha'", 1),
            c("name ~ 'alpha'", 2),
            c("name ~ 'Alpha*'", 1),
            c("name ~ '*gear'", 3),
            c("name ~ 'Beta%'", 3),
            c("name ~ 'gamma_spring'", 4),
            c("description ~ 'wild%card'", 5),
            c("name like 'Epsilon'", 6),
            c("name ~~ 'ALPHA'", 1, 2),
            c("name ~~ 'alpha*'", 1, 2),
            c("name ilike 'EPSILON'", 6),
            c("name ~ ['Alpha%', 'Beta%']", 1, 3),
            c("name ~~ ['%BOLT', '%GEAR']", 1, 3),
            c("description ~ 'padded'", 4),
            // --- collections and associations (plural paths are wrapped in EXISTS) ---
            c("tags : 'red'", 1, 4),
            c("'red' in tags", 1, 4),
            c("tags in ['blue', 'large']", 3, 4, 5),
            c("tags is empty", 2, 6),
            c("tags is not empty", 1, 3, 4, 5),
            c("size(tags) > 1", 1, 4),
            c("size(tags) : 0", 2, 6),
            c("size(lines) >: 2", 1, 4, 6),
            c("lines.status : 'shipped'", 1, 3, 5, 6),
            c("lines.amount > 200", 3, 5),
            c("lines is empty", 2),
            c("supplier.name : 'Acme'", 1, 2),
            c("supplier.country in ['US', 'DE']", 3, 5, 6),
            c("supplier is null", 4),
            c("supplier is not null", 1, 2, 3, 5, 6),
            // --- null / empty ---
            c("description is null", 2, 6),
            c("description is not null", 1, 3, 4, 5),
            c("description : ''", 3),
            c("description is empty"),
            c("greeting is null", 2, 4, 5),
            // --- temporal literals and functions ---
            c("createdAt > '2021-01-01T00:00:00Z'", 2, 3, 5, 6),
            c("createdAt < '2020-06-01T00:00:00Z'", 1, 4),
            c("dueDate : '2021-06-30'", 3),
            c("dueDate between '2020-01-01' and '2022-12-31'", 1, 3, 5),
            c("updatedAt < '2020-06-01T00:00:00'", 1, 4),
            c("startTime >: '12:00:00'", 2, 3, 6),
            c("dayOfWeek : today()", 1, 3),
            c("updatedAt < localDateTime()", 1, 3, 4, 5, 6),
            c("updatedAt < currentTimestamp()", 1, 3, 4, 5, 6),
            c("dueDate < localDate()", 1, 3, 4, 5, 6),
            c("dueDate > currentDate()", 2),
            // The time-of-day functions cannot be pinned to a value: the tautology only proves they
            // execute and compare against a LocalTime attribute without error.
            c("startTime <: localTime() or startTime >: localTime()", 1, 2, 3, 4, 5, 6),
            c("startTime <: currentTime() or startTime >: currentTime()", 1, 2, 3, 4, 5, 6),
            // --- identifiers ---
            c("id : {id:W1}", 1),
            c("id in [{id:W1}, {id:W3}]", 1, 3),
            c("externalId : '11111111-1111-1111-1111-111111111111'", 1),
            c("externalId in ['22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333']", 2, 3),
            // --- jpa-language functions: arithmetic ---
            c("abs(quantity) : 5", 4),
            c("neg(quantity) > 0", 4),
            c("sign(quantity) : -1", 4),
            c("sign(quantity) : 0", 2),
            c("mod(quantity, 5) : 0", 1, 2, 3, 4, 5),
            c("sqrt(price) > 30", 5),
            c("power(quantity, 2) : 100", 1),
            c("exp(rating) > 100", 3),
            c("ln(price) > 5", 3, 5),
            c("ceiling(price) : 10", 1),
            c("floor(price) : 49", 6),
            c("prod(quantity, 2) : 20", 1),
            c("quot(quantity, 5) : 5", 3),
            c("diff(price, cost_price) > 45", 3, 5),
            c("sum(price, cost_price) > 1000", 5),
            // --- jpa-language functions: aggregates and quantifiers over collections ---
            c("sum(lines.amount) > 200", 3, 5),
            c("avg(lines.amount) > 100", 3, 5),
            c("max(lines.amount) >: 100", 1, 3, 5),
            c("min(lines.amount) < 20", 4),
            c("count(lines) > 1", 1, 4, 6),
            c("greatest(lines.amount) > 400", 5),
            c("least(lines.amount) < 30", 4, 6),
            c("exists(lines.status : 'pending' and lines.amount > 15)", 1, 4),
            c("quantity > all(scores)", 1, 2, 5, 6),
            c("quantity < any(scores)", 4),
            c("quantity : some(scores)", 3),
            // --- jpa-language functions: strings ---
            c("length(name) < 8", 6),
            c("lower(name) : 'alpha nut'", 2),
            c("lower(name) ~ 'alpha*'", 1, 2),
            c("upper(name) : 'EPSILON'", 6),
            c("trim(description) : 'padded'", 4),
            c("substring(name, 1, 5) : 'Alpha'", 1),
            c("locate(name, 'a', 1) : 1", 2),
            c("concat(name, '-', code) : 'Epsilon-W6'", 6),
            // --- jpa-language functions: conversions ---
            c("toInteger(price) > 999", 5),
            c("toBigDecimal(quantity) > 20.5", 3, 5),
            c("toBigInteger(price) >: 250", 3, 5),
            c("toDouble(quantity) : 7.0", 6),
            c("toFloat(quantity) : 7.0", 6),
            c("toString(quantity) : '10'", 1),
            // --- placeholder ---
            c("greeting : `hello`", 1, 3, 6)
        );
    }

    static Stream<Arguments> invalidFilters() {
        return Stream.of(
            // unknown fields: rejected by the JSON-name rewriter, never reach Hibernate
            Arguments.of("nope : 1"),
            Arguments.of("dims.nope : 1"),
            Arguments.of("supplier.nope : 'x'"),
            Arguments.of("supplier.$x : 1"),
            Arguments.of("name.length : 3"),
            Arguments.of("externalId.leastSignificantBits : 1"),
            Arguments.of("createdAt.nano : 0"),
            // properties the wire exposes but the persistence unit does not map (@Transient), root and nested
            Arguments.of("profit > 10"),
            Arguments.of("lines.subtotal > 1"),
            // a function the parser knows but the JPA backend has no processor for
            Arguments.of("countDistinct(lines.status) > 1")
        );
    }

    /**
     * Literals that do not convert to the attribute's type. By decision these are <em>not</em> client
     * faults: they fail inside the persistence layer (Turkraft falls back to the raw literal, which becomes
     * a SQL {@code CAST} rejected at execution, or Hibernate rejects the value while building the predicate)
     * and surface as a {@link DataAccessException} — a server fault, exactly as on the Mongo backend.
     */
    static Stream<Arguments> unconvertibleLiterals() {
        return Stream.of(
            Arguments.of("quantity : 'abc'"),
            Arguments.of("status : 'BOGUS'"),
            Arguments.of("createdAt : 'yesterday'"),
            Arguments.of("id : 'abc'")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unconvertibleLiterals")
    void unconvertibleLiteralIsAServerFaultOnThisBackend(String filter) {
        assertThatThrownBy(() -> read(filter))
            .isInstanceOf(DataAccessException.class)
            .isNotInstanceOf(DalInvalidFilterException.class)
            .satisfies(failure -> assertThat(DalFailureKind.of(failure)).isEqualTo(DalFailureKind.SERVER_ERROR));
    }

    private static Arguments c(String filter, int... codes) {
        return Arguments.of(filter, Arrays.stream(codes).mapToObj(n -> "W" + n).collect(Collectors.toSet()));
    }

    private Page<Widget> read(String filter) {
        return dal.read(filter.isEmpty() ? null : filterStringConverter.convert(resolve(filter)),
            PageRequest.of(0, 100));
    }

    /**
     * Replaces {@code {id:Wn}} tokens with the generated id of the row with that code.
     */
    private String resolve(String filter) {
        Matcher matcher = ID_PLACEHOLDER.matcher(filter);
        return matcher.replaceAll(match -> String.valueOf(idsByCode.get(match.group(1))));
    }

    // ---------------------------------------------------------------------------------------------
    // Seed: six rows chosen so that every case above has matching and non-matching rows.
    // ---------------------------------------------------------------------------------------------

    private void seed() {
        String todayName = new SimpleDateFormat("EEEE").format(new Date());
        Supplier acme = supplier("Acme", "IT");
        Supplier globex = supplier("Globex", "US");
        Supplier initech = supplier("Initech", "DE");

        Widget w1 = widget("W1", "Alpha bolt", "small part", 10, "9.99", 4.5, true, WidgetStatus.ACTIVE,
            "2020-01-15T10:00:00Z", "08:00", "5.00", 10, 20, acme, List.of("red", "small"), List.of(1, 2, 3),
            "Hello world!", todayName);
        line(w1, "100", "shipped");
        line(w1, "50", "pending");
        Widget w2 = widget("W2", "alpha nut", null, 0, "100.00", 3.0, false, WidgetStatus.PENDING,
            "2099-12-31T23:00:00Z", "23:59", "60.00", 5, 5, acme, List.of(), List.of(), null, "Neverday");
        Widget w3 = widget("W3", "Beta gear", "", 25, "250.50", 5.0, true, WidgetStatus.ARCHIVED,
            "2021-06-30T12:00:00Z", "12:00", "200.00", 100, 200, globex, List.of("blue"), List.of(10, 25),
            "Hello world!", todayName);
        line(w3, "300", "shipped");
        Widget w4 = widget("W4", "gamma_spring", "  padded  ", -5, "1.00", 0.0, true, WidgetStatus.DELETED,
            "2019-03-01T00:00:00Z", "00:00", "0.00", 1, 1, null, List.of("red", "blue", "green"), List.of(5),
            null, "Neverday");
        line(w4, "10", "pending");
        line(w4, "20", "pending");
        line(w4, "30", "cancelled");
        Widget w5 = widget("W5", "Delta 100%", "wild%card", 100, "1000.00", 1.5, false, WidgetStatus.ACTIVE,
            "2022-11-11T09:30:00Z", "09:30", "900.00", 50, 50, globex, List.of("large"), List.of(7, 8, 9),
            null, "Neverday");
        line(w5, "500", "shipped");
        Widget w6 = widget("W6", "Epsilon", null, 7, "49.95", 2.25, true, WidgetStatus.PENDING,
            "2023-05-05T15:45:00Z", "15:45", "40.00", 7, 7, initech, List.of(), List.of(),
            "Hello world!", "Neverday");
        line(w6, "75", "shipped");
        line(w6, "25", "shipped");

        for (Widget widget : List.of(w1, w2, w3, w4, w5, w6)) {
            dal.getRepository().save(widget);
            idsByCode.put(widget.getCode(), widget.getId());
        }
        entityManager.flush();
    }

    private Supplier supplier(String name, String country) {
        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setCountry(country);
        entityManager.persist(supplier);
        return supplier;
    }

    @SuppressWarnings("java:S107")
    private static Widget widget(
        String code, String name, @Nullable String description, int quantity, String price, double rating,
        boolean active, WidgetStatus status, String createdAt, String startTime, String costPrice,
        int width, int heightMm, @Nullable Supplier supplier, List<String> tags, List<Integer> scores,
        @Nullable String greeting, String dayOfWeek
    ) {
        Instant created = Instant.parse(createdAt);
        Widget widget = new Widget();
        widget.setCode(code);
        widget.setName(name);
        widget.setDescription(description);
        widget.setQuantity(quantity);
        widget.setPrice(new BigDecimal(price));
        widget.setCostPrice(new BigDecimal(costPrice));
        widget.setRating(rating);
        widget.setActive(active);
        widget.setStatus(status);
        widget.setCreatedAt(created);
        widget.setUpdatedAt(LocalDateTime.ofInstant(created, ZoneOffset.UTC));
        widget.setDueDate(LocalDate.ofInstant(created, ZoneOffset.UTC));
        widget.setStartTime(LocalTime.parse(startTime));
        widget.setExternalId(UUID.fromString(code.substring(1).repeat(8) + "-" + code.substring(1).repeat(4)
            + "-" + code.substring(1).repeat(4) + "-" + code.substring(1).repeat(4) + "-" + code.substring(1).repeat(12)));
        Dimensions dims = new Dimensions();
        dims.setWidth(width);
        dims.setHeightMm(heightMm);
        widget.setDims(dims);
        widget.setSupplier(supplier);
        widget.setTags(new ArrayList<>(tags));
        widget.setScores(new ArrayList<>(scores));
        widget.setGreeting(greeting);
        widget.setDayOfWeek(dayOfWeek);
        return widget;
    }

    private static void line(Widget widget, String amount, String status) {
        Line line = new Line();
        line.setWidget(widget);
        line.setAmount(new BigDecimal(amount));
        line.setStatus(status);
        widget.getLines().add(line);
    }

    // ---------------------------------------------------------------------------------------------
    // Fixture model
    // ---------------------------------------------------------------------------------------------

    @DalService(name = "widgets")
    static class WidgetDal extends JpaDal<Widget, Long> {
    }

    enum WidgetStatus {ACTIVE, PENDING, ARCHIVED, DELETED}

    @Entity
    @Table(name = "filter_widgets")
    @Getter
    @Setter
    static class Widget {

        @Id
        @GeneratedValue
        private Long id;

        @Column(unique = true)
        private String code;

        private String name;

        @Nullable
        private String description;

        @Nullable
        private String greeting;

        private String dayOfWeek;

        private Integer quantity;

        @Column(precision = 12, scale = 2)
        private BigDecimal price;

        @JsonProperty("cost_price")
        @Column(precision = 12, scale = 2)
        private BigDecimal costPrice;

        private Double rating;

        private Boolean active;

        @Enumerated(EnumType.STRING)
        private WidgetStatus status;

        private UUID externalId;

        private LocalDate dueDate;

        private LocalDateTime updatedAt;

        private LocalTime startTime;

        private Instant createdAt;

        @Embedded
        private Dimensions dims;

        @ManyToOne(fetch = FetchType.LAZY)
        @Nullable
        private Supplier supplier;

        @ElementCollection
        @CollectionTable(name = "filter_widget_tags", joinColumns = @JoinColumn(name = "widget_id"))
        @Column(name = "tag")
        private List<String> tags = new ArrayList<>();

        @ElementCollection
        @CollectionTable(name = "filter_widget_scores", joinColumns = @JoinColumn(name = "widget_id"))
        @Column(name = "score")
        private List<Integer> scores = new ArrayList<>();

        @OneToMany(mappedBy = "widget", cascade = CascadeType.ALL, orphanRemoval = true)
        private List<Line> lines = new ArrayList<>();

        /**
         * Serialized but not persisted: visible to Jackson, unknown to the metamodel.
         */
        @Transient
        @Nullable
        private BigDecimal profit;
    }

    @Embeddable
    @Getter
    @Setter
    static class Dimensions {

        private Integer width;

        @JsonProperty("height_mm")
        private Integer heightMm;
    }

    @Entity
    @Table(name = "filter_suppliers")
    @Getter
    @Setter
    static class Supplier {

        @Id
        @GeneratedValue
        private Long id;

        private String name;

        private String country;
    }

    @Entity
    @Table(name = "filter_lines")
    @Getter
    @Setter
    static class Line {

        @Id
        @GeneratedValue
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        private Widget widget;

        @Column(precision = 12, scale = 2)
        private BigDecimal amount;

        private String status;

        @Transient
        @Nullable
        private BigDecimal subtotal;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(considerNestedRepositories = true)
    @Import(WidgetDal.class)
    static class TestApp {

        @SuppressWarnings("unused")
        interface WidgetRepository extends JpaDalRepository<Widget, Long> {
        }

        /**
         * Turkraft's {@code FilterConversionServiceConfiguration} requires a pre-existing
         * {@link ConversionService} bean (supplied as {@code mvcConversionService} in web apps); this bare
         * non-web context has none.
         */
        @Bean
        ConversionService defaultConversionService() {
            return new DefaultFormattingConversionService();
        }

        /**
         * {@code AbstractDal} requires a validator adapter and an {@link ObjectMapper}; this module has
         * neither a Bean Validation provider nor Boot's Jackson autoconfiguration on its classpath (nothing
         * is validated or merged here — the DAL is only read).
         */
        @Bean
        SpringValidatorAdapter validatorAdapter() {
            return new SpringValidatorAdapter(mock(Validator.class));
        }

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }
}
