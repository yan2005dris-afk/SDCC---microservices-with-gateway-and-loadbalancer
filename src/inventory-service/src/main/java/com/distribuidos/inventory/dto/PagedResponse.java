package com.distribuidos.inventory.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable page envelope for catalog responses; keeps the documented field names. */
public record PagedResponse<T>(
    List<T> content, long totalElements, int totalPages, int currentPage, int size) {

  public static <T> PagedResponse<T> from(Page<T> page) {
    return new PagedResponse<>(
        page.getContent(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize());
  }
}
