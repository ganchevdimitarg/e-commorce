package com.concordeu.catalog.repository;

import com.concordeu.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, String> {
    Optional<Category> findByName(String categoryName);
    void deleteByName(String categoryName);
}
