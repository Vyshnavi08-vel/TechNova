package com.example.orders.model;

public enum OrderStatus {
    PENDING,       // accepted, sitting in the queue / not yet picked up by a worker thread
    PROCESSING,    // a worker thread currently holds it
    COMPLETED,     // stock reserved successfully
    RETRYING,      // a transient failure occurred, will be retried
    FAILED,        // exhausted retries or hit a permanent business failure (out of stock)
    DEAD_LETTERED  // moved to the dead-letter table for manual inspection / replay
}
