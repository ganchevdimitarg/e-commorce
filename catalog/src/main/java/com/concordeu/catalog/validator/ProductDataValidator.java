package com.concordeu.catalog.validator;

import com.concordeu.catalog.dto.ProductDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class ProductDataValidator {

    public boolean validateData(ProductDTO productDTO, String name) {
        return isValidProductName(productDTO.getName())
               && isValidDescription(productDTO.getDescription())
               && isValidName(name)
               && isValidPrice(productDTO.getPrice());
    }

    private boolean isValidPrice(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            log.warn("The price is not correct!");
            throw new IllegalArgumentException("The price is not correct!");
        }
        return true;
    }

    private boolean isValidProductName(String name) {
        if (name == null
            || name.isBlank()
            || name.trim().length() < 3
            || name.trim().length() > 20) {
            log.warn("The name is not correct!");
            throw new IllegalArgumentException("The name is not correct!");
        }
        return true;
    }

    private boolean isValidDescription(String description) {
        if (description == null
            || description.isEmpty()
            || description.trim().length() < 10
            || description.trim().length() > 50) {
            log.warn("The description is not correct!");
            throw new IllegalArgumentException("The description is not correct!");
        }
        return true;
    }

    private boolean isValidName(String name) {
        if (name.isBlank()) {
            log.warn("No such name: " + name);
            throw new IllegalArgumentException("No such name: " + name);
        }
        return true;
    }
}
