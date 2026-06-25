# Validation patterns

## Record with Bean Validation + compact constructor
```java
record CreateOrderCommand(
        @NotNull UUID customerId,
        @NotEmpty List<@Valid OrderItem> items) {
    CreateOrderCommand {
        if (items.size() > 50) throw new ValidationException("Order cannot exceed 50 items");
        items = List.copyOf(items);
    }
}
```

## Controller — always @Valid on @RequestBody
```java
@PostMapping
ResponseEntity<OrderResponse> create(@RequestBody @Valid CreateOrderCommand cmd) {
    return ResponseEntity.status(CREATED).body(orderService.create(cmd));
}
```

## Rules
- Bean Validation constraints on record components — not service params
- Cross-field / business rules in compact constructor → throw `ValidationException`
- `@Valid` on every `@RequestBody` and `@PathVariable`
- Never re-validate in service layer what is declared on the record
