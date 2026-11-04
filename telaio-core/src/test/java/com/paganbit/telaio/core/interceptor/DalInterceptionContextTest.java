package com.paganbit.telaio.core.interceptor;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.registry.DalDefinitionEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DalInterceptionContextTest {

    @Test
    void of_shouldExposeTheDefinitionAndItsConvenienceAccessors() {
        DalDefinitionEntry definition = new DalDefinitionEntry("orders", OrdersDal.class, true);

        DalInterceptionContext context = DalInterceptionContext.of(definition);

        assertThat(context.definition()).isSameAs(definition);
        assertThat(context.dalName()).isEqualTo("orders");
        assertThat(context.dalBeanClass()).isEqualTo(OrdersDal.class);
    }

    abstract static class OrdersDal implements Dal<Object, Long> {
    }
}
