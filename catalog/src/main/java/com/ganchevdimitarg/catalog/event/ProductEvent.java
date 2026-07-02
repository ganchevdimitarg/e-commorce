package com.ganchevdimitarg.catalog.event;

import java.time.Instant;

public sealed interface ProductEvent
        permits ProductEvent.ProductCreated, ProductEvent.ProductUpdated, ProductEvent.ProductDeleted {

    String eventId();
    String productId();
    String productName();
    Instant occurredAt();

    record ProductCreated(String eventId, String productId, String productName, Instant occurredAt) implements ProductEvent {}
    record ProductUpdated(String eventId, String productId, String productName, Instant occurredAt) implements ProductEvent {}
    record ProductDeleted(String eventId, String productId, String productName, Instant occurredAt) implements ProductEvent {}
}
