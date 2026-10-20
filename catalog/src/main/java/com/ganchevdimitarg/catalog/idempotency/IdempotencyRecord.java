package com.ganchevdimitarg.catalog.idempotency;

/**
 * A single idempotency entry stored in Redis.
 *
 * <p>{@link State#IN_PROGRESS} is written when a request claims a key and is replaced by
 * {@link State#COMPLETED} once the response has been produced. A COMPLETED record carries the
 * response status, content type, {@code Location} header and body so a retry can be replayed
 * without executing the handler again.
 */
public record IdempotencyRecord(State state, int status,
                                String contentType, String location, String body) {

    public enum State { IN_PROGRESS, COMPLETED }

    public static IdempotencyRecord inProgress() {
        return new IdempotencyRecord(State.IN_PROGRESS, 0, null, null, null);
    }

    public static IdempotencyRecord completed(int status, String contentType,
                                              String location, String body) {
        return new IdempotencyRecord(State.COMPLETED, status, contentType, location, body);
    }
}
