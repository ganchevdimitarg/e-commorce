package com.concordeu.catalog.dao;

import com.concordeu.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

public interface ProductDao extends JpaRepository<Product, String> {

    @EntityGraph(value = "graph-catalog-product")
    Optional<Product> findByName(String productName);

    @Query(value = """
            SELECT * FROM products WHERE CATEGORY_ID = ?1
            """, nativeQuery = true)
    @EntityGraph(value = "graph-catalog-product")
    Page<Product> findAllByCategoryIdByPage(String categoryId, PageRequest pageRequest);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update Product p set p.description= :description, p.price= :price, p.characteristics= :characteristics, p.inStock= :inStock where p.name= :name
            """)
    void update(@Param("name") String name, @Param("description") String description, @Param("price") BigDecimal price, @Param("characteristics") String characteristics, @Param("inStock") boolean inStock);

    @Transactional
    @Modifying
    @Query("""
            update Product p set p.category.id= :category_id where p.name= :name
            """)
    void changeCategory(@Param("name") String name, @Param("category_id") String category_id);

    @Transactional
    void deleteByName(String productName);
}
 