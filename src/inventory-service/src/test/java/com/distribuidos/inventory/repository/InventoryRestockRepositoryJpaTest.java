package com.distribuidos.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.distribuidos.inventory.model.InventoryProduct;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
class InventoryRestockRepositoryJpaTest {

  @Autowired private InventoryProductRepository repository;

  @Autowired private JdbcTemplate jdbc;

  @Test
  void restockAtomicallyAddsQuantityAndIncrementsVersion() {
    String code = "JPA-RESTOCK";
    seed(code, 50);

    assertThat(repository.restockAtomically(code, 25)).isEqualTo(1);

    assertRow(code, 75, 0, 1);
  }

  @Test
  void restockAtomicallyUpdatesNoRowsForUnknownProduct() {
    assertThat(repository.restockAtomically("JPA-MISSING", 10)).isZero();
  }

  private void seed(String code, int stock) {
    InventoryProduct product = new InventoryProduct();
    product.setProductCode(code);
    product.setName("Restock test product");
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
