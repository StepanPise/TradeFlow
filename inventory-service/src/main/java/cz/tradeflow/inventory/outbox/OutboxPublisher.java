package cz.tradeflow.inventory.outbox;

import cz.tradeflow.inventory.event.ReservationResultPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ReservationResultPublisher reservationResultPublisher;

    @Scheduled(fixedDelayString = "${outbox.poll-delay-ms:1000}")
    public void publishPendingEvents() {

        var pendingEvents = outboxEventRepository
                .findTop100ByPublishedAtIsNullOrderByIdAsc();

        for (OutboxEvent event : pendingEvents) {
            try {
                reservationResultPublisher.publish(
                        event.getTopic(),
                        event.getMessageKey(),
                        event.getPayload()
                ).get(20, TimeUnit.SECONDS);

                event.markPublished();
                outboxEventRepository.save(event);

                log.info(
                        "Published outbox event {} to {}",
                        event.getId(),
                        event.getTopic()
                );

            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();

                log.warn(
                        "Outbox publisher interrupted",
                        exception
                );
                return;

            } catch (ExecutionException |
                     TimeoutException |
                     RuntimeException exception) {

                log.error(
                        "Failed to publish or mark outbox event {}",
                        event.getId(),
                        exception
                );
                return;
            }
        }
    }
}
