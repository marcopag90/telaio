package com.paganbit.telaio.jpa.sort;

import com.paganbit.telaio.core.exception.DalInvalidSortException;
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
 * Unit tests for {@link JpaSortPropertyValidator} against a stubbed metamodel: which paths resolve to
 * orderable attributes, and where the sort walk is deliberately stricter than the filter one (no Map
 * accessors, no terminal plural attribute, no subtype fallback).
 */
class JpaSortPropertyValidatorTest {

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
        "name", "dims", "dims.width", "lines.amount"
    })
    void acceptsOrderablePaths(String path) {
        assertThatCode(() -> JpaSortPropertyValidator.validate(path, widget)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "profit", "dims.nope", "lines.subtotal",        // unknown or not mapped
        "name.length", "lines.amount.abs"               // a segment on a basic attribute
    })
    void rejectsNonPersistentPaths(String path) {
        assertThatThrownBy(() -> JpaSortPropertyValidator.validate(path, widget))
            .isInstanceOf(DalInvalidSortException.class)
            .hasMessageContaining("Unknown sort property");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "tags", "lines",                                // nothing to order a collection itself by
        "attributes", "attributes.value.width"          // PropertyPath has no Map vocabulary at all
    })
    void rejectsPathsSpringDataCannotOrderBy(String path) {
        assertThatThrownBy(() -> JpaSortPropertyValidator.validate(path, widget))
            .isInstanceOf(DalInvalidSortException.class)
            .hasMessageContaining("is not sortable");
    }

    @Test
    void rejectsAnAttributeThatIsNeitherSingularNorPlural() {
        // Fail closed: an attribute kind the walk does not know cannot be ordered by.
        doReturn(mock(Attribute.class)).when(widget).getAttribute("weird");

        assertThatThrownBy(() -> JpaSortPropertyValidator.validate("weird", widget))
            .isInstanceOf(DalInvalidSortException.class)
            .hasMessageContaining("is not sortable");
    }

    @Test
    void rejectsAttributesDeclaredOnSubtypesOfAPolymorphicRoot() {
        // Deliberate divergence from the filter walk: Spring Data's PropertyPath resolves sorted paths
        // against the root Java type, so a subtype attribute would still fail while the query is built.
        ManagedDomainType<?> polymorphicRoot = mock(ManagedDomainType.class);
        doThrow(new IllegalArgumentException("Unable to locate Attribute"))
            .when(polymorphicRoot).getAttribute(anyString());
        PersistentAttribute<?, ?> special = mock(PersistentAttribute.class);
        doReturn(special).when(polymorphicRoot).findSubTypesAttribute("special");

        assertThatThrownBy(() -> JpaSortPropertyValidator.validate("special", polymorphicRoot))
            .isInstanceOf(DalInvalidSortException.class)
            .hasMessageContaining("Unknown sort property");
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
        lenient().doReturn(keyType).when(attribute).getKeyType();
        lenient().doReturn(valueType).when(attribute).getElementType();
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
