package com.distribuidos.inventory.repository;

import com.distribuidos.inventory.model.MovementType;
import com.distribuidos.inventory.model.StockMovement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

  Optional<StockMovement> findByReferenceOrderNumberAndType(
      String referenceOrderNumber, MovementType type);

  Optional<StockMovement> findByReferenceOrderNumberAndTypeAndProductCode(
      String referenceOrderNumber, MovementType type, String productCode);

  boolean existsByReferenceOrderNumberAndType(String referenceOrderNumber, MovementType type);

  boolean existsByReferenceOrderNumberAndTypeAndProductCode(
      String referenceOrderNumber, MovementType type, String productCode);

  List<StockMovement> findAllByReferenceOrderNumberAndType(
      String referenceOrderNumber, MovementType type);

  @Modifying
  @Query(
      value =
          """
          INSERT INTO stock_movement_history (product_code, movement_type, quantity, reference_order_number)
          VALUES (:productCode, :movementType, :quantity, :referenceOrderNumber)
          ON CONFLICT (reference_order_number, movement_type, product_code)
            WHERE reference_order_number IS NOT NULL
          DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(
      @Param("productCode") String productCode,
      @Param("movementType") String movementType,
      @Param("quantity") int quantity,
      @Param("referenceOrderNumber") String referenceOrderNumber);
}
