package com.paganbit.telaio.showcase.dal.ticket;

import org.jspecify.annotations.Nullable;

/**
 * Client-side view of a {@code feed} entry, used only to type the remote {@code DalClient} handle
 * that {@link SupportTicketDalService} calls. Kept separate from the {@code FeedEntry} JPA entity
 * so the remote-boundary contract does not leak persistence concerns.
 *
 * @param id      the server-assigned identifier of the created entry
 * @param source  the activity source (always {@code "tickets"} here)
 * @param message the human-readable activity message
 */
public record FeedActivity(@Nullable Long id, @Nullable String source, @Nullable String message) {
}
