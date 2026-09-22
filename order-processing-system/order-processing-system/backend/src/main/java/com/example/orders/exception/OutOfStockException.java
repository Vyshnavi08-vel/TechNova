package com.example.orders.exception;

/**
 * Thrown when a product genuinely does not have enough stock to satisfy an
 * order line. This is a PERMANENT failure - retrying won't help because
 * the stock isn't coming back mid-request - so the retry loop must treat
 * this differently from a transient DB/locking failure and send it
 * straight to the dead-letter queue without burning retry attempts.
 */
public class OutOfStockException extends RuntimeException {
    public OutOfStockException(String message) {
        super(message);
    }
}
