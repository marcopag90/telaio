package com.paganbit.telaio.core.channel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DalChannelTest {

    @Test
    void of_shouldKeepTheId() {
        DalChannel channel = DalChannel.of("rest");

        assertThat(channel.id()).isEqualTo("rest");
        assertThat(channel).hasToString("rest");
    }

    @Test
    void of_shouldRejectBlankId() {
        assertThatThrownBy(() -> DalChannel.of("  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void channelsWithTheSameId_shouldBeEqual() {
        DalChannel first = DalChannel.of("agent");
        DalChannel second = DalChannel.of("agent");

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    void channelsWithDifferentIds_shouldNotBeEqual() {
        assertThat(DalChannel.of("rest")).isNotEqualTo(DalChannel.of("Rest"));
        assertThat(DalChannel.of("rest")).isNotEqualTo(DalChannel.IN_PROCESS);
    }

    @Test
    void inProcess_shouldBeTheInProcessId() {
        assertThat(DalChannel.IN_PROCESS.id()).isEqualTo("in-process");
        assertThat(DalChannel.IN_PROCESS).isEqualTo(DalChannel.of("in-process"));
    }
}
