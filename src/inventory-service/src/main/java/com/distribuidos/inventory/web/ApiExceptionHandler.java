package com.distribuidos.inventory.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
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

  private ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setInstance(URI.create(request.getRequestURI()));
    return problem;
  }
}
