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
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryController.class)
@Import(InstanceIdentity.class)
class InventoryCatalogControllerTest {

  private static final String CATALOG_URL = "/api/inventory";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InventoryProductRepository repository;

  @MockitoBean private InventoryReservationService reservationService;

  @Test
  void listProductsReturnsPagedProductResponse() throws Exception {
    when(repository.findAll(any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(List.of(product("PROD-001"), product("PROD-002")), pageable(0, 2), 5));

    mockMvc
        .perform(get(CATALOG_URL).param("page", "0").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].productCode").value("PROD-001"))
        .andExpect(jsonPath("$.content[0].name").value("Laptop Dell XPS 15"))
        .andExpect(jsonPath("$.content[0].description").value("Intel Core i9, 32GB RAM, 1TB SSD"))
        .andExpect(jsonPath("$.content[0].stock").value(50))
        .andExpect(jsonPath("$.content[0].unitPrice").value(1899.99))
        .andExpect(jsonPath("$.content[1].productCode").value("PROD-002"))
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.currentPage").value(0))
        .andExpect(jsonPath("$.size").value(2));

    verify(repository).findAll(pageable(0, 2));
  }

  @Test
  void listProductsDefaultsToPageZeroSizeTwentySortedByProductCode() throws Exception {
    when(repository.findAll(any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(
                    product("PROD-001"),
                    product("PROD-002"),
                    product("PROD-003"),
                    product("PROD-004"),
                    product("PROD-005")),
                pageable(0, 20),
                5));

    mockMvc
        .perform(get(CATALOG_URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].productCode").value("PROD-001"))
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.currentPage").value(0))
        .andExpect(jsonPath("$.size").value(20));

    verify(repository).findAll(pageable(0, 20));
  }

  @Test
  void listProductsUsesRequestedPageNumber() throws Exception {
    when(repository.findAll(any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(product("PROD-003")),
                org.springframework.data.domain.PageRequest.of(1, 2, Sort.by("productCode")),
                5));

    mockMvc
        .perform(get(CATALOG_URL).param("page", "1").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].productCode").value("PROD-003"))
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.currentPage").value(1))
        .andExpect(jsonPath("$.size").value(2));

    verify(repository).findAll(pageable(1, 2));
  }

  @Test
  void listProductsCapsRequestedSizeAtOneHundred() throws Exception {
    when(repository.findAll(any(Pageable.class)))
        .thenAnswer(
            invocation -> {
              Pageable requested = invocation.getArgument(0);
              return new PageImpl<>(List.of(product("PROD-001")), requested, 5);
            });

    mockMvc
        .perform(get(CATALOG_URL).param("size", "150"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(100));

    verify(repository).findAll(pageable(0, 100));
  }

  @Test
  void listProductsIgnoresUnknownSortProperty() throws Exception {
    when(repository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(product("PROD-001")), pageable(0, 20), 5));

    mockMvc.perform(get(CATALOG_URL).param("sort", "bogus")).andExpect(status().isOk());

    verify(repository).findAll(pageable(0, 20));
  }

  @Test
  void listProductsKeepsOnlyKnownSortProperties() throws Exception {
    when(repository.findAll(any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(product("PROD-001")),
                org.springframework.data.domain.PageRequest.of(
                    0, 20, Sort.by(Sort.Order.desc("name"))),
                5));

    mockMvc
        .perform(get(CATALOG_URL).param("sort", "name,desc").param("sort", "bogus,asc"))
        .andExpect(status().isOk());

    verify(repository)
        .findAll(
            org.springframework.data.domain.PageRequest.of(
                0, 20, Sort.by(Sort.Order.desc("name"))));
  }

  @Test
  void listProductsKeepsClientSortWhenProvided() throws Exception {
    when(repository.findAll(any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(product("PROD-001")),
                org.springframework.data.domain.PageRequest.of(0, 20, Sort.by("name").descending()),
                5));

    mockMvc.perform(get(CATALOG_URL).param("sort", "name,desc")).andExpect(status().isOk());

    verify(repository)
        .findAll(
            org.springframework.data.domain.PageRequest.of(0, 20, Sort.by("name").descending()));
  }

  private static Pageable pageable(int page, int size) {
    return org.springframework.data.domain.PageRequest.of(page, size, Sort.by("productCode"));
  }

  private static InventoryProduct product(String code) {
    InventoryProduct product = new InventoryProduct();
    product.setProductCode(code);
    product.setName("Laptop Dell XPS 15");
    product.setDescription("Intel Core i9, 32GB RAM, 1TB SSD");
    product.setStock(50);
    product.setUnitPrice(new BigDecimal("1899.99"));
    return product;
  }
}
