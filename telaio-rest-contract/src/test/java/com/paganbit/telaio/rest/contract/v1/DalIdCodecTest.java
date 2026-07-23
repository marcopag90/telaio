package com.paganbit.telaio.rest.contract.v1;

import com.paganbit.telaio.rest.contract.DalIdCodecException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DalIdCodecTest {

    record CompositeId(String language, String code) {
    }

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final DalIdCodec codec = new DalIdCodec(objectMapper);

    @Test
    void simpleIdsTravelRaw() {
        assertThat(codec.encode(42L, Long.class)).isEqualTo("42");
        assertThat(codec.encode("abc", String.class)).isEqualTo("abc");

        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        assertThat(codec.encode(uuid, UUID.class)).isEqualTo(uuid.toString());
    }

    @Test
    void simpleIdsRoundTrip() {
        assertThat(codec.decode(codec.encode(42L, Long.class), Long.class)).isEqualTo(42L);
        assertThat(codec.decode(codec.encode("abc", String.class), String.class)).isEqualTo("abc");

        UUID uuid = UUID.randomUUID();
        assertThat(codec.decode(codec.encode(uuid, UUID.class), UUID.class)).isEqualTo(uuid);
    }

    @Test
    void temporalIdsRoundTrip() {
        LocalDate date = LocalDate.of(2026, Month.JULY, 13);
        assertThat(codec.decode(codec.encode(date, LocalDate.class), LocalDate.class)).isEqualTo(date);
    }

    @Test
    void compositeIdsRoundTrip() {
        CompositeId id = new CompositeId("it", "GREETING");
        assertThat(codec.decode(codec.encode(id, CompositeId.class), CompositeId.class)).isEqualTo(id);
    }

    @Test
    void compositeIdsAreUrlSafeUnpaddedBase64OfJson() {
        CompositeId id = new CompositeId("it", "GREETING");
        String encoded = codec.encode(id, CompositeId.class);

        assertThat(encoded).doesNotContain("=", "+", "/");
        // Must be decodable with the exact decoder the server uses.
        String json = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        assertThat(objectMapper.readValue(json, CompositeId.class)).isEqualTo(id);
    }

    @Test
    void malformedBase64FailsWithCodecException() {
        assertThatThrownBy(() -> codec.decode("not-valid-base64!!!", CompositeId.class))
            .isInstanceOf(DalIdCodecException.class)
            .hasMessageContaining("Base64");
    }

    @Test
    void unserializableSegmentFailsWithCodecException() {
        assertThatThrownBy(() -> codec.decode("abc", Long.class))
            .isInstanceOf(DalIdCodecException.class);
    }

    @Test
    void unserializableIdFailsWithCodecException() {
        // A complex id whose accessor throws while Jackson serializes it: the value never
        // reaches the message (it may be sensitive), only the declared type does.
        ExplodingId id = new ExplodingId("x");
        assertThatThrownBy(() -> codec.encode(id, ExplodingId.class))
            .isInstanceOf(DalIdCodecException.class)
            .hasMessageContaining("encode")
            .hasMessageContaining(ExplodingId.class.getName());
    }

    @Test
    void validBase64WithWrongJsonShapeFailsWithCodecException() {
        // Well-formed Base64, but the decoded JSON does not map onto the composite type.
        String encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("[1,2,3]".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decode(encoded, CompositeId.class))
            .isInstanceOf(DalIdCodecException.class)
            .hasMessageContaining("composite");
    }

    /** A complex (record) id whose accessor throws while Jackson serializes it. */
    record ExplodingId(String code) {
        @Override
        public String code() {
            throw new IllegalStateException("cannot read id component");
        }
    }
}
