package com.example.orders.service;

import com.example.orders.exception.OutOfStockException;
import com.example.orders.model.Order;
import com.example.orders.model.OrderStatus;
import com.example.orders.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OrderProcessingService {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessingService.class);

    /** Bounded retry: transient failures get at most this many attempts before landing in the DLQ. */
    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 200;

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final DeadLetterService deadLetterService;

    public OrderProcessingService(OrderRepository orderRepository,
                                   InventoryService inventoryService,
                                   DeadLetterService deadLetterService) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.deadLetterService = deadLetterService;
    }

    /**
     * Entry point invoked by the controller right after an order is
     * persisted as PENDING. @Async hands this off to the bounded thread
     * pool defined in AsyncConfig, so many orders are processed
     * concurrently instead of one at a time on the request thread.
     */
    @Async("orderProcessingExecutor")
    public void processOrderAsync(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Order {} vanished before processing could start", orderId);
            return;
        }

        int attempt = 0;

        while (true) {
            attempt++;
            order.setStatus(OrderStatus.PROCESSING);
            order.setAttemptCount(attempt);
            orderRepository.save(order);

            try {
                inventoryService.reserveStock(order);
                order.setStatus(OrderStatus.COMPLETED);
                order.setLastError(null);
                orderRepository.save(order);
                log.info("Order {} completed on attempt {}", orderId, attempt);
                return;

            } catch (OutOfStockException e) {
                // Permanent business failure - retrying will never fix a
                // genuine stock shortfall, so fail fast to the DLQ.
                log.warn("Order {} out of stock: {}", orderId, e.getMessage());
                failAndDeadLetter(order, e.getMessage());
                return;

            } catch (Exception e) {
                // Transient failure (lock timeout, deadlock, connection
                // blip, etc). Retry with backoff up to MAX_ATTEMPTS.
                log.warn("Order {} attempt {} failed transiently: {}", orderId, attempt, e.toString());
                order.setLastError(e.getMessage());

                if (attempt >= MAX_ATTEMPTS) {
                    failAndDeadLetter(order, "Exceeded max retry attempts (" + MAX_ATTEMPTS + "): " + e.getMessage());
                    return;
                }

                order.setStatus(OrderStatus.RETRYING);
                orderRepository.save(order);
                sleepBackoff(attempt);
            }
        }
    }

    private void failAndDeadLetter(Order order, String reason) {
        order.setStatus(OrderStatus.FAILED);
        order.setLastError(reason);
        orderRepository.save(order);

        deadLetterService.sendToDeadLetter(order, reason);

        order.setStatus(OrderStatus.DEAD_LETTERED);
        orderRepository.save(order);
    }

    /** Simple exponential backoff between retry attempts. */
    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(BASE_BACKOFF_MS * (1L << (attempt - 1)));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
