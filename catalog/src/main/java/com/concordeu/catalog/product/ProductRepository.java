package com.concordeu.catalog.product;

import com.concordeu.catalog.category.Category;
import com.concordeu.catalog.category.CategoryDTO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {
    Optional<Product> findByName(String productName);

    List<Product> findAllByCategoryOrderByNameAsc(Category category);

}
