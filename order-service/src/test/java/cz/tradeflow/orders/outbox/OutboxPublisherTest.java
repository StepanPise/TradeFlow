package cz.tradeflow.orders.outbox;

import cz.tradeflow.orders.event.OrderEventPublisher;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private OrderEventPublisher publisher;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @Test
    void shouldMarkEventPublishedAfterKafkaSuccess() {
        OutboxEvent event = new OutboxEvent(
                "orders.created", "42", "{\"orderId\":42}"
        );

        when(repository.findTop100ByPublishedAtIsNullOrderByIdAsc())
                .thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> future =
                CompletableFuture.completedFuture(null);

        when(publisher.publish(
                event.getTopic(),
                event.getMessageKey(),
                event.getPayload()
        )).thenReturn(future);

        outboxPublisher.publishPendingEvents();

        assertNotNull(event.getPublishedAt());
        verify(repository).save(event);
    }

    @Test
    void shouldKeepEventPendingWhenKafkaFails() {
        OutboxEvent event = new OutboxEvent(
                "orders.created", "42", "{\"orderId\":42}"
        );

        when(repository.findTop100ByPublishedAtIsNullOrderByIdAsc())
                .thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> future =
                new CompletableFuture<>();

        future.completeExceptionally(
                new KafkaException("Kafka unavailable")
        );

        when(publisher.publish(
                event.getTopic(),
                event.getMessageKey(),
                event.getPayload()
        )).thenReturn(future);

        outboxPublisher.publishPendingEvents();

        assertNull(event.getPublishedAt());
        verify(repository, never()).save(any(OutboxEvent.class));
    }
}