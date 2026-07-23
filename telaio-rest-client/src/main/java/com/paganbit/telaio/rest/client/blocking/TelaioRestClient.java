package com.paganbit.telaio.rest.client.blocking;

import com.paganbit.telaio.rest.client.blocking.v1.DalClient;
import com.paganbit.telaio.rest.client.internal.DalErrorMapper;
import com.paganbit.telaio.rest.client.internal.DalFilterStringConverters;
import com.paganbit.telaio.rest.client.internal.DalPayloadCodec;
import com.paganbit.telaio.rest.client.internal.DalUriFactory;
import com.paganbit.telaio.rest.contract.v1.DalIdCodec;
import com.turkraft.springfilter.converter.FilterStringConverter;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;

/**
 * {@link TelaioClient} implementation backed by Spring's blocking {@link RestClient}, assembled
 * by composition: it layers the {@code /dal/v1} wire conventions (URI mapping, payload encoding,
 * error translation) on top of a caller-supplied {@link RestClient} and delegates every
 * transport concern to it. Configure the base URL (required) and anything else the connection
 * needs — authentication, SSL, timeouts, interceptors — on that client with Spring's own API
 * before passing it to {@link #create}. The given client is used exactly as configured and is
 * never altered, so its full capability set stays available and this class needs no
 * configuration surface of its own.
 * <p>
 * Instances are thread-safe. In a Spring Boot application, prefer the autoconfigured
 * {@code TelaioClientRegistry} over manual construction.
 * <pre>{@code
 * TelaioClient client = TelaioRestClient.create(RestClient.builder()
 *     .baseUrl("https://billing.example.com")
 *     .requestInterceptor(myAuthInterceptor)
 *     .build());
 * }</pre>
 * <p>
 * A missing base URL cannot be detected at creation time (a built {@link RestClient} exposes no
 * accessor for it) and surfaces on the first invocation with a descriptive exception. The base
 * URL must not embed credentials — authentication belongs to request interceptors.
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public final class TelaioRestClient implements TelaioClient {

    private final DalRestClient.Wiring wiring;

    private TelaioRestClient(
        RestClient restClient,
        ObjectMapper objectMapper,
        FilterStringConverter filterStringConverter
    ) {
        DalPayloadCodec payloadCodec = new DalPayloadCodec(objectMapper);
        this.wiring = new DalRestClient.Wiring(
            restClient,
            new DalUriFactory(),
            payloadCodec,
            new DalErrorMapper(payloadCodec.mapper()),
            new DalIdCodec(payloadCodec.mapper()),
            filterStringConverter
        );
    }

    /**
     * Creates a client on top of the given {@link RestClient}, which must be built with the
     * remote Telaio application's base URL. Wire (de)serialization uses a fresh default Jackson
     * mapper with the wire contract pinned on top, as described in
     * {@link #create(RestClient, ObjectMapper, FilterStringConverter)}.
     */
    public static TelaioRestClient create(RestClient restClient) {
        return create(restClient, JsonMapper.builder().build());
    }

    /**
     * Creates a client with the given Jackson mapper and the default filter converter, as
     * described in {@link #create(RestClient, ObjectMapper, FilterStringConverter)}.
     */
    public static TelaioRestClient create(RestClient restClient, ObjectMapper objectMapper) {
        return create(restClient, objectMapper, DalFilterStringConverters.pinned());
    }

    /**
     * Creates a client with a fresh default Jackson mapper and the given filter converter, as
     * described in {@link #create(RestClient, ObjectMapper, FilterStringConverter)}.
     */
    public static TelaioRestClient create(RestClient restClient, FilterStringConverter filterStringConverter) {
        return create(restClient, JsonMapper.builder().build(), filterStringConverter);
    }

    /**
     * Creates a client from its three collaborators.
     * <p>
     * The wire mapper is derived from the given Jackson mapper: its modules and configuration
     * are retained, but the {@code /dal/v1} wire contract is pinned on top regardless —
     * responses deserialize leniently (unknown members, absent creator properties and null
     * primitives are tolerated), and explicit {@code null} values in {@link java.util.Map}
     * payloads reach the wire even under a non-null inclusion configuration.
     * <p>
     * The given {@link FilterStringConverter} renders the filter trees passed to
     * {@code DalClient.read(FilterNode, ...)} into the {@code q} wire expression — typically
     * Turkraft's autoconfigured bean. The overloads without one fall back to the library's
     * converter over Spring's shared default conversion service.
     */
    public static TelaioRestClient create(
        RestClient restClient, ObjectMapper objectMapper, FilterStringConverter filterStringConverter) {
        Objects.requireNonNull(restClient, "restClient must not be null");
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        Objects.requireNonNull(filterStringConverter, "filterStringConverter must not be null");
        return new TelaioRestClient(restClient, objectMapper, filterStringConverter);
    }

    @Override
    public <E, I> DalClient<E, I> dal(String dalName, Class<E> entityType, Class<I> idType) {
        Objects.requireNonNull(dalName, "dalName must not be null");
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(idType, "idType must not be null");
        if (dalName.isBlank()) {
            throw new IllegalArgumentException("dalName must not be blank");
        }
        return new DalRestClient<>(wiring, dalName, entityType, idType);
    }
}
