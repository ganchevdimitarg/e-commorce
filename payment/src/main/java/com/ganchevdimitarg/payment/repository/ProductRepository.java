package com.concordeu.catalog.repository;

import com.concordeu.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findByName(String productName);
    Optional<Product> findByNameAndCategoryId(String name, UUID categoryId);

    @Query("select p from Product p where p.category.id = :categoryId")
    Page<Product> findAllByCategoryIdByPage(@Param("categoryId") UUID categoryId, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("""
            update Product p set p.description = :description, p.price = :price, \
            p.characteristics = :characteristics, p.inStock = :inStock, \
            p.version = p.version + 1 \
            where p.id = :id and p.version = :version
            """)
    int updateById(@Param("id") UUID id, @Param("description") String description,
                   @Param("price") BigDecimal price, @Param("characteristics") String characteristics,
                   @Param("inStock") boolean inStock, @Param("version") long version);

    @Modifying
    @Query("""
            update Product p set p.category.id = :categoryId, \
            p.version = p.version + 1 \
            where p.name = :name and p.version = :version
            """)
    int changeCategory(@Param("name") String name, @Param("categoryId") UUID categoryId,
                       @Param("version") long version);

    @Modifying(clearAutomatically = true)
    @Query("""
            update Product p set p.category.id = :toId, p.version = p.version + 1 \
            where p.category.id = :fromId
            """)
    int moveAllProductsToCategory(@Param("fromId") UUID fromId, @Param("toId") UUID toId);
}
