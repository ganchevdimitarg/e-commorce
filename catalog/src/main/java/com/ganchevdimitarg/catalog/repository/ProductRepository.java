package com.ganchevdimitarg.catalog.repository;

import com.ganchevdimitarg.catalog.domain.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    Optional<Product> findByName(String productName);

    // Rating recalculation reads and writes the product row, so comment mutations take a
    // row lock before touching either aggregate. @SQLRestriction still applies: a
    // soft-deleted product is intentionally not lockable.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.name = :name")
    Optional<Product> findByNameForUpdate(@Param("name") String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") UUID id);
    Optional<Product> findByNameAndCategoryId(String name, UUID categoryId);
    boolean existsByCategory_Id(UUID categoryId);
    List<Product> findAllByCategory_Id(UUID categoryId);

    // One query for the whole batch; category is joined, comments stay lazy
    // (@BatchSize) so a collection fetch join cannot duplicate rows.
    @Override
    @EntityGraph(attributePaths = "category")
    List<Product> findAllById(Iterable<UUID> ids);

    // category is join-fetched to avoid a select per row on page reads; comments stay
    // lazy (@BatchSize on the entity) because a collection fetch join cannot paginate
    // in SQL.
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Pageable pageable);

    // Search pages carry the same eager category as plain page reads; without the graph a
    // response mapping would issue one select per distinct category on the page.
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query("select p from Product p where p.category.id = :categoryId")
    Page<Product> findAllByCategoryIdByPage(@Param("categoryId") UUID categoryId, Pageable pageable);

    long countByCategory_Id(UUID categoryId);

    interface CategoryProductCount {
        UUID getCategoryId();

        long getProductCount();
    }

    @Query("""
            select p.category.id as categoryId, count(p) as productCount
            from Product p
            where p.category.id in :categoryIds
            group by p.category.id
            """)
    List<CategoryProductCount> countProductsByCategoryIds(@Param("categoryIds") Collection<UUID> categoryIds);

    @Modifying(clearAutomatically = true)
    @Query("""
            update Product p set p.description = :description, p.price = :price, \
            p.characteristics = :characteristics, p.inStock = :inStock, \
            p.updatedAt = CURRENT_TIMESTAMP, p.updatedBy = :updatedBy, \
            p.version = p.version + 1 \
            where p.id = :id and p.version = :version
            """)
    int updateById(@Param("id") UUID id, @Param("description") String description,
                   @Param("price") BigDecimal price, @Param("characteristics") String characteristics,
                   @Param("inStock") boolean inStock, @Param("version") long version,
                   @Param("updatedBy") String updatedBy);

    @Modifying
    @Query("""
            update Product p set p.category.id = :categoryId, \
            p.updatedAt = CURRENT_TIMESTAMP, p.updatedBy = :updatedBy, \
            p.version = p.version + 1 \
            where p.name = :name and p.version = :version
            """)
    int changeCategory(@Param("name") String name, @Param("categoryId") UUID categoryId,
                       @Param("version") long version, @Param("updatedBy") String updatedBy);

    @Modifying(clearAutomatically = true)
    @Query("""
            update Product p set p.category.id = :toId, \
            p.updatedAt = CURRENT_TIMESTAMP, p.updatedBy = :updatedBy, \
            p.version = p.version + 1 \
            where p.category.id = :fromId
            """)
    int moveAllProductsToCategory(@Param("fromId") UUID fromId, @Param("toId") UUID toId,
                                  @Param("updatedBy") String updatedBy);
}
