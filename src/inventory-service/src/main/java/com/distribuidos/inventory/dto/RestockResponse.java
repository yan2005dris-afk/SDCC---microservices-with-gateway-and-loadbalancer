package com.distribuidos.inventory.dto;

/** Response body for an atomic stock restock operation. */
public record RestockResponse(
    String productCode, Integer quantity, Integer stock, String message) {}
