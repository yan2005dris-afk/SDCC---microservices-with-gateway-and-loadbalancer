package com.distribuidos.inventory.repository;

import com.distribuidos.inventory.model.InventoryProduct;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryProductRepository extends JpaRepository<InventoryProduct, Long> {
  Optional<InventoryProduct> findByProductCode(String productCode);
}
