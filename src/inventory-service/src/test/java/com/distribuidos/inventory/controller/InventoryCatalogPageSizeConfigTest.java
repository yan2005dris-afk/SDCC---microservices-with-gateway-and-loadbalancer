package com.distribuidos.inventory.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.distribuidos.inventory.model.InventoryProduct;
import com.distribuidos.inventory.observability.InstanceIdentity;
import com.distribuidos.inventory.repository.InventoryProductRepository;
import com.distribuidos.inventory.service.InventoryReservationService;
import com.distribuidos.inventory.service.InventoryRestockService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifica que el tope de items por página del catálogo es configurable por propiedad ({@code
 * inventory.pagination.max-page-size}) y no un valor fijo en código.
 */
@WebMvcTest(InventoryController.class)
@Import(InstanceIdentity.class)
@TestPropertySource(properties = "inventory.pagination.max-page-size=2")
class InventoryCatalogPageSizeConfigTest {

  private static final String CATALOG_URL = "/api/inventory";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InventoryProductRepository repository;

  @MockitoBean private InventoryReservationService reservationService;

  @MockitoBean private InventoryRestockService restockService;

  @Test
  void listProductsCapsRequestedSizeAtConfiguredLimit() throws Exception {
    when(repository.findAll(any(Pageable.class)))
        .thenAnswer(
            invocation -> {
              Pageable requested = invocation.getArgument(0);
              return new PageImpl<>(List.of(product("PROD-001")), requested, 5);
            });

    mockMvc
        .perform(get(CATALOG_URL).param("size", "50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(2));

    verify(repository)
        .findAll(org.springframework.data.domain.PageRequest.of(0, 2, Sort.by("productCode")));
  }

  private static InventoryProduct product(String code) {
    InventoryProduct product = new InventoryProduct();
    product.setProductCode(code);
    product.setName("Laptop Dell XPS 15");
    product.setDescription("Intel Core i9, 32GB RAM, 1TB SSD");
    product.setStock(50);
    product.setUnitPrice(new java.math.BigDecimal("1899.99"));
    return product;
  }
}
