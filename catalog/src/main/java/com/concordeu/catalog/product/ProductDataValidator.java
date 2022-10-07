package com.concordeu.catalog.product;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class ProductDataValidator {

    public boolean isValid(ProductDTO productDTO) {
        return isValidName(productDTO.getName())
                && isValidDescription(productDTO.getDescription());
    }

    private boolean isValidName(String name) {
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
}
