package com.example.orders.service;

import com.example.orders.exception.OutOfStockException;
import com.example.orders.model.Order;
import com.example.orders.model.OrderItem;
import com.example.orders.model.Product;
import com.example.orders.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    private final ProductRepository productRepository;

    public InventoryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Reserves stock for every line item of an order in a single DB
     * transaction. Each product row is locked with SELECT ... FOR UPDATE
     * (see ProductRepository#findByIdForUpdate) before its quantity is
     * checked and decremented, so concurrent threads reserving stock for
     * the same product are serialized at the row level and inventory can
     * never be decremented below zero.
     *
     * Rows are locked in ascending product-id order to avoid two orders
     * deadlocking each other by locking the same two products in opposite
     * order.
     */
    @Transactional
    public void reserveStock(Order order) {
        List<OrderItem> items = order.getItems();
        items.sort((a, b) -> Long.compare(a.getProductId(), b.getProductId()));

        for (OrderItem item : items) {
            Product product = productRepository.findByIdForUpdate(item.getProductId())
                    .orElseThrow(() -> new OutOfStockException(
                            "Product " + item.getProductId() + " does not exist"));

            if (product.getQuantityAvailable() < item.getQuantity()) {
                throw new OutOfStockException(
                        "Insufficient stock for " + product.getSku() +
                                " (requested " + item.getQuantity() +
                                ", available " + product.getQuantityAvailable() + ")");
            }

            product.setQuantityAvailable(product.getQuantityAvailable() - item.getQuantity());
            productRepository.save(product);
        }
    }
}
