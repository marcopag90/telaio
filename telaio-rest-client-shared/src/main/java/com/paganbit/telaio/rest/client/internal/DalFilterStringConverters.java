package com.paganbit.telaio.rest.client.internal;

import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.converter.FilterStringConverterImpl;
import org.springframework.core.convert.support.DefaultConversionService;

/**
 * Factory of the client's fallback {@link FilterStringConverter}, used whenever the host does
 * not provide one (plain composition, or a context without Turkraft's autoconfiguration): the
 * library's own implementation over Spring's shared default conversion service, so the rendered
 * expression never depends on host converters.
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public final class DalFilterStringConverters {

    private DalFilterStringConverters() {
    }

    /**
     * Returns a new fallback converter. The client only converts node to string, so the embedded
     * parser deliberately rejects the string-to-node direction.
     */
    public static FilterStringConverter pinned() {
        return new FilterStringConverterImpl(
            (input, context) -> {
                throw new UnsupportedOperationException(
                    "The Telaio client renders filters node-to-string only");
            },
            DefaultConversionService.getSharedInstance()
        );
    }
}
