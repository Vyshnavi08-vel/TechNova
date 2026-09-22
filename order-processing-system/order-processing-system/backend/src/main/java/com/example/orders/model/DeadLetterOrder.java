package com.example.orders.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Dead-letter queue, implemented as a persisted table rather than an
 * in-memory queue so that failed orders survive an application restart and
 * can be inspected/replayed by an operator via the dashboard or a REST call.
 */
@Entity
@Table(name = "dead_letter_orders")
public class DeadLetterOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long originalOrderId;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(nullable = false)
    private int attemptsMade;

    @Column(nullable = false)
    private Instant deadLetteredAt = Instant.now();

    @Column(nullable = false)
    private boolean replayed = false;

    public DeadLetterOrder() {}

    public DeadLetterOrder(Long originalOrderId, String customerName, String reason, int attemptsMade) {
        this.originalOrderId = originalOrderId;
        this.customerName = customerName;
        this.reason = reason;
        this.attemptsMade = attemptsMade;
    }

    public Long getId() { return id; }
    public Long getOriginalOrderId() { return originalOrderId; }
    public String getCustomerName() { return customerName; }
    public String getReason() { return reason; }
    public int getAttemptsMade() { return attemptsMade; }
    public Instant getDeadLetteredAt() { return deadLetteredAt; }
    public boolean isReplayed() { return replayed; }
    public void setReplayed(boolean replayed) { this.replayed = replayed; }
}
