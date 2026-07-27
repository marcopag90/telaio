package com.paganbit.telaio.rest.contract;

/**
 * Raised by a DAL ID path-segment codec when an ID segment cannot be encoded or decoded
 * (e.g. malformed Base64 for a composite key).
 *
 * @author Marco Pagan
 * @since 1.1.0
 */
public class DalIdCodecException extends RuntimeException {

    public DalIdCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
