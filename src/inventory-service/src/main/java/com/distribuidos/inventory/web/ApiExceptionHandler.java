package com.distribuidos.inventory.web;

import com.distribuidos.inventory.exception.InvalidStockReleaseException;
import com.distribuidos.inventory.exception.ProductNotFoundException;
import com.distribuidos.inventory.exception.ReservationNotFoundException;
import com.distribuidos.inventory.exception.StockUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps inventory exceptions to RFC 7807 responses.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so the standard Spring MVC exceptions (405,
 * 415, 404 without mapping, ...) keep their own status instead of being swallowed by the catch-all
 * and reported as 500. The messages of the domain errors are produced by overriding the protected
 * handlers of the parent class, never by declaring a second {@code @ExceptionHandler} for the same
 * type (that would be an ambiguous mapping).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(FieldError::getDefaultMessage)
            .orElse("Solicitud inválida");
    return problemResponse(HttpStatus.BAD_REQUEST, detail, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return problemResponse(
        HttpStatus.BAD_REQUEST, "Cuerpo de solicitud inválido", headers, status, request);
  }

  /**
   * Keeps the inherited handling of the standard Spring MVC exceptions but guarantees that the
   * answer is a {@code application/problem+json} document with the request URI as instance.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ResponseEntity<Object> response =
        super.handleExceptionInternal(ex, body, headers, status, request);

    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.putAll(response.getHeaders());

    Object responseBody = response.getBody();
    if (responseBody instanceof ProblemDetail detail) {
      if (detail.getInstance() == null) {
        detail.setInstance(instanceUri(request));
      }
      responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    return new ResponseEntity<>(responseBody, responseHeaders, response.getStatusCode());
  }

  @ExceptionHandler(ProductNotFoundException.class)
  ResponseEntity<ProblemDetail> handleProductNotFound(
      ProductNotFoundException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem(HttpStatus.NOT_FOUND, exception.getMessage(), request));
  }

  @ExceptionHandler(ReservationNotFoundException.class)
  ResponseEntity<ProblemDetail> handleReservationNotFound(
      ReservationNotFoundException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem(HttpStatus.NOT_FOUND, exception.getMessage(), request));
  }

  /**
   * Stock rejection keeps its historical custom body: {@code order-service} deserializes it into
   * {@code InventoryReserveResponse}, so it cannot be turned into a {@code ProblemDetail} without
   * breaking the inter-service contract.
   */
  @ExceptionHandler(StockUnavailableException.class)
  ResponseEntity<Map<String, Object>> handleStockUnavailable(StockUnavailableException exception) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "productCode",
                exception.getProductCode(),
                "available",
                false,
                "remainingStock",
                exception.getAvailableStock(),
                "message",
                exception.getMessage()));
  }

  @ExceptionHandler(InvalidStockReleaseException.class)
  ResponseEntity<ProblemDetail> handleInvalidRelease(
      InvalidStockReleaseException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem(HttpStatus.CONFLICT, exception.getMessage(), request));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
    log.error(
        "Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", request));
  }

  private ResponseEntity<Object> problemResponse(
      HttpStatusCode status,
      String detail,
      HttpHeaders headers,
      HttpStatusCode responseStatus,
      WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setInstance(instanceUri(request));

    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.putAll(headers);
    responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);

    return new ResponseEntity<>(problem, responseHeaders, responseStatus);
  }

  private ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setInstance(URI.create(request.getRequestURI()));
    return problem;
  }

  private URI instanceUri(WebRequest request) {
    if (request instanceof ServletWebRequest servletWebRequest) {
      return URI.create(servletWebRequest.getRequest().getRequestURI());
    }
    String description = request.getDescription(false);
    return URI.create(description.startsWith("uri=") ? description.substring(4) : description);
  }
}
