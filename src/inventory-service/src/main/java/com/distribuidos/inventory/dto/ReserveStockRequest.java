package com.distribuidos.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.lang.Nullable;

/** Validated payload for {@code POST /api/inventory/reserve}. */
public record ReserveStockRequest(
    @NotBlank(message = "productCode no puede estar vacío") String productCode,
    @NotNull(message = "quantity es requerido") @Positive(message = "quantity debe ser > 0")
        Integer quantity,
    @Nullable String orderNumber) {}
