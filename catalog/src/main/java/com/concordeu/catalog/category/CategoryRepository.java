package com.concordeu.catalog.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, String> {
    Optional<Category> findByName(String categoryName);

    void deleteByName(String categoryName);
}
