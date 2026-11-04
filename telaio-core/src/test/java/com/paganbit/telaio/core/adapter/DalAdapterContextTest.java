package com.paganbit.telaio.core.adapter;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.channel.DalChannel;
import com.paganbit.telaio.core.registry.DalCatalog;
import com.paganbit.telaio.core.registry.DalDefinitionEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DalAdapterContextTest {

    @Test
    void of_shouldExposeTheDefinitionAndItsConvenienceAccessors() {
        DalDefinitionEntry definition = new DalDefinitionEntry("orders", OrdersDal.class);
        DalChannel channel = DalChannel.of("rest");
        DalCatalog catalog = mock(DalCatalog.class);

        DalAdapterContext context = DalAdapterContext.of(definition, channel, catalog);

        assertThat(context.definition()).isSameAs(definition);
        assertThat(context.channel()).isEqualTo(channel);
        assertThat(context.catalog()).isSameAs(catalog);
        assertThat(context.dalName()).isEqualTo("orders");
        assertThat(context.dalBeanClass()).isEqualTo(OrdersDal.class);
    }

    abstract static class OrdersDal implements Dal<Object, Long> {
    }
}
