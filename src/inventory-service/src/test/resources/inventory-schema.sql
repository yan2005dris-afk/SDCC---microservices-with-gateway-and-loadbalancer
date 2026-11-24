CREATE TABLE inventory_products (
    id BIGSERIAL PRIMARY KEY,
    product_code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    stock INT NOT NULL DEFAULT 0 CHECK (stock >= 0),
    reserved_stock INT NOT NULL DEFAULT 0 CHECK (reserved_stock >= 0),
    unit_price NUMERIC(10, 2) NOT NULL CHECK (unit_price > 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE stock_movement_history (
    id BIGSERIAL PRIMARY KEY,
    product_code VARCHAR(50) NOT NULL,
    movement_type VARCHAR(30) NOT NULL,
    quantity INT NOT NULL,
    reference_order_number VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_stock_movement_order_product_type
ON stock_movement_history(reference_order_number, movement_type, product_code)
WHERE reference_order_number IS NOT NULL;

INSERT INTO inventory_products (product_code, name, description, stock, reserved_stock, unit_price, version)
VALUES
('PROD-A', 'Product A', 'Integration test product A', 50, 0, 10.00, 0),
('PROD-B', 'Product B', 'Integration test product B', 30, 0, 20.00, 0)
ON CONFLICT (product_code) DO NOTHING;
