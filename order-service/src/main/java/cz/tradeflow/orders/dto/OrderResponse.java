package cz.tradeflow.orders.dto;

import cz.tradeflow.orders.entity.enums.OrderStatus;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        Instant createdAt,
        List<OrderItemResponse> items
) {}