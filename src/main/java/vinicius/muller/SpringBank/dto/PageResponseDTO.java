package vinicius.muller.SpringBank.dto;

import org.springframework.data.domain.Page;

import java.util.List;

// Keeps the paged JSON contract ours - PageImpl's own shape is explicitly not a stable API
public record PageResponseDTO<T>(List<T> content, int page, int size, long totalElements, int totalPages,
                                 boolean last) {

    public PageResponseDTO(Page<T> page) {
        this(page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
