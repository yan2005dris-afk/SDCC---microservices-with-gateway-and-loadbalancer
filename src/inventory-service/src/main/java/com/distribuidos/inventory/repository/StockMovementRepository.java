package com.distribuidos.inventory.repository;

import com.distribuidos.inventory.model.MovementType;
import com.distribuidos.inventory.model.StockMovement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
