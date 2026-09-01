package com.paganbit.telaio.mongo.sort;

import com.paganbit.telaio.core.exception.DalInvalidSortException;
import org.jspecify.annotations.Nullable;
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
 * Unit tests for {@link MongoSortPropertyValidator}: which sort property paths a real (empty) mapping
 * context accepts as persistent, where the walk stops, and which segments are rejected — the same walk
 * as the filter-side validator.
 */
class MongoSortPropertyValidatorTest {

    private final MongoSortPropertyValidator validator = new MongoSortPropertyValidator(new MongoMappingContext());

    @ParameterizedTest
    @ValueSource(strings = {
        "id", "name", "address.city", "lines.amount", "tags",
        "attributes", "attributes.color", "attributes.color.shade",
        "payload", "payload.kind", "payload.nested.level", "events.type",
        "owner", "owner.$id", "owner.$ref", "owner.$db", "parent", "parent.$id"
    })
    void acceptsPersistentPaths(String path) {
        assertThatCode(() -> validator.validate(path, Doc.class)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "nope", "address.nope", "label", "lines.subtotal",   // unknown or not stored
        "name.length", "tags.size",                           // a segment on a simple property
        "owner.name", "owner.$id.x", "owner.$x",              // a reference is never dereferenced
        "attributes.$id", "payload.$x"                        // a dynamic key must not look like an operator
    })
    void rejectsNonPersistentPaths(String path) {
        assertThatThrownBy(() -> validator.validate(path, Doc.class))
            .isInstanceOf(DalInvalidSortException.class);
    }

    @SuppressWarnings("unused")
    static class Doc {

        @Id
        private String id;

        @Nullable
        private String name;

        @Nullable
        private Address address;

        @Nullable
        private List<Line> lines;

        @Nullable
        private List<String> tags;

        @Nullable
        private Map<String, String> attributes;

        @Nullable
        private Object payload;

        @Nullable
        private List<Map<String, Object>> events;

        @DBRef
        @Nullable
        private Owner owner;

        @DocumentReference
        @Nullable
        private Doc parent;

        @Nullable
        public String getLabel() {
            return name;
        }
    }

    @SuppressWarnings("unused")
    static class Address {

        @Nullable
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

        @Nullable
        private String name;
    }
}
