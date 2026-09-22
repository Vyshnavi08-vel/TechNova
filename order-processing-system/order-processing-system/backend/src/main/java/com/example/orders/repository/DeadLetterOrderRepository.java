package com.example.orders.repository;

import com.example.orders.model.DeadLetterOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeadLetterOrderRepository extends JpaRepository<DeadLetterOrder, Long> {
    List<DeadLetterOrder> findAllByOrderByDeadLetteredAtDesc();
}
