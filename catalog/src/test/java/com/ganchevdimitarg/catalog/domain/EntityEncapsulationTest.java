package com.ganchevdimitarg.catalog.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class EntityEncapsulationTest {

    @Test
    void should_returnDefensiveCopy_fromCategoryProducts() {
        Category category = new Category();
        List<Product> backing = new ArrayList<>();
        backing.add(new Product());
        category.setProducts(backing);

        List<Product> exposed = category.getProducts();

        assertThatThrownBy(() -> exposed.add(new Product()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(category.getProducts()).hasSize(1);
    }

    @Test
    void should_returnDefensiveCopy_fromProductComments() {
        Product product = new Product();
        List<Comment> backing = new ArrayList<>();
        backing.add(new Comment());
        product.setComments(backing);

        List<Comment> exposed = product.getComments();

        assertThatThrownBy(() -> exposed.add(new Comment()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(product.getComments()).hasSize(1);
    }
}
