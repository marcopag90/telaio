package com.paganbit.telaio.mongo.jackson;

import com.paganbit.telaio.introspection.DefaultSimpleTypePredicate;
import com.paganbit.telaio.rest.contract.DalIdCodec;
import com.paganbit.telaio.rest.contract.DalIdCodecException;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ObjectIdJacksonModule}: hex round-trips on the mapper, id-codec symmetry
 * with the contributed simple-type classification, and rejection of invalid hex input.
 */
class ObjectIdJacksonModuleTest {

    private final JsonMapper mapper =
        JsonMapper.builder().addModule(new ObjectIdJacksonModule()).build();

    private final DalIdCodec codec =
        new DalIdCodec(mapper, new DefaultSimpleTypePredicate(Set.of(ObjectId.class)));

    @Test
    void serializesToHexString() {
        ObjectId id = new ObjectId();

        assertThat(mapper.writeValueAsString(id)).isEqualTo("\"" + id.toHexString() + "\"");
    }

    @Test
    void deserializesFromHexString() {
        ObjectId id = new ObjectId();

        assertThat(mapper.readValue("\"" + id.toHexString() + "\"", ObjectId.class)).isEqualTo(id);
    }

    @Test
    void rejectsInvalidHexRepresentation() {
        assertThatThrownBy(() -> mapper.readValue("\"not-an-object-id\"", ObjectId.class))
            .hasMessageContaining("ObjectId");
    }

    @Test
    void rejectsNonTextualToken() {
        assertThatThrownBy(() -> mapper.readValue("{}", ObjectId.class))
            .hasMessageContaining("ObjectId");
    }

    @Test
    void idCodec_objectIdTravelsRawHexAndRoundTrips() {
        ObjectId id = new ObjectId();

        String encoded = codec.encode(id, ObjectId.class);

        assertThat(encoded).isEqualTo(id.toHexString());
        assertThat(codec.decode(encoded, ObjectId.class)).isEqualTo(id);
    }

    @Test
    void idCodec_invalidHexSegmentFailsWithCodecException() {
        assertThatThrownBy(() -> codec.decode("not-an-object-id", ObjectId.class))
            .isInstanceOf(DalIdCodecException.class);
    }

    @Test
    void idCodec_withoutContribution_objectIdDoesNotTravelRaw() {
        // Without the contributed classification the id is treated as complex (Base64 JSON):
        // both peers must share the contribution for the wire representation to line up.
        DalIdCodec plainCodec = new DalIdCodec(mapper, new DefaultSimpleTypePredicate());
        ObjectId id = new ObjectId();

        assertThat(plainCodec.encode(id, ObjectId.class)).isNotEqualTo(id.toHexString());
    }
}
