-- Idempotency key for stock movements: (order, movement type, product).
-- Run on already initialized databases:
--   docker exec -i <postgres-primary> psql -U <user> -d inventory_db \
--     < docker/postgres/migrate-stock-movement-index.sql
\c inventory_db

DROP INDEX IF EXISTS uq_stock_movement_ref_type;

CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_movement_order_product_type
ON stock_movement_history(reference_order_number, movement_type, product_code)
WHERE reference_order_number IS NOT NULL;

-- Consulta de movimientos por producto (historial y auditoría de stock).
CREATE INDEX IF NOT EXISTS idx_stock_movement_product_code
ON stock_movement_history(product_code);
