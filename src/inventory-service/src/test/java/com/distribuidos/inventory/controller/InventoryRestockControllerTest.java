package com.distribuidos.inventory.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.distribuidos.inventory.dto.RestockResponse;
import com.distribuidos.inventory.exception.ProductNotFoundException;
import com.distribuidos.inventory.observability.InstanceIdentity;
import com.distribuidos.inventory.repository.InventoryProductRepository;
import com.distribuidos.inventory.service.InventoryReservationService;
import com.distribuidos.inventory.service.InventoryRestockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryController.class)
@Import(InstanceIdentity.class)
class InventoryRestockControllerTest {

  private static final String RESTOCK_URL = "/api/inventory/PROD-001/stock";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InventoryProductRepository repository;

  @MockitoBean private InventoryReservationService reservationService;

  @MockitoBean private InventoryRestockService restockService;

  @Test
  void restockReturnsDocumentedResponse() throws Exception {
    when(restockService.restockProduct("PROD-001", 25))
        .thenReturn(new RestockResponse("PROD-001", 25, 75, "Stock reabastecido exitosamente"));

    mockMvc
        .perform(
            put(RESTOCK_URL).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":25}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productCode").value("PROD-001"))
        .andExpect(jsonPath("$.quantity").value(25))
        .andExpect(jsonPath("$.stock").value(75))
        .andExpect(jsonPath("$.message").value("Stock reabastecido exitosamente"));
  }

  @Test
  void restockUnknownProductReturns404ProblemDetail() throws Exception {
    when(restockService.restockProduct("PROD-999", 10))
        .thenThrow(new ProductNotFoundException("PROD-999", "Producto PROD-999 no encontrado"));

    mockMvc
        .perform(
            put("/api/inventory/PROD-999/stock")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":10}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.title").value("Not Found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("Producto PROD-999 no encontrado"))
        .andExpect(jsonPath("$.instance").value("/api/inventory/PROD-999/stock"));
  }

  @Test
  void restockRejectsMissingQuantity() throws Exception {
    mockMvc
        .perform(put(RESTOCK_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("quantity requerido"));
  }

  @Test
  void restockRejectsNonPositiveQuantity() throws Exception {
    mockMvc
        .perform(
            put(RESTOCK_URL).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("quantity debe ser > 0"));
  }
}
