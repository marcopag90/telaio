package com.paganbit.telaio.web.channel;

import com.paganbit.telaio.core.channel.DalChannel;

/**
 * The channels {@code telaio-web} declares.
 *
 * @author Marco Pagan
 * @since 3.0.0
 */
public final class DalChannels {

    /**
     * The REST channel: calls arriving through the {@code /dal/v1} endpoints.
     */
    public static final DalChannel REST = DalChannel.of("rest");

    private DalChannels() {
    }
}
