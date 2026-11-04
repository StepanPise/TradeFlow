package cz.tradeflow.orders.event;

import cz.tradeflow.orders.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ReservationResultListener {
    private final JsonMapper jsonMapper;
    private final OrderService orderService;

    @KafkaListener(
            topics = "inventory.reservation-results",
            groupId = "order-service"
    )
    public void handleReservationResult(String message) {
        orderService.applyReservationResult(jsonMapper.readValue(message, ReservationResultEvent.class));
    }
}
