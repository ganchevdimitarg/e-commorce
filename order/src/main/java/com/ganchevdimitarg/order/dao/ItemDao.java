package com.ganchevdimitarg.order.dao;

import com.ganchevdimitarg.order.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemDao extends JpaRepository<Item, String> {
}
