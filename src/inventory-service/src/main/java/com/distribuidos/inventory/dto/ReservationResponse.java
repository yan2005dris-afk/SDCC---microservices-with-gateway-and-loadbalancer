package com.distribuidos.inventory.dto;

import java.math.BigDecimal;

/** Response payload for a successful stock reservation. */
public record ReservationResponse(
    String productCode,
    boolean available,
    Integer remainingStock,
    BigDecimal unitPrice,
    String productName,
    String message) {}
