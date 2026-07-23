/**
 * Blocking client contract pinned to the DAL REST API v1 — the client-side mirror of
 * {@code com.paganbit.telaio.rest.contract.v1}. Types here are frozen together with the wire
 * contract: a signature change requires a sibling {@code blocking.v2} package, never an in-place
 * edit. Version-agnostic types (the {@code TelaioClient} hub, paging and sorting inputs,
 * exceptions) live in {@code telaio-rest-client-shared} and are shared across contract versions
 * and transports. The freeze extends to their shapes wherever they appear in frozen signatures:
 * such shared types evolve additively only, and a wire revision that changes one of them ships a
 * replacement type in the sibling version package instead of editing the shared one.
 *
 * @since 1.1.0
 */
@NullMarked
package com.paganbit.telaio.rest.client.blocking.v1;

import org.jspecify.annotations.NullMarked;
