package com.distribuidos.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Request body for an atomic stock restock operation. */
public record RestockRequest(
    @NotNull(message = "quantity requerido") @Positive(message = "quantity debe ser > 0")
        Integer quantity) {}
