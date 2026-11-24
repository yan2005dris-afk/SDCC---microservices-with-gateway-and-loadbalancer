package com.distribuidos.order.loadbalancer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class LoadBalancedInventoryStockClientTest {

  private static final String BASE = "http://inventory";
  private static final String RESERVE = BASE + "/api/inventory/reserve";
  private static final String RELEASE = BASE + "/api/inventory/release";

  private MockRestServiceServer server;
  private LoadBalancedInventoryStockClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    server = MockRestServiceServer.bindTo(builder).build();
    client = new LoadBalancedInventoryStockClient(builder.build());
  }

  @Test
  void successfulReservationReturnsInventoryPriceAndName() {
    server
        .expect(requestTo(RESERVE))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content()
                .json("{\"productCode\":\"PROD-001\",\"quantity\":2,\"orderNumber\":\"ORD-1\"}"))
        .andRespond(
            withSuccess(
                "{\"productCode\":\"PROD-001\",\"available\":true,\"remainingStock\":48,"
                    + "\"unitPrice\":1899.99,\"productName\":\"Laptop\"}",
                MediaType.APPLICATION_JSON));

    ReservedStock stock = client.reserve("PROD-001", 2, "ORD-1");

    assertThat(stock.unitPrice()).isEqualByComparingTo(new BigDecimal("1899.99"));
    assertThat(stock.productName()).isEqualTo("Laptop");
    server.verify();
  }

  @Test
  void insufficientStockReadsInventoryMessageField() {
    server
        .expect(requestTo(RESERVE))
        .andRespond(
            withBadRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    "{\"productCode\":\"PROD-001\",\"available\":false,\"remainingStock\":2,"
                        + "\"message\":\"Stock insuficiente. Disponible: 2\"}"));

    assertThatThrownBy(() -> client.reserve("PROD-001", 5, "ORD-1"))
        .isInstanceOfSatisfying(
            StockUnavailableException.class, ex -> assertThat(ex.remainingStock()).isEqualTo(2));
  }

  @Test
  void validationProblemFromInventoryIsAContractError() {
    server
        .expect(requestTo(RESERVE))
        .andRespond(
            withBadRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("{\"title\":\"Bad Request\",\"status\":400,\"detail\":\"quantity\"}"));

    assertThatThrownBy(() -> client.reserve("PROD-001", 1, "ORD-1"))
        .isInstanceOf(InventoryContractException.class);
  }

  @Test
  void unknownProductIsNotFound() {
    server.expect(requestTo(RESERVE)).andRespond(withResourceNotFound());

    assertThatThrownBy(() -> client.reserve("PROD-X", 1, "ORD-1"))
        .isInstanceOf(ProductNotFoundException.class);
  }

  @Test
  void serverErrorIsUnavailableAndReservationMayExist() {
    server.expect(requestTo(RESERVE)).andRespond(withServerError());

    assertThatThrownBy(() -> client.reserve("PROD-001", 1, "ORD-1"))
        .isInstanceOfSatisfying(
            InventoryUnavailableException.class,
            ex -> assertThat(ex.reservationMayExist()).isTrue());
  }

  @Test
  void readTimeoutIsUnavailableAndReservationMayExist() {
    server
        .expect(requestTo(RESERVE))
        .andRespond(withException(new SocketTimeoutException("Read timed out")));

    assertThatThrownBy(() -> client.reserve("PROD-001", 1, "ORD-1"))
        .isInstanceOfSatisfying(
            InventoryUnavailableException.class,
            ex -> assertThat(ex.reservationMayExist()).isTrue());
  }

  @Test
  void connectionRefusedMeansTheReservationWasNeverSent() {
    server
        .expect(requestTo(RESERVE))
        .andRespond(withException(new ConnectException("Connection refused")));

    assertThatThrownBy(() -> client.reserve("PROD-001", 1, "ORD-1"))
        .isInstanceOfSatisfying(
            InventoryUnavailableException.class,
            ex -> assertThat(ex.reservationMayExist()).isFalse());
  }

  @Test
  void incompleteSuccessBodyIsAContractError() {
    server
        .expect(requestTo(RESERVE))
        .andRespond(
            withSuccess(
                "{\"productCode\":\"PROD-001\",\"available\":true}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.reserve("PROD-001", 1, "ORD-1"))
        .isInstanceOf(InventoryContractException.class);
  }

  @Test
  void releaseSendsOrderNumberAndToleratesMissingReservation() {
    server
        .expect(requestTo(RELEASE))
        .andExpect(content().json("{\"orderNumber\":\"ORD-1\"}"))
        .andRespond(withSuccess());
    server.expect(requestTo(RELEASE)).andRespond(withResourceNotFound());

    assertThatCode(() -> client.release("ORD-1")).doesNotThrowAnyException();
    assertThatCode(() -> client.release("ORD-1")).doesNotThrowAnyException();
    server.verify();
  }

  @Test
  void releaseFailureIsUnavailable() {
    server.expect(requestTo(RELEASE)).andRespond(withServerError());

    assertThatThrownBy(() -> client.release("ORD-1"))
        .isInstanceOf(InventoryUnavailableException.class);
  }
}
