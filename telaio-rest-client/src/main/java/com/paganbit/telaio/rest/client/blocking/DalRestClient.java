package com.paganbit.telaio.rest.client.blocking;

import com.paganbit.telaio.rest.client.DalPage;
import com.paganbit.telaio.rest.client.DalPageRequest;
import com.paganbit.telaio.rest.client.blocking.v1.DalClient;
import com.paganbit.telaio.rest.client.exception.DalClientEncodingException;
import com.paganbit.telaio.rest.client.exception.DalClientMalformedResponseException;
import com.paganbit.telaio.rest.client.exception.DalClientNotFoundException;
import com.paganbit.telaio.rest.client.exception.DalClientTransportException;
import com.paganbit.telaio.rest.client.internal.DalErrorMapper;
import com.paganbit.telaio.rest.client.internal.DalPayloadCodec;
import com.paganbit.telaio.rest.client.internal.DalUriFactory;
import com.paganbit.telaio.rest.client.internal.DalUriFactory.DalRequestUri;
import com.paganbit.telaio.rest.contract.DalIdCodecException;
import com.paganbit.telaio.rest.contract.v1.DalIdCodec;
import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@link DalClient} implementation of {@link TelaioRestClient}: one immutable, thread-safe
 * handle per {@code (dalName, entityType, idType)} triple.
 *
 * <p>Requests run through {@code RestClient.exchange(...)}: status handling and body parsing are
 * performed here with the client's own pinned mapper — the supplied {@code RestClient} is never
 * mutated and the wire behavior never depends on the host application's converters.</p>
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
final class DalRestClient<E, I> implements DalClient<E, I> {

    private static final String ID_MUST_NOT_BE_NULL = "id must not be null";

    private final RestClient restClient;
    private final DalUriFactory uriFactory;
    private final DalPayloadCodec payloadCodec;
    private final DalErrorMapper errorMapper;
    private final DalIdCodec idCodec;
    private final FilterStringConverter filterStringConverter;
    private final String dalName;
    private final Class<E> entityType;
    private final Class<I> idType;

    /**
     * The transport, codecs and filter converter of one {@link TelaioRestClient}, built once and
     * shared as-is by every DAL handle it creates.
     */
    record Wiring(
        RestClient restClient,
        DalUriFactory uriFactory,
        DalPayloadCodec payloadCodec,
        DalErrorMapper errorMapper,
        DalIdCodec idCodec,
        FilterStringConverter filterStringConverter
    ) {
    }

    DalRestClient(Wiring wiring, String dalName, Class<E> entityType, Class<I> idType) {
        this.restClient = wiring.restClient();
        this.uriFactory = wiring.uriFactory();
        this.payloadCodec = wiring.payloadCodec();
        this.errorMapper = wiring.errorMapper();
        this.idCodec = wiring.idCodec();
        this.filterStringConverter = wiring.filterStringConverter();
        this.dalName = dalName;
        this.entityType = entityType;
        this.idType = idType;
    }

    @Override
    public E create(Object input) {
        Objects.requireNonNull(input, "input must not be null");
        DalRequestUri uri = uriFactory.collection(dalName);
        return exchangeForBody(() -> restClient.post()
            .uri(uri.template(), uri.variables())
            .contentType(MediaType.APPLICATION_JSON)
            .body(payloadCodec.toWireJson(input)))
            .map(node -> payloadCodec.toEntity(node, entityType))
            .orElseThrow(() -> new DalClientMalformedResponseException(
                "The server returned no entity body for the created resource"));
    }

    @Override
    public DalPage<E> read(@Nullable String filter, DalPageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        DalRequestUri uri = uriFactory.collection(dalName, filter, pageRequest);
        JsonNode node = exchangeForBody(() -> restClient.get()
            .uri(uri.template(), uri.variables()))
            .orElse(null);
        return payloadCodec.toPage(node, entityType);
    }

    @Override
    public DalPage<E> read(@Nullable FilterNode filter, DalPageRequest pageRequest) {
        return read(filter == null ? null : filterStringConverter.convert(filter), pageRequest);
    }

