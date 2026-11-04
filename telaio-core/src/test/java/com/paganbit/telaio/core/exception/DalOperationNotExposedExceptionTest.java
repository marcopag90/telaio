package com.paganbit.telaio.core.exception;

import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.channel.DalChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DalOperationNotExposedExceptionTest {

    @Test
    void shouldCarryDalOperationAndChannel() {
        DalChannel channel = DalChannel.of("rest");

        DalOperationNotExposedException exception =
            new DalOperationNotExposedException("articles", DalOperationType.DELETE, channel);

        assertThat(exception.dalName()).isEqualTo("articles");
        assertThat(exception.operation()).isEqualTo(DalOperationType.DELETE);
        assertThat(exception.channel()).isEqualTo(channel);
        assertThat(exception).hasMessage("Operation DELETE of DAL 'articles' is not exposed on channel 'rest'");
    }
}
