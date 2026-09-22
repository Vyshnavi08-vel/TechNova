package com.example.orders.controller;

import com.example.orders.dto.OrderItemRequest;
import com.example.orders.dto.OrderRequest;
import com.example.orders.model.DeadLetterOrder;
import com.example.orders.model.Order;
import com.example.orders.model.OrderItem;
import com.example.orders.model.OrderStatus;
import com.example.orders.repository.DeadLetterOrderRepository;
import com.example.orders.repository.OrderRepository;
import com.example.orders.service.OrderProcessingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final DeadLetterOrderRepository deadLetterOrderRepository;
    private final OrderProcessingService orderProcessingService;

    public OrderController(OrderRepository orderRepository,
                            DeadLetterOrderRepository deadLetterOrderRepository,
                            OrderProcessingService orderProcessingService) {
        this.orderRepository = orderRepository;
        this.deadLetterOrderRepository = deadLetterOrderRepository;
        this.orderProcessingService = orderProcessingService;
    }

    /**
     * Persists the order as PENDING immediately (fast response to the
     * client / dashboard) then hands it off to the async thread pool for
     * actual stock reservation. This decouples "order accepted" from
     * "order fulfilled" the way a real order system would.
     */
    @PostMapping
    public ResponseEntity<Order> submitOrder(@Valid @RequestBody OrderRequest request) {
        Order order = new Order(request.getCustomerName());
        for (OrderItemRequest itemReq : request.getItems()) {
            order.addItem(new OrderItem(itemReq.getProductId(), "", itemReq.getQuantity()));
        }
        order.setStatus(OrderStatus.PENDING);
        Order saved = orderRepository.save(order);

        orderProcessingService.processOrderAsync(saved.getId());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(saved);
    }

    @GetMapping
    public List<Order> listOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
        return orderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/dead-letter")
    public List<DeadLetterOrder> listDeadLetterOrders() {
        return deadLetterOrderRepository.findAllByOrderByDeadLetteredAtDesc();
    }

    /**
     * Manually re-submit a dead-lettered order for processing (e.g. after
     * an operator has restocked inventory). Resets attempt count and
     * re-enters the same async pipeline.
     */
    @PostMapping("/{id}/replay")
    public ResponseEntity<Order> replay(@PathVariable Long id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        order.setStatus(OrderStatus.PENDING);
        order.setAttemptCount(0);
        order.setLastError(null);
        orderRepository.save(order);

        orderProcessingService.processOrderAsync(order.getId());
        return ResponseEntity.accepted().body(order);
    }
}
