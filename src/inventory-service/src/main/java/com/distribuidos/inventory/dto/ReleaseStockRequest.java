package com.distribuidos.inventory.dto;

import jakarta.validation.constraints.NotBlank;

/** Validated payload for {@code POST /api/inventory/release}. */
public record ReleaseStockRequest(
    @NotBlank(message = "orderNumber no puede estar vacío") String orderNumber) {}
