package com.distribuidos.inventory.web;

import com.distribuidos.inventory.exception.ProductNotFoundException;
import com.distribuidos.inventory.exception.ReservationNotFoundException;
import com.distribuidos.inventory.exception.StockUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps inventory exceptions to RFC 7807 responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> handleInvalidRequest(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    String detail =
        exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(FieldError::getDefaultMessage)
            .orElse("Solicitud inválida");
    return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, detail, request));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetail> handleUnreadableBody(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(problem(HttpStatus.BAD_REQUEST, "Cuerpo de solicitud inválido", request));
  }

  @ExceptionHandler(ProductNotFoundException.class)
  ResponseEntity<ProblemDetail> handleProductNotFound(
      ProductNotFoundException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(problem(HttpStatus.NOT_FOUND, exception.getMessage(), request));
  }

  @ExceptionHandler(ReservationNotFoundException.class)
  ResponseEntity<ProblemDetail> handleReservationNotFound(
      ReservationNotFoundException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(problem(HttpStatus.NOT_FOUND, exception.getMessage(), request));
  }

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

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", request));
  }

  private ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setInstance(URI.create(request.getRequestURI()));
    return problem;
  }
}
