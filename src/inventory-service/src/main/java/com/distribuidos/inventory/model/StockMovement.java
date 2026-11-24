package com.distribuidos.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Audit record for a stock reservation, release or restock operation. */
@Entity
@Table(name = "stock_movement_history")
public class StockMovement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "product_code", nullable = false, length = 50)
  private String productCode;

  @Column(name = "movement_type", nullable = false)
  @Enumerated(EnumType.STRING)
  private MovementType type;

  @Column(nullable = false)
  private Integer quantity;

  @Column(name = "reference_order_number", length = 64)
  private String referenceOrderNumber;

  @Column(name = "created_at", updatable = false)
  private OffsetDateTime createdAt = OffsetDateTime.now();

  public Long getId() {
    return id;
  }

  public String getProductCode() {
    return productCode;
  }

  public void setProductCode(String productCode) {
    this.productCode = productCode;
  }

  public MovementType getType() {
    return type;
  }

  public void setType(MovementType type) {
    this.type = type;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public void setQuantity(Integer quantity) {
    this.quantity = quantity;
  }

  public String getReferenceOrderNumber() {
    return referenceOrderNumber;
  }

  public void setReferenceOrderNumber(String referenceOrderNumber) {
    this.referenceOrderNumber = referenceOrderNumber;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
