package com.ganchevdimitarg.order.validation;

import com.ganchevdimitarg.order.dto.OrderDto;
import com.ganchevdimitarg.order.excaption.InvalidRequestDataException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ValidateRequestImpl implements ValidateRequest {

    @Override
    public boolean validateRequest(OrderDto orderDto) {
        return isValidUsername(orderDto.username());
    }

    private boolean isValidUsername(String username) {
        if (username.isBlank()) {
            log.warn("Username code is not correct: {}.", username);
            throw new InvalidRequestDataException(
                    String.format("Username code is not correct: %s.", username));
        }
        return true;
    }
}
