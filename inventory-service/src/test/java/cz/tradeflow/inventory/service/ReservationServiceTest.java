package cz.tradeflow.inventory.service;

import cz.tradeflow.inventory.entity.Product;
import cz.tradeflow.inventory.event.OrderCreatedEvent;
import cz.tradeflow.inventory.event.OrderCreatedEvent.OrderCreatedItem;
import cz.tradeflow.inventory.event.ReservationResultEvent.Status;
import cz.tradeflow.inventory.outbox.OutboxEvent;
import cz.tradeflow.inventory.outbox.OutboxEventRepository;
import cz.tradeflow.inventory.repository.ProcessedOrderRepository;
import cz.tradeflow.inventory.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {
    @Mock ProductRepository products;
    @Mock ProcessedOrderRepository processed;
    @Mock OutboxEventRepository outbox;
    ReservationService service;
    JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        service = new ReservationService(products, processed, outbox, mapper);
    }

    @Test
    void reservesAggregatedQuantitiesAndWritesResult() {
        Product product = product(10);
        when(processed.claim(1L)).thenReturn(true);
        when(products.findByIdForUpdate(2L)).thenReturn(Optional.of(product));
        service.reserve(event(new OrderCreatedItem(2L, 3), new OrderCreatedItem(2L, 4)));
        assertEquals(3, product.getStockQuantity());
        verify(products, times(1)).findByIdForUpdate(2L);
        verify(processed).complete(1L, Status.RESERVED);
        assertResult("RESERVED");
    }

    @Test
    void insufficientOrMissingProductLeavesAllStockUnchanged() {
        Product first = product(10);
        Product second = product(2);
        when(processed.claim(1L)).thenReturn(true);
        when(products.findByIdForUpdate(2L)).thenReturn(Optional.of(first));
        when(products.findByIdForUpdate(3L)).thenReturn(Optional.of(second), Optional.empty());
        var event = event(new OrderCreatedItem(2L, 3), new OrderCreatedItem(3L, 3));
        service.reserve(event);
        service.reserve(event);
        assertEquals(10, first.getStockQuantity());
        assertEquals(2, second.getStockQuantity());
        verify(processed, times(2)).complete(1L, Status.REJECTED);
        verify(outbox, times(2)).save(any());
    }

    @Test
    void duplicateDoesNotTouchStockOrCreateAnotherResult() {
        when(processed.claim(1L)).thenReturn(false);
        service.reserve(event(new OrderCreatedItem(2L, 3)));
        verifyNoInteractions(products, outbox);
        verify(processed, never()).complete(any(), any());
    }

    @Test
    void databaseFailurePropagatesWithoutRejectedResult() {
        when(processed.claim(1L)).thenReturn(true);
        when(products.findByIdForUpdate(2L)).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThrows(DataAccessResourceFailureException.class,
                () -> service.reserve(event(new OrderCreatedItem(2L, 3))));
        verifyNoInteractions(outbox);
        verify(processed, never()).complete(any(), any());
    }

    private Product product(int stock) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "stockQuantity", stock);
        return product;
    }

    private OrderCreatedEvent event(OrderCreatedItem... items) {
        return new OrderCreatedEvent(1L, List.of(items));
    }

    private void assertResult(String status) {
        var capture = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(capture.capture());
        var saved = capture.getValue();
        assertEquals("1", saved.getMessageKey());
        assertEquals("inventory.reservation-results", saved.getTopic());
        var json = mapper.readTree(saved.getPayload());
        assertEquals(1L, json.get("orderId").asLong());
        assertEquals(status, json.get("status").asText());
    }
}
