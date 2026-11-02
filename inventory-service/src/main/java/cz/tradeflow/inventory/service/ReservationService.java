package cz.tradeflow.inventory.service;

import cz.tradeflow.inventory.entity.Product;
import cz.tradeflow.inventory.event.OrderCreatedEvent;
import cz.tradeflow.inventory.event.ReservationResultEvent;
import cz.tradeflow.inventory.event.ReservationResultEvent.Status;
import cz.tradeflow.inventory.outbox.OutboxEvent;
import cz.tradeflow.inventory.outbox.OutboxEventRepository;
import cz.tradeflow.inventory.repository.ProcessedOrderRepository;
import cz.tradeflow.inventory.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class ReservationService {
    private final ProductRepository products;
    private final ProcessedOrderRepository processedOrders;
    private final OutboxEventRepository outbox;
    private final JsonMapper jsonMapper;

    @Transactional
    public void reserve(OrderCreatedEvent event) {
        if (event.orderId() == null || event.items() == null || event.items().isEmpty()) {
            throw new IllegalArgumentException("Order id and items are required");
        }
        Map<Long, Long> quantities = new TreeMap<>();
        for (var item : event.items()) {
            if (item == null || item.productId() == null || item.quantity() <= 0) {
                throw new IllegalArgumentException("Product id and positive quantity are required");
            }
            quantities.merge(item.productId(), (long) item.quantity(), Math::addExact);
        }
        if (!processedOrders.claim(event.orderId())) {
            return;
        }

        Map<Long, Product> locked = new TreeMap<>();
        for (Long id : quantities.keySet()) {
            products.findByIdForUpdate(id).ifPresent(product -> locked.put(id, product));
        }

        boolean available = quantities.entrySet().stream().allMatch(entry -> locked.containsKey(entry.getKey())
                && locked.get(entry.getKey()).getStockQuantity() >= entry.getValue());

        Status status = available ? Status.RESERVED : Status.REJECTED;

        if (available) {
            quantities.forEach((id, quantity) -> locked.get(id).reserve(Math.toIntExact(quantity)));
        }

        processedOrders.complete(event.orderId(), status);

        outbox.save(new OutboxEvent("inventory.reservation-results", event.orderId().toString(),
                jsonMapper.writeValueAsString(new ReservationResultEvent(event.orderId(), status))));
    }
}
