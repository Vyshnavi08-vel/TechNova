package com.example.orders.repository;

import com.example.orders.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * SELECT ... FOR UPDATE. This takes an exclusive row lock in Postgres,
     * so if two threads try to reserve stock for the same product at the
     * same time, the second thread blocks here until the first thread's
     * transaction commits or rolls back. That serializes the read-check-
     * decrement-write sequence per product row and is what actually
     * prevents inventory from going negative under concurrency.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    Optional<Product> findBySku(String sku);
}
