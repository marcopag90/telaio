package com.paganbit.telaio.mongo.jackson;

import org.bson.types.ObjectId;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Jackson 3 module mapping {@link ObjectId} to its 24-character hexadecimal string form.
 *
 * @author Marco Pagan
 * @since 2.0.0
 */
public class ObjectIdJacksonModule extends SimpleModule {

    /**
     * Creates the module with the {@link ObjectId} serializer and deserializer registered.
     */
    public ObjectIdJacksonModule() {
        super(ObjectIdJacksonModule.class.getName());
        addSerializer(ObjectId.class, new ObjectIdSerializer());
        addDeserializer(ObjectId.class, new ObjectIdDeserializer());
    }

    private static final class ObjectIdSerializer extends StdSerializer<ObjectId> {

        private ObjectIdSerializer() {
            super(ObjectId.class);
        }

        @Override
        public void serialize(ObjectId value, JsonGenerator gen, SerializationContext ctx) throws JacksonException {
            gen.writeString(value.toHexString());
        }
    }

    private static final class ObjectIdDeserializer extends StdDeserializer<ObjectId> {

        private ObjectIdDeserializer() {
            super(ObjectId.class);
        }

        @Override
        public ObjectId deserialize(JsonParser p, DeserializationContext ctx) throws JacksonException {
            final var hex = p.getString();
            if (hex == null || !ObjectId.isValid(hex)) {
                return ctx.reportInputMismatch(ObjectId.class,
                    "Not a valid ObjectId hexadecimal representation");
            }
            return new ObjectId(hex);
        }
    }
}
