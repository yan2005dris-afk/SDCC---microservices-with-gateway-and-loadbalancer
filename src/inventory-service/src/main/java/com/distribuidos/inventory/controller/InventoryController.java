package com.distribuidos.inventory.controller;

import com.distribuidos.inventory.dto.PagedResponse;
import com.distribuidos.inventory.dto.ProductResponse;
import com.distribuidos.inventory.dto.ReleaseResponse;
import com.distribuidos.inventory.dto.ReleaseStockRequest;
import com.distribuidos.inventory.dto.ReserveStockRequest;
import com.distribuidos.inventory.exception.ProductNotFoundException;
import com.distribuidos.inventory.repository.InventoryProductRepository;
import com.distribuidos.inventory.service.InventoryReservationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

  private static final int MAX_PAGE_SIZE = 100;

  private final InventoryProductRepository repository;
  private final InventoryReservationService reservationService;

  public InventoryController(
      InventoryProductRepository repository, InventoryReservationService reservationService) {
    this.repository = repository;
    this.reservationService = reservationService;
  }

  @GetMapping
  public PagedResponse<ProductResponse> listProducts(Pageable pageable) {
    Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by("productCode");
    Pageable effective =
        PageRequest.of(
            pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), sort);
    return PagedResponse.from(
        repository
            .findAll(effective)
            .map(
                product ->
                    new ProductResponse(
                        product.getProductCode(),
                        product.getName(),
                        product.getDescription(),
                        product.getStock(),
                        product.getUnitPrice())));
  }

  @GetMapping("/{productCode}")
  public ResponseEntity<?> getProduct(@PathVariable String productCode) {
    return repository
        .findByProductCode(productCode)
        .map(ResponseEntity::ok)
        .orElseThrow(() -> new ProductNotFoundException(productCode));
  }

  @PostMapping("/reserve")
  public ResponseEntity<?> reserveStock(@Valid @RequestBody ReserveStockRequest request) {
    return ResponseEntity.ok()
        .body(
            reservationService.reserveStock(
                request.productCode(), request.quantity(), request.orderNumber()));
  }

  @PostMapping("/release")
  public ResponseEntity<ReleaseResponse> releaseStock(
      @Valid @RequestBody ReleaseStockRequest request) {
    return ResponseEntity.ok().body(reservationService.releaseStock(request.orderNumber()));
  }
}
