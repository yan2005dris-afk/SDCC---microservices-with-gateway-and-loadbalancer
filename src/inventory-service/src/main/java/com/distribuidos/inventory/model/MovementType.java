package com.distribuidos.inventory.model;

/** Type of stock movement recorded for auditing and idempotency. */
public enum MovementType {
  RESERVATION,
  RELEASE,
  RESTOCK
}
