package com.concordeu.catalog.event;

public sealed interface ProductEvent
        permits ProductEvent.ProductCreated, ProductEvent.ProductUpdated, ProductEvent.ProductDeleted {

    String productName();

    record ProductCreated(String productName) implements ProductEvent {}
    record ProductUpdated(String productName) implements ProductEvent {}
    record ProductDeleted(String productName) implements ProductEvent {}
}
