package com.example.orders.service;

import com.example.orders.model.DeadLetterOrder;
import com.example.orders.model.Order;
import com.example.orders.repository.DeadLetterOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeadLetterService {

    private final DeadLetterOrderRepository deadLetterOrderRepository;

    public DeadLetterService(DeadLetterOrderRepository deadLetterOrderRepository) {
        this.deadLetterOrderRepository = deadLetterOrderRepository;
    }

    @Transactional
    public void sendToDeadLetter(Order order, String reason) {
        DeadLetterOrder dlq = new DeadLetterOrder(
                order.getId(),
                order.getCustomerName(),
                reason,
                order.getAttemptCount()
        );
        deadLetterOrderRepository.save(dlq);
    }
}
