package com.concordeu.catalog.dao;

import com.concordeu.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, String> {
    Optional<Category> findByName(String categoryName);
    @Transactional
    void deleteByName(String categoryName);
}