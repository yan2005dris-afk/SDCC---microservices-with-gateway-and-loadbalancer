package com.distribuidos.inventory.dto;

import java.math.BigDecimal;

/** Public catalog projection shared by the paginated inventory endpoint. */
public record ProductResponse(
    String productCode, String name, String description, Integer stock, BigDecimal unitPrice) {}
