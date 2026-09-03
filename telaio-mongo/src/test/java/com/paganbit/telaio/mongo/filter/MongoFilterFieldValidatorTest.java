package com.paganbit.telaio.mongo.filter;

import com.paganbit.telaio.core.exception.DalInvalidFilterException;
import com.turkraft.springfilter.parser.node.FieldNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MongoFilterFieldValidator}: which field paths a real (empty) mapping context
 * accepts as persistent, where the walk stops, and which segments are rejected.
 */
class MongoFilterFieldValidatorTest {

    private final MongoFilterFieldValidator validator = new MongoFilterFieldValidator(new MongoMappingContext());

    @ParameterizedTest
    @ValueSource(strings = {
        "id", "name", "address.city", "lines.amount", "tags",
        "attributes", "attributes.color", "attributes.color.shade",
        "payload", "payload.kind", "payload.nested.level", "events.type",
        "owner", "owner.$id", "owner.$ref", "owner.$db", "parent", "parent.$id"
    })
    void acceptsPersistentPaths(String path) {
        assertThatCode(() -> validator.validate(new FieldNode(path), Doc.class)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "nope", "address.nope", "label", "lines.subtotal",   // unknown or not stored
        "name.length", "tags.size",                           // a segment on a simple property
        "owner.name", "owner.$id.x", "owner.$x",              // a reference is never dereferenced
        "attributes.$id", "payload.$x"                        // a dynamic key must not look like an operator
    })
    void rejectsNonPersistentPaths(String path) {
        FieldNode field = new FieldNode(path);

        assertThatThrownBy(() -> validator.validate(field, Doc.class))
            .isInstanceOf(DalInvalidFilterException.class);
    }

    @SuppressWarnings("unused")
    static class Doc {

        @Id
        private String id;

        private String name;

        private Address address;

        private List<Line> lines;

        private List<String> tags;

        private Map<String, String> attributes;

        private Object payload;

        private List<Map<String, Object>> events;

        @DBRef
        private Owner owner;

        @DocumentReference
        private Doc parent;

        public String getLabel() {
            return name;
        }
    }

    @SuppressWarnings("unused")
    static class Address {

        private String city;
    }

    @SuppressWarnings("unused")
    static class Line {

        private double amount;

        public double getSubtotal() {
            return amount;
        }
    }

    @SuppressWarnings("unused")
    static class Owner {

        @Id
        private String id;

        private String name;
    }
}
