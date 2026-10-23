package com.ganchevdimitarg.catalog.repository;

import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.dto.product.ProductCursor;
import com.ganchevdimitarg.catalog.dto.product.ProductSearchCriteria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Criteria implementation of the keyset page read.
 *
 * <p>It deliberately does not use {@code findAll(Specification, Pageable)}: that would run a
 * count query, and a cursor page needs no total. The filters are the same
 * {@link ProductSpecifications} predicates as offset search; the category is join-fetched so
 * mapping a page does not issue a select per row.
 * {@code @SQLRestriction} on {@link Product} keeps soft-deleted rows out of the scan.
 */
@Repository
@RequiredArgsConstructor
public class ProductCursorRepositoryImpl implements ProductCursorRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<Product> findPage(ProductSearchCriteria criteria, UUID categoryId,
                                  ProductCursor position, String direction, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Product> cq = cb.createQuery(Product.class);
        Root<Product> root = cq.from(Product.class);
        root.fetch("category", JoinType.LEFT);

        Path<Instant> createdAt = root.get("createdAt");
        Path<UUID> id = root.get("id");

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(ProductSpecifications.matching(criteria, categoryId).toPredicate(root, cq, cb));
        if (position != null) {
            predicates.add(after(cb, createdAt, id, position, "desc".equals(direction)));
        }
        cq.where(cb.and(predicates.toArray(Predicate[]::new)));
        cq.orderBy("desc".equals(direction)
                ? List.of(cb.desc(createdAt), cb.desc(id))
                : List.of(cb.asc(createdAt), cb.asc(id)));

        TypedQuery<Product> query = em.createQuery(cq).setMaxResults(limit);
        return query.getResultList();
    }

    /**
     * Strictly after the position in the requested direction; the id breaks createdAt ties so
     * equal timestamps cannot repeat or skip rows between pages.
     */
    private static Predicate after(CriteriaBuilder cb, Path<Instant> createdAt, Path<UUID> id,
                                   ProductCursor position, boolean descending) {
        Predicate strictlyAfter = descending
                ? cb.lessThan(createdAt, position.createdAt())
                : cb.greaterThan(createdAt, position.createdAt());
        Predicate idAfter = descending
                ? cb.lessThan(id, position.id())
                : cb.greaterThan(id, position.id());
        return cb.or(strictlyAfter, cb.and(cb.equal(createdAt, position.createdAt()), idAfter));
    }
}
