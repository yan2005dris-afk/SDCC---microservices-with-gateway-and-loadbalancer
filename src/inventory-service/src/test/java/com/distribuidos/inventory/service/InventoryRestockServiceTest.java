package com.distribuidos.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distribuidos.inventory.dto.RestockResponse;
import com.distribuidos.inventory.exception.ProductNotFoundException;
import com.distribuidos.inventory.model.InventoryProduct;
import com.distribuidos.inventory.model.StockMovement;
import com.distribuidos.inventory.repository.InventoryProductRepository;
import com.distribuidos.inventory.repository.StockMovementRepository;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class InventoryRestockServiceTest {

  @Mock private InventoryProductRepository repository;

  @Mock private StockMovementRepository stockMovementRepository;

  @Test
  void restockReturnsUpdatedStockAndRecordsRestockMovement() {
    when(repository.restockAtomically("PROD-001", 25)).thenReturn(1);
    when(stockMovementRepository.insertIfAbsent("PROD-001", "RESTOCK", 25, null)).thenReturn(1);
    when(repository.findByProductCode("PROD-001")).thenReturn(Optional.of(product("PROD-001", 75)));

    RestockResponse response = service().restockProduct("PROD-001", 25);

    assertThat(response.productCode()).isEqualTo("PROD-001");
    assertThat(response.quantity()).isEqualTo(25);
    assertThat(response.stock()).isEqualTo(75);
    assertThat(response.message()).isEqualTo("Stock reabastecido exitosamente");
    verify(stockMovementRepository).insertIfAbsent("PROD-001", "RESTOCK", 25, null);
    verify(repository, never()).save(any(InventoryProduct.class));
    verify(stockMovementRepository, never()).save(any(StockMovement.class));
  }

  @Test
  void restockThrowsNotFoundAndSkipsMovementWhenProductIsMissing() {
    when(repository.restockAtomically("PROD-999", 10)).thenReturn(0);

    ProductNotFoundException exception =
        assertThrows(
            ProductNotFoundException.class, () -> service().restockProduct("PROD-999", 10));

    assertThat(exception).hasMessage("Producto PROD-999 no encontrado");
    verify(stockMovementRepository, never()).insertIfAbsent(any(), any(), anyInt(), any());
  }

  @Test
  void restockMethodRunsInsideTransaction() throws NoSuchMethodException {
    Method method =
        InventoryRestockService.class.getDeclaredMethod("restockProduct", String.class, int.class);

    assertThat(method.getAnnotation(Transactional.class)).isNotNull();
  }

  private InventoryRestockService service() {
    return new InventoryRestockService(repository, stockMovementRepository);
  }

  private InventoryProduct product(String code, int stock) {
    InventoryProduct product = new InventoryProduct();
    product.setProductCode(code);
    product.setName("Restock test product");
    product.setStock(stock);
    product.setReservedStock(0);
    product.setUnitPrice(BigDecimal.ONE);
    return product;
  }
}
