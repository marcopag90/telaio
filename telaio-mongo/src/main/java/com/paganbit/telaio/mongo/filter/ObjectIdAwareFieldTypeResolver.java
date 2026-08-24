package com.paganbit.telaio.mongo.filter;

import com.turkraft.springfilter.converter.StringCustomObjectIdConverter.CustomObjectId;
import com.turkraft.springfilter.helper.FieldTypeResolver;
import org.bson.types.ObjectId;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * {@link FieldTypeResolver} decorator that resolves every {@link ObjectId}-typed field to
 * Turkraft's {@link CustomObjectId} marker type, so filter values compared against such fields
 * convert to the extended-JSON shape ({@code {"$oid": <hex>}}) that parses into a BSON ObjectId.
 *
 * <p>A custom Jackson serializer cannot achieve this: filter values are embedded as POJO nodes and
 * rendered through the JSON tree's {@code toString()}, which uses Jackson's internal node mapper —
 * serializers registered on the application mapper never apply there. {@link CustomObjectId}
 * instead carries its shape via annotations, which the internal mapper honors; the
 * {@code String → CustomObjectId} conversion is covered by Spring's {@code ObjectToObjectConverter}
 * through the public single-argument constructor. This mirrors the mechanism Turkraft itself
 * applies to {@code String} {@code @Id} fields.</p>
 *
 * @author Marco Pagan
 * @since 1.2.0
 */
public final class ObjectIdAwareFieldTypeResolver implements FieldTypeResolver {

    private final FieldTypeResolver delegate;

    /**
     * Creates the decorator around the given resolver.
     *
     * @param delegate the resolver performing the actual field resolution
     */
    public ObjectIdAwareFieldTypeResolver(FieldTypeResolver delegate) {
        this.delegate = Objects.requireNonNull(delegate, "FieldTypeResolver delegate must not be null");
    }

    @Override
    public Class<?> resolve(Class<?> klass, String path) {
        Class<?> resolved = delegate.resolve(klass, path);
        return resolved == ObjectId.class ? CustomObjectId.class : resolved;
    }

    @Override
    public @Nullable Field getField(Class<?> klass, String path) {
        return delegate.getField(klass, path);
    }
}
