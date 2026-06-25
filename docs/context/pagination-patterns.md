# Pagination patterns

## PageResponse record
```java
record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(),
                                  p.getTotalElements(), p.getTotalPages());
    }
}
```

## Controller pattern
```java
@GetMapping
PageResponse<OrderResponse> list(
        @PageableDefault(size = 20, sort = "createdAt", direction = DESC) Pageable pageable) {
    return PageResponse.of(orderService.findAll(pageable));
}
```

## Rules
- Default page size: 20; maximum: 100 — enforce `@Max(100)` on `size` param
- Never expose raw `Page<Entity>` — always map to `Page<ResponseRecord>` first
- Never use `findAll()` without pagination on large collections
