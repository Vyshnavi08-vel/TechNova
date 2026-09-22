package com.example.orders.model;

import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int quantityAvailable;

    @Column(nullable = false)
    private double price;

    // Belt-and-braces optimistic lock. The pessimistic row lock taken in
    // InventoryService is what actually prevents overselling under
    // concurrency; this @Version just protects against any code path that
    // ever updates a Product without going through that locked path.
    @Version
    private Long version;

    public Product() {}

    public Product(String sku, String name, int quantityAvailable, double price) {
        this.sku = sku;
        this.name = name;
        this.quantityAvailable = quantityAvailable;
        this.price = price;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getQuantityAvailable() { return quantityAvailable; }
    public void setQuantityAvailable(int quantityAvailable) { this.quantityAvailable = quantityAvailable; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public Long getVersion() { return version; }
}
