/**
 * Version-agnostic root of the Telaio DAL REST wire contract.
 *
 * <p>This package holds contract types that are <strong>not</strong> tied to a specific wire
 * version — types with no byte-on-the-wire shape of their own, shared across every contract
 * version. Today that is {@link com.paganbit.telaio.rest.contract.DalIdCodecException}, a neutral
 * failure type reused by the codec regardless of version.</p>
 *
 * <p>Everything that <em>does</em> define a wire shape — path and parameter constants, payload
 * types and the id-encoding scheme — lives under a versioned sub-package
 * ({@link com.paganbit.telaio.rest.contract.v1}), so that a future {@code v2} contract can coexist
 * in the same artifact without disturbing v1.</p>
 *
 * @since 1.1.0
 */
@NullMarked
package com.paganbit.telaio.rest.contract;

import org.jspecify.annotations.NullMarked;
