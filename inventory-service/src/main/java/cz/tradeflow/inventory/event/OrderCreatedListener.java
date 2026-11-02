package cz.tradeflow.inventory.event;

import lombok.RequiredArgsConstructor;
import cz.tradeflow.inventory.service.ReservationService;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCreatedListener {
    private final JsonMapper jsonMapper;
    private final ReservationService reservationService;

    @KafkaListener(
            topics = "orders.created",
            groupId = "inventory-service"
    )
    public void handleOrderCreated(String message) {
        reservationService.reserve(jsonMapper.readValue(message, OrderCreatedEvent.class));
    }
}
