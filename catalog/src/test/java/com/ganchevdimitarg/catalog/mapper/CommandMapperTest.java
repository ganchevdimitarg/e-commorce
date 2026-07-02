package com.ganchevdimitarg.catalog.mapper;

import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.dto.product.CreateProductCommand;
import com.ganchevdimitarg.catalog.dto.product.ProductRequestDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class CommandMapperTest {

    private final MapStructMapper mapper = Mappers.getMapper(MapStructMapper.class);

    @Test
    void should_mapRequestAndCategory_toCreateCommand() {
        ProductRequestDto dto = new ProductRequestDto("mouse", "WiFi mouse USB", BigDecimal.ONE, true, "rgb");

        CreateProductCommand cmd = mapper.mapProductRequestToCreateCommand(dto, "PC");

        assertThat(cmd.name()).isEqualTo("mouse");
        assertThat(cmd.categoryName()).isEqualTo("PC");
        assertThat(cmd.characteristics()).isEqualTo("rgb");
    }

    @Test
    void should_mapCreateCommand_toProductEntity() {
        CreateProductCommand cmd = new CreateProductCommand("mouse", "WiFi mouse USB", BigDecimal.ONE, true, "rgb", "PC");

        Product product = mapper.mapCreateCommandToProduct(cmd);

        assertThat(product.getName()).isEqualTo("mouse");
        assertThat(product.getPrice()).isEqualByComparingTo(BigDecimal.ONE);
    }
}
