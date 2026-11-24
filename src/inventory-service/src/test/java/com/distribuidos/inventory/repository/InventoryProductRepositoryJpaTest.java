package com.distribuidos.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.distribuidos.inventory.model.InventoryProduct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
class InventoryProductRepositoryJpaTest {

  @Autowired private InventoryProductRepository repository;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private PlatformTransactionManager transactionManager;

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

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void twentyConcurrentReservesSerializeWithoutOptimisticLockErrors() throws Exception {
    String code = "JPA-CONCURRENT";
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.executeWithoutResult(status -> seed(code, 20));

    ExecutorService pool = Executors.newFixedThreadPool(20);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> results = new ArrayList<>();
    try {
      for (int i = 0; i < 20; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return transactionTemplate.execute(
                      status -> repository.reserveAtomically(code, 1));
                }));
      }
      start.countDown();
      for (Future<Integer> result : results) {
        assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(1);
      }
    } finally {
      pool.shutdownNow();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    assertRow(code, 0, 20, 20);
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
