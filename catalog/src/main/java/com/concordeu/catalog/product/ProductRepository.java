package com.concordeu.catalog.product;

import com.concordeu.catalog.category.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {
    Optional<Product> findByName(String productName);

    List<Product> findAllByCategoryOrderByNameAsc(Category category);

    List<Product> findAll();
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.description= :description, p.price= :price, p.characteristics= :characteristics, p.inStock= :inStock where p.name= :name")
    void update(@Param("name") String name, @Param("description") String description, @Param("price") BigDecimal price, @Param("characteristics") String characteristics, @Param("inStock") boolean inStock);

    @Transactional
    void deleteByName(String productName);
}
