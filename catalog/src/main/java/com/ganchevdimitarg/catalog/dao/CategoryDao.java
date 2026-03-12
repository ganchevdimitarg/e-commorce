package com.ganchevdimitarg.catalog.dao;

import com.ganchevdimitarg.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface CategoryDao extends JpaRepository<Category, String> {
    Optional<Category> findByName(String categoryName);
    @Transactional
    void deleteByName(String categoryName);
}
