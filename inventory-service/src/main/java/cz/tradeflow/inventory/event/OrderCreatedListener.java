package cz.tradeflow.inventory.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderCreatedListener {

    @KafkaListener(
            topics = "orders.created",
            groupId = "inventory-service"
    )
    public void handleOrderCreated(String message) {
        log.info("Received OrderCreated: {}", message);
    }
}
