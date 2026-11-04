package com.paganbit.telaio.core.channel;

import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;

/**
 * Identifies an invocation channel: the kind of caller a DAL operation reaches the DAL through.
 *
 * <p>A channel is an open value type keyed by an id, not an enumeration: core knows only
 * {@link #IN_PROCESS}, the label for direct {@link com.paganbit.telaio.core.Dal} calls where no adapter
 * chain runs, and every channel module declares its own id. A third-party channel plugs in by declaring
 * a new id and a {@link DalChannelDefinition}, without touching core.</p>
 *
 * <p>Two channels are equal when their ids are equal. Ids are case-sensitive and must not be blank;
 * declare them once, as a public constant, in the module that owns the channel.</p>
 *
 * @author Marco Pagan
 * @since 3.0.0
 */
public final class DalChannel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The channel of direct, in-process calls to a {@link com.paganbit.telaio.core.Dal} bean: no
     * operation adapter, no exposure rule.
     */
    public static final DalChannel IN_PROCESS = new DalChannel("in-process");

    private final String id;

    private DalChannel(String id) {
        this.id = id;
    }

    /**
     * Returns the channel with the given id.
     *
     * @param id the channel id; must not be blank
     * @return the channel
     * @throws IllegalArgumentException if the id is blank
     */
    public static DalChannel of(String id) {
        if (id.isBlank()) {
            throw new IllegalArgumentException("A channel id must not be blank");
        }
        return new DalChannel(id);
    }

    /**
     * Returns the id of this channel.
     *
     * @return the channel id
     */
    public String id() {
        return id;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof DalChannel that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
