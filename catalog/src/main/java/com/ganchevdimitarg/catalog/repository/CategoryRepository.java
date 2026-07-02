package com.ganchevdimitarg.catalog.repository;

import com.ganchevdimitarg.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    Optional<Category> findByName(String categoryName);
    void deleteByName(String categoryName);
}
