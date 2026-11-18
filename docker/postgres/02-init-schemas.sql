-- Inicialización de bases de datos para microservicios
CREATE DATABASE orders_db;
CREATE DATABASE inventory_db;

-- Conexión a orders_db para crear el esquema de pedidos
\c orders_db

CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(64) UNIQUE NOT NULL,
    customer_name VARCHAR(150) NOT NULL,
    customer_email VARCHAR(150) NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_code VARCHAR(50) NOT NULL,
    product_name VARCHAR(150) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10, 2) NOT NULL CHECK (unit_price >= 0),
    subtotal NUMERIC(12, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS order_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT REFERENCES orders(id) ON DELETE SET NULL,
    operation_type VARCHAR(30) NOT NULL,
    instance_id VARCHAR(100) NOT NULL,
    db_node VARCHAR(30) NOT NULL,
    logged_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_customer_email ON orders(customer_email);
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);

-- Conexión a inventory_db para crear el esquema de inventario
\c inventory_db

CREATE TABLE IF NOT EXISTS inventory_products (
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

CREATE TABLE IF NOT EXISTS stock_movement_history (
    id BIGSERIAL PRIMARY KEY,
    product_code VARCHAR(50) NOT NULL,
    movement_type VARCHAR(30) NOT NULL,
    quantity INT NOT NULL,
    reference_order_number VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Seed data inicial para inventario
INSERT INTO inventory_products (product_code, name, description, stock, reserved_stock, unit_price, version)
VALUES 
('PROD-001', 'Laptop Dell XPS 15', 'Intel Core i9, 32GB RAM, 1TB SSD', 50, 0, 1899.99, 0),
('PROD-002', 'Monitor UltraWide 34"', 'LG Curved IPS 144Hz 1ms', 30, 0, 499.50, 0),
('PROD-003', 'Teclado Mecánico RGB', 'Switches Cherry MX Blue, Wireless', 100, 0, 89.90, 0),
('PROD-004', 'Mouse Ergonómico Inalámbrico', 'Sensor óptico 16000 DPI', 75, 0, 59.99, 0),
('PROD-005', 'Auriculares Noise Cancelling', 'Cancelación activa de ruido Bluetooth', 40, 0, 199.00, 0)
ON CONFLICT (product_code) DO NOTHING;
