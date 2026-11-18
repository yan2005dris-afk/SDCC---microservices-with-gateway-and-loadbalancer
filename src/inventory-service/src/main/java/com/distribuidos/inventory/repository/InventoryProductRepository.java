package com.distribuidos.inventory.repository;

import com.distribuidos.inventory.model.InventoryProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryProductRepository extends JpaRepository<InventoryProduct, Long> {
    Optional<InventoryProduct> findByProductCode(String productCode);
}
