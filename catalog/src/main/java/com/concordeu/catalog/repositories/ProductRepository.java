package com.concordeu.catalog.repositories;

import com.concordeu.catalog.entities.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @EntityGraph(value = "graph-catalog-product")
    Optional<Product> findByName(String productName);

    @Override
    Optional<Product> findById(UUID id);

    @Query(value = """
            SELECT * FROM products WHERE CATEGORY_ID = ?1
            """, nativeQuery = true)
    @EntityGraph(value = "graph-catalog-product")
    Page<Product> findAllByCategoryIdByPage(UUID categoryId, Pageable pageable);

    @Modifying
    @Query("""
            update Product p set p.description= :description, p.price= :price, p.characteristics= :characteristics, p.inStock= :inStock where p.name= :name
            """)
    void update(@Param("name") String name, @Param("description") String description, @Param("price") BigDecimal price, @Param("characteristics") String characteristics, @Param("inStock") boolean inStock);

    @Modifying
    @Query("""
            update Product p set p.category.id= :category_id where p.name= :name
            """)
    void changeCategory(@Param("name") String name, @Param("category_id") UUID category_id);

    void deleteByName(String productName);
}
 