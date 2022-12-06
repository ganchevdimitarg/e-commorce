package com.concordeu.catalog.validator;

import com.concordeu.catalog.dto.product.ProductResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class ProductDataValidator {

    public boolean validateData(ProductResponseDto productResponseDto, String name) {
        return isValidProductName(productResponseDto.name())
                && isValidDescription(productResponseDto.description())
                && isValidPrice(productResponseDto.price())
                && isValidName(name);
    }

    private boolean isValidProductName(String name) {
        if (name.isEmpty()
                || name.trim().length() < 3
                || name.trim().length() > 20){
            log.error("The name is not correct!");
            throw new IllegalArgumentException("The name is not correct!");
        }
        return true;
    }

    private boolean isValidDescription(String description) {
        if (description.isEmpty()
                || description.trim().length() < 10
                || description.trim().length() > 50){
            log.error("The description is not correct!");
            throw new IllegalArgumentException("The description is not correct!");
        }
        return true;
    }

    private boolean isValidPrice(BigDecimal price) {
        if (price == null){
            log.error("The price is not correct!");
            throw new IllegalArgumentException("The price is not correct!");
        }

        return true;
    }

    private boolean isValidName(String name) {
        if (name.isEmpty()) {
            log.error("No such name: " + name);
            throw new IllegalArgumentException("No such name: " + name);
        }
        return true;
    }
}
