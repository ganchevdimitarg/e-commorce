package com.concordeu.order.service;

import com.concordeu.order.domain.Item;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ItemService {
    Flux<Item> saveAll(List<Item> items);
    Flux<Item> findItemsByOrderId(Long orderId);
}
