package com.paganbit.telaio.web.channel;

import com.paganbit.telaio.core.channel.DalChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DalChannelsTest {

    @Test
    void rest_isTheRestChannelId() {
        assertThat(DalChannels.REST).isEqualTo(DalChannel.of("rest"));
        assertThat(DalChannels.REST).isNotEqualTo(DalChannel.IN_PROCESS);
    }
}
