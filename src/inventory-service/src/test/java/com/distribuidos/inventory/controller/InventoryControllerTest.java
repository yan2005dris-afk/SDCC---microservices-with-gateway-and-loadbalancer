package com.distribuidos.inventory.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.distribuidos.inventory.model.InventoryProduct;
import com.distribuidos.inventory.repository.InventoryProductRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

  private static final String RESERVE_URL = "/api/inventory/reserve";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InventoryProductRepository repository;

  @Test
  void rejectsNonNumericQuantityWithBadRequest() throws Exception {
    performReserve("{\"productCode\":\"PROD-001\",\"quantity\":\"abc\"}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsFractionalQuantityWithBadRequest() throws Exception {
    performReserve("{\"productCode\":\"PROD-001\",\"quantity\":2.0}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsNegativeQuantityWithBadRequest() throws Exception {
    performReserve("{\"productCode\":\"PROD-001\",\"quantity\":-1}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsBlankProductCodeWithBadRequest() throws Exception {
    performReserve("{\"productCode\":\"\",\"quantity\":5}").andExpect(status().isBadRequest());
  }

  @Test
  void validRequestReservesStock() throws Exception {
    InventoryProduct product = new InventoryProduct();
    product.setProductCode("PROD-001");
    product.setStock(10);
    product.setReservedStock(0);
    when(repository.findByProductCode("PROD-001")).thenReturn(Optional.of(product));

    performReserve(
            "{\"productCode\":\"PROD-001\",\"quantity\":5,\"orderNumber\":\"ORD-20250101-001\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(true))
        .andExpect(jsonPath("$.remainingStock").value(5));
  }

  private org.springframework.test.web.servlet.ResultActions performReserve(String body)
      throws Exception {
    return mockMvc.perform(post(RESERVE_URL).contentType(MediaType.APPLICATION_JSON).content(body));
  }
}
