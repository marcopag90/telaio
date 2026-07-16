/**
 * Wire contract of the Telaio DAL REST API, version 1.
 *
 * <p>This package codifies the {@code /dal/v1} wire shape shared between the server
 * (telaio-web) and any remote client (telaio-client): path and parameter constants, the
 * validation-error payload and the ID path-segment codec. The {@code v1} package segment allows a
 * future {@code v2} contract to coexist in the same artifact; version-agnostic contract types live
 * one package up, in {@link com.paganbit.telaio.rest.contract}.</p>
 *
 * <p><strong>Compatibility policy:</strong> the v1 wire shape is frozen. Any change to the
 * constants, payload shapes or encoding rules in this package is a breaking API change that
 * requires a new contract version ({@code /dal/v2}), never an in-place edit.</p>
 *
 * @since 1.1.0
 */
@NullMarked
package com.paganbit.telaio.rest.contract.v1;

import org.jspecify.annotations.NullMarked;
