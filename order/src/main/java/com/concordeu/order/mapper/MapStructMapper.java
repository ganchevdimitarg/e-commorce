package com.concordeu.order.mapper;

import com.concordeu.order.domain.Order;
import com.concordeu.order.dto.OrderDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MapStructMapper {
    Order mapOrderDtoToOrder (OrderDto notificationDto);
    OrderDto mapOrderToOrderDto (Order notification);
}
