package com.concordeu.catalog.domain;

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
}
