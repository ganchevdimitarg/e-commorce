package com.concordeu.order.dao;

import com.concordeu.order.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemDao extends JpaRepository<Item, String> {
}
