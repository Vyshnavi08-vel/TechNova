INSERT INTO products (sku, name, quantity_available, price, version)
SELECT * FROM (VALUES
    ('SKU-001', 'Wireless Mouse', 50, 19.99, 0),
    ('SKU-002', 'Mechanical Keyboard', 30, 89.99, 0),
    ('SKU-003', '27-inch Monitor', 15, 249.99, 0),
    ('SKU-004', 'USB-C Hub', 100, 24.99, 0),
    ('SKU-005', 'Webcam 1080p', 5, 39.99, 0)
) AS v(sku, name, quantity_available, price, version)
WHERE NOT EXISTS (SELECT 1 FROM products);
