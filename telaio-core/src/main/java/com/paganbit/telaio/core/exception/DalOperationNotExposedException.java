package com.paganbit.telaio.core.exception;

import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.channel.DalChannel;

/**
 * Raised when a call reaches a DAL operation that is not exposed on the channel it arrived through.
 *
 * <p>It is a <em>structural</em> rejection, distinct from an authorization denial: the operation is not
 * published on that channel at all, rather than being forbidden to a particular principal. Core raises it
 * from the exposure interceptor of every adapter chain; each channel module translates it into its own
 * failure vocabulary.</p>
 *
 * @author Marco Pagan
 * @since 3.0.0
 */
public class DalOperationNotExposedException extends RuntimeException {

    private final String dalName;
    private final DalOperationType operation;
    private final transient DalChannel channel;

    public DalOperationNotExposedException(String dalName, DalOperationType operation, DalChannel channel) {
        super("Operation %s of DAL '%s' is not exposed on channel '%s'".formatted(operation, dalName, channel));
        this.dalName = dalName;
        this.operation = operation;
        this.channel = channel;
    }

    /**
     * The registration name of the DAL.
     */
    public String dalName() {
        return dalName;
    }

    /**
     * The operation that was attempted.
     */
    public DalOperationType operation() {
        return operation;
    }

    /**
     * The channel the call arrived through.
     */
    public DalChannel channel() {
        return channel;
    }
}
