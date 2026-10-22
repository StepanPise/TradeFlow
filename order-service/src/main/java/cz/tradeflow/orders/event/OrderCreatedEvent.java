package cz.tradeflow.orders.event;

import java.util.List;

public record OrderCreatedEvent(
        Long orderId,
        List<OrderCreatedItem> items
) {
    public record OrderCreatedItem(
            Long productId,
            int quantity
    ) {}
}