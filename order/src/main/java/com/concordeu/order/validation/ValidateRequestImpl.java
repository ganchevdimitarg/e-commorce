package com.concordeu.order.validation;

import com.concordeu.order.dto.OrderDto;
import com.concordeu.order.excaption.InvalidRequestDataException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ValidateRequestImpl implements ValidateRequest {

    @Override
    public boolean validateRequest(OrderDto orderDto) {
        return isValidUsername(orderDto.username()) &&
                isValidProductName(orderDto.productName()) &&
                isValidQuantity(orderDto.quantity());
    }

    private boolean isValidUsername(String username) {
        if (username.isBlank()) {
            log.warn("Username code is not correct: {}.", username);
            throw new InvalidRequestDataException(
                    String.format("Username code is not correct: %s.", username));
        }
        return true;
    }

    private boolean isValidProductName(String productName) {
        if (productName.isBlank()) {
            log.warn("Product name code is not correct: {}", productName);
            throw new InvalidRequestDataException(
                    String.format("Product name code is not correct: %s", productName));
        }
        return true;
    }


    private boolean isValidQuantity(long quantity) {
        if (quantity <= 0) {
            log.warn("Quantity code is not correct: {}.", quantity);
            throw new InvalidRequestDataException(
                    String.format("Quantity code is not correct: %s.", quantity));
        }
        return true;
    }
}
