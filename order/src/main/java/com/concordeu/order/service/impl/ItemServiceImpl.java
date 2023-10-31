package com.concordeu.order.service.impl;

import com.concordeu.order.domain.Item;
import com.concordeu.order.repositories.ItemRepository;
import com.concordeu.order.service.ItemService;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {
    private final ItemRepository itemRepository;

    @Override
    public Flux<Item> saveAll(List<Item> items) {
        return itemRepository.saveAll(items);
    }

    @Override
    public Flux<Item> findItemsByOrderId(Long orderId) {
        return itemRepository.findItemsByOrderId(orderId);
    }
}