    @Override
    public Optional<E> readOne(I id) {
        Objects.requireNonNull(id, ID_MUST_NOT_BE_NULL);
        try {
            DalRequestUri uri = entityUri(id);
            return exchangeForBody(() -> restClient.get()
                .uri(uri.template(), uri.variables()))
                .map(node -> payloadCodec.toEntity(node, entityType));
        } catch (DalClientNotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<E> update(I id, Object patch) {
        Objects.requireNonNull(id, ID_MUST_NOT_BE_NULL);
        Objects.requireNonNull(patch, "patch must not be null");
        DalRequestUri uri = entityUri(id);
        return exchangeForBody(() -> restClient.patch()
            .uri(uri.template(), uri.variables())
            .contentType(MediaType.APPLICATION_JSON)
            .body(payloadCodec.toWireJson(patch)))
            .map(node -> payloadCodec.toEntity(node, entityType));
    }

    @Override
    public void delete(I id) {
        Objects.requireNonNull(id, ID_MUST_NOT_BE_NULL);
        DalRequestUri uri = entityUri(id);
        this.<@Nullable Void>exchange(() -> restClient.delete()
                .uri(uri.template(), uri.variables()),
            body -> null);
    }

    private DalRequestUri entityUri(I id) {
        return uriFactory.entity(dalName, encodeId(id));
    }

    private String encodeId(I id) {
        try {
            return idCodec.encode(id, idType);
        } catch (DalIdCodecException e) {
            // Neutral message: the id value itself may be sensitive and belongs to the cause chain.
            throw new DalClientEncodingException(
                "Failed to encode the identifier for DAL '%s'".formatted(dalName), e);
        }
    }

    /**
     * Executes the request and reads the response body with the pinned mapper: error statuses
     * are mapped onto the client exception hierarchy, a bodiless success maps to empty, a
     * non-JSON success body raises {@link DalClientMalformedResponseException}.
     */
    private Optional<JsonNode> exchangeForBody(Supplier<RestClient.RequestHeadersSpec<?>> request) {
        return exchange(request, body -> body.length == 0
            ? Optional.empty()
            : Optional.of(readTree(body)));
    }

    private JsonNode readTree(byte[] body) {
        try {
            return payloadCodec.mapper().readTree(body);
        } catch (RuntimeException e) {
            // E.g. a proxy/gateway answering 2xx with an HTML page in the server's place.
            throw new DalClientMalformedResponseException("The response body is not valid JSON", e);
        }
    }

    private <T extends @Nullable Object> T exchange(
        Supplier<RestClient.RequestHeadersSpec<?>> request,
        Function<byte[], T> onSuccess
    ) {
        try {
            RestClient.RequestHeadersSpec<?> spec = request.get();
            // retrieve() used to send this through the converters; restore the wire behavior.
            spec.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON);
            return spec.exchange((clientRequest, clientResponse) -> {
                byte[] body = clientResponse.getBody().readAllBytes();
                if (clientResponse.getStatusCode().isError()) {
                    throw errorMapper.map(
                        clientResponse.getStatusCode(), clientResponse.getHeaders(), body);
                }
                return onSuccess.apply(body);
            });
        } catch (ResourceAccessException e) {
            if (indicatesMissingBaseUrl(e)) {
                // Some request factories (Apache HttpComponents) only reject a relative URI at
                // execution time, surfacing it as an I/O failure.
                throw missingBaseUrl(e);
            }
            throw new DalClientTransportException(
                "I/O failure calling DAL '%s': %s".formatted(dalName, e.getMessage()), e);
        } catch (IllegalArgumentException e) {
            if (indicatesMissingBaseUrl(e)) {
                // The relative request template could not be resolved: the supplied RestClient
                // carries no base URL (undetectable at create() time — Spring exposes no getter).
                throw missingBaseUrl(e);
            }
            throw e;
        }
    }

    private static boolean indicatesMissingBaseUrl(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String message = current.getMessage() == null
                ? ""
                : current.getMessage().toLowerCase(Locale.ROOT);
            // JDK client: "URI with undefined scheme"; SimpleClientHttpRequestFactory:
            // "URI is not absolute"; Apache HttpComponents: "does not specify a valid host name".
            if (message.contains("absolute") || message.contains("scheme")
                || message.contains("valid host name")) {
                return true;
            }
        }
        return false;
    }

    private static IllegalStateException missingBaseUrl(Throwable cause) {
        return new IllegalStateException(
            "The RestClient passed to TelaioRestClient.create(...) must be built with a "
                + "base URL (RestClient.builder().baseUrl(...))", cause);
    }
}
