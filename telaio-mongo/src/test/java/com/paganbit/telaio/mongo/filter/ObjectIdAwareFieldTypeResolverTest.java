package com.paganbit.telaio.mongo.filter;

import com.turkraft.springfilter.converter.StringCustomObjectIdConverter.CustomObjectId;
import com.turkraft.springfilter.helper.FieldTypeResolver;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link ObjectIdAwareFieldTypeResolver}: {@code ObjectId}-typed fields resolve to
 * Turkraft's {@code CustomObjectId} marker, everything else passes through untouched — including the
 * reference-field methods that carry interface defaults, which must reach the delegate.
 */
class ObjectIdAwareFieldTypeResolverTest {

    private final FieldTypeResolver delegate = mock(FieldTypeResolver.class);

    private final ObjectIdAwareFieldTypeResolver resolver = new ObjectIdAwareFieldTypeResolver(delegate);

    @Test
    void resolve_mapsObjectIdToCustomObjectId() {
        doReturn(ObjectId.class).when(delegate).resolve(Widget.class, "id");

        assertThat(resolver.resolve(Widget.class, "id")).isEqualTo(CustomObjectId.class);
    }

    @Test
    void resolve_passesOtherTypesThrough() {
        doReturn(String.class).when(delegate).resolve(Widget.class, "name");

        assertThat(resolver.resolve(Widget.class, "name")).isEqualTo(String.class);
    }

    @Test
    void getField_delegates() throws NoSuchFieldException {
        Field field = Widget.class.getDeclaredField("name");
        doReturn(field).when(delegate).getField(Widget.class, "name");

        assertThat(resolver.getField(Widget.class, "name")).isSameAs(field);
    }

    @Test
    void isCollectionDbRefField_delegatesInsteadOfUsingTheInterfaceDefault() {
        doReturn(true).when(delegate).isCollectionDbRefField(Widget.class, "roles.$id");

        assertThat(resolver.isCollectionDbRefField(Widget.class, "roles.$id")).isTrue();
    }

    @Test
    void isReferenceDollarField_delegatesInsteadOfUsingTheInterfaceDefault() {
        doReturn(true).when(delegate).isReferenceDollarField(Widget.class, "manager.$id");

        assertThat(resolver.isReferenceDollarField(Widget.class, "manager.$id")).isTrue();
    }

    @Test
    void storedFieldPath_delegatesInsteadOfUsingTheInterfaceDefault() {
        doReturn("advisor").when(delegate).storedFieldPath(Widget.class, "advisor.$id");

        assertThat(resolver.storedFieldPath(Widget.class, "advisor.$id")).isEqualTo("advisor");
    }

    @Test
    void hasDollarSegment_delegatesInsteadOfUsingTheInterfaceDefault() {
        doReturn(true).when(delegate).hasDollarSegment("plain.path");

        assertThat(resolver.hasDollarSegment("plain.path")).isTrue();
    }

    @SuppressWarnings("unused")
    private static final class Widget {

        private ObjectId id;

        private String name;
    }
}
