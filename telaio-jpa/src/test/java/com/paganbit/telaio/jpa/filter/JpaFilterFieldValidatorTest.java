package com.paganbit.telaio.jpa.filter;

import com.paganbit.telaio.core.exception.DalInvalidFilterException;
import com.turkraft.springfilter.parser.node.FieldNode;
import jakarta.persistence.metamodel.*;
import org.hibernate.metamodel.model.domain.ManagedDomainType;
import org.hibernate.metamodel.model.domain.PersistentAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JpaFilterFieldValidator} against a stubbed metamodel: which paths resolve to
 * persistent attributes, where the walk stops ({@code Map} accessors), and which segments are rejected.
 */
class JpaFilterFieldValidatorTest {

    private EntityType<?> widget;

    @BeforeEach
    void setUp() {
        Type<?> basic = mock(Type.class);
        EmbeddableType<?> dims = embeddable(basic);
        EntityType<?> line = entity(basic);

        widget = mock(EntityType.class);
        doThrow(new IllegalArgumentException("Unable to locate Attribute")).when(widget).getAttribute(anyString());
        doReturn(singular(basic)).when(widget).getAttribute("name");
        doReturn(singular(dims)).when(widget).getAttribute("dims");
        doReturn(plural(basic)).when(widget).getAttribute("tags");
        doReturn(plural(line)).when(widget).getAttribute("lines");
        doReturn(map(basic, dims)).when(widget).getAttribute("attributes");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "name", "dims", "dims.width", "tags", "lines", "lines.amount",
        "attributes", "attributes.key", "attributes.keys", "attributes.value", "attributes.values",
        "attributes.value.width"
    })
    void acceptsPersistentPaths(String path) {
        assertThatCode(() -> JpaFilterFieldValidator.validate(new FieldNode(path), widget)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "profit", "dims.nope", "lines.subtotal",        // unknown or not mapped
        "name.length", "tags.size", "lines.amount.abs", // a segment on a basic attribute
        "attributes.color", "attributes.key.nope"       // a map is addressed through its accessors only
    })
    void rejectsNonPersistentPaths(String path) {
        FieldNode field = new FieldNode(path);

        assertThatThrownBy(() -> JpaFilterFieldValidator.validate(field, widget))
            .isInstanceOf(DalInvalidFilterException.class);
    }

    @Test
    void acceptsAttributesDeclaredOnSubtypesOfAPolymorphicRoot() {
        // Hibernate resolves `root.get("special")` on a polymorphic root by looking into its subtypes.
        ManagedDomainType<?> polymorphicRoot = mock(ManagedDomainType.class);
        doThrow(new IllegalArgumentException("Unable to locate Attribute"))
            .when(polymorphicRoot).getAttribute(anyString());
        PersistentAttribute<?, ?> special = mock(PersistentAttribute.class);
        doReturn(special).when(polymorphicRoot).findSubTypesAttribute("special");
        FieldNode specialField = new FieldNode("special");
        FieldNode otherField = new FieldNode("other");

        assertThatCode(() -> JpaFilterFieldValidator.validate(specialField, polymorphicRoot))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> JpaFilterFieldValidator.validate(otherField, polymorphicRoot))
            .isInstanceOf(DalInvalidFilterException.class);
    }

    private static SingularAttribute<?, ?> singular(Type<?> type) {
        SingularAttribute<?, ?> attribute = mock(SingularAttribute.class);
        doReturn(type).when(attribute).getType();
        return attribute;
    }

    private static ListAttribute<?, ?> plural(Type<?> elementType) {
        ListAttribute<?, ?> attribute = mock(ListAttribute.class);
        doReturn(elementType).when(attribute).getElementType();
        return attribute;
    }

    private static MapAttribute<?, ?, ?> map(Type<?> keyType, Type<?> valueType) {
        MapAttribute<?, ?, ?> attribute = mock(MapAttribute.class);
        doReturn(keyType).when(attribute).getKeyType();
        doReturn(valueType).when(attribute).getElementType();
        return attribute;
    }

    private static EmbeddableType<?> embeddable(Type<?> basic) {
        EmbeddableType<?> type = mock(EmbeddableType.class);
        doThrow(new IllegalArgumentException("Unable to locate Attribute")).when(type).getAttribute(anyString());
        doReturn(singular(basic)).when(type).getAttribute("width");
        return type;
    }

    private static EntityType<?> entity(Type<?> basic) {
        EntityType<?> type = mock(EntityType.class);
        doThrow(new IllegalArgumentException("Unable to locate Attribute")).when(type).getAttribute(anyString());
        doReturn(singular(basic)).when(type).getAttribute("amount");
        return type;
    }
}
