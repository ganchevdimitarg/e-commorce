package com.concordeu.order.validation;

import com.concordeu.order.dto.OrderDto;
import com.concordeu.order.excaption.InvalidRequestDataException;
import org.springframework.stereotype.Component;

@Component
public class ValidateRequestImpl implements ValidateRequest {

    @Override
    public boolean validateRequest(OrderDto orderDto) {
        return isValidUsername(orderDto.username()) &&
                isValidProductName(orderDto.productName()) &&
                isValidQuantity(orderDto.quantity());
    }

    private boolean isValidUsername(String username) {
        if (username.isBlank()) {
            throw new InvalidRequestDataException(
                    String.format("Username code is not correct: %s.", username));
        }
        return true;
    }

    private boolean isValidProductName(String productName) {
        if (productName.isBlank()) {
            throw new InvalidRequestDataException(
                    String.format("Product name code is not correct: %s", productName));
        }
        return true;
    }


    private boolean isValidQuantity(long quantity) {
        if (quantity <= 0) {
            throw new InvalidRequestDataException(
                    String.format("Quantity code is not correct: %s.", quantity));
        }
        return true;
    }
}
