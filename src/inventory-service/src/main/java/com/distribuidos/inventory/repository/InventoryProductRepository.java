package com.distribuidos.inventory.repository;

import com.distribuidos.inventory.model.InventoryProduct;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryProductRepository extends JpaRepository<InventoryProduct, Long> {
  Optional<InventoryProduct> findByProductCode(String productCode);

  @Modifying
  @Query(
      """
      UPDATE InventoryProduct p
         SET p.stock = p.stock - :qty,
             p.reservedStock = p.reservedStock + :qty,
             p.version = p.version + 1,
             p.updatedAt = CURRENT_TIMESTAMP
       WHERE p.productCode = :code AND p.stock >= :qty
      """)
  int reserveAtomically(@Param("code") String productCode, @Param("qty") int quantity);
}
