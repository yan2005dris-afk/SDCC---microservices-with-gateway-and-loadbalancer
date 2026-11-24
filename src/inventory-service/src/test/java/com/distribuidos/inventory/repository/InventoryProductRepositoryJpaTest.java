package com.distribuidos.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.distribuidos.inventory.model.InventoryProduct;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
class InventoryProductRepositoryJpaTest {

  @Autowired private InventoryProductRepository repository;

  @Autowired private JdbcTemplate jdbc;

  @Test
  void bulkUpdateClearsPersistenceContextForReserveAndRelease() {
    String code = "JPA-REPLAY";
    seed(code, 50);

    assertThat(repository.reserveAtomically(code, 5)).isEqualTo(1);
    assertThat(repository.findByProductCode(code).orElseThrow().getStock()).isEqualTo(45);
    assertRow(code, 45, 5, 1);

    assertThat(repository.releaseAtomically(code, 5)).isEqualTo(1);
    assertThat(repository.findByProductCode(code).orElseThrow().getStock()).isEqualTo(50);
    assertRow(code, 50, 0, 2);
  }

  @Test
  void reserveAboveStockUpdatesNoRows() {
    String code = "JPA-INSUFFICIENT";
    seed(code, 3);

    assertThat(repository.reserveAtomically(code, 5)).isZero();
    assertRow(code, 3, 0, 0);
    assertThat(repository.findByProductCode(code).orElseThrow().getStock()).isEqualTo(3);
  }

  private void seed(String code, int stock) {
    InventoryProduct product = new InventoryProduct();
    product.setProductCode(code);
    product.setName("Slice test product");
    product.setReservedStock(0);
    product.setStock(stock);
    product.setUnitPrice(BigDecimal.ONE);
    repository.saveAndFlush(product);
  }

  private void assertRow(String code, int stock, int reservedStock, long version) {
    Integer actualStock =
        jdbc.queryForObject(
            "select stock from inventory_products where product_code = ?", Integer.class, code);
    Integer actualReserved =
        jdbc.queryForObject(
            "select reserved_stock from inventory_products where product_code = ?",
            Integer.class,
            code);
    Long actualVersion =
        jdbc.queryForObject(
            "select version from inventory_products where product_code = ?", Long.class, code);

    assertThat(actualStock).isEqualTo(stock);
    assertThat(actualReserved).isEqualTo(reservedStock);
    assertThat(actualVersion).isEqualTo(version);
  }
}
