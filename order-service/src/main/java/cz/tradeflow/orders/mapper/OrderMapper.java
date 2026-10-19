package cz.tradeflow.orders.mapper;

import cz.tradeflow.orders.dto.OrderItemResponse;
import cz.tradeflow.orders.dto.OrderResponse;
import cz.tradeflow.orders.entity.Order;
import cz.tradeflow.orders.entity.OrderItem;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice()
        );
    }

    public OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getItems().stream().map(this::toItemResponse).toList()
        );
    }
}