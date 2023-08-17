package com.concordeu.catalog.repositories;

import com.concordeu.catalog.entities.Category;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    @EntityGraph(value = "graph-catalog-category")
    Optional<Category> findByName(String categoryName);

    void deleteByName(String categoryName);
}
