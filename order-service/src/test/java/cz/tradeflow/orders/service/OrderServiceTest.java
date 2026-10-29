package cz.tradeflow.orders.service;

import cz.tradeflow.orders.catalog.ProductCatalog;
import cz.tradeflow.orders.catalog.ProductInfo;
import cz.tradeflow.orders.dto.CreateOrderItemRequest;
import cz.tradeflow.orders.dto.CreateOrderRequest;
import cz.tradeflow.orders.dto.OrderResponse;
import cz.tradeflow.orders.entity.Order;
import cz.tradeflow.orders.entity.OrderItem;
import cz.tradeflow.orders.entity.enums.OrderStatus;
import cz.tradeflow.orders.event.OrderCreatedEvent;
import cz.tradeflow.orders.exception.OrderNotFoundException;
import cz.tradeflow.orders.exception.ProductNotFoundException;
import cz.tradeflow.orders.mapper.OrderMapper;
import cz.tradeflow.orders.outbox.OutboxEvent;
import cz.tradeflow.orders.outbox.OutboxEventRepository;
import cz.tradeflow.orders.repository.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductCatalog productCatalog;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private final OrderMapper orderMapper = new OrderMapper();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                orderMapper,
                productCatalog,
                outboxEventRepository,
                jsonMapper
        );
    }

    @Test
    void createOrder_shouldCreatePendingOrderAndSaveOutboxEvent() {

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new CreateOrderItemRequest(10L, 2))
        );

        ProductInfo product = new ProductInfo(
                10L,
                "Sony Headphones",
                new BigDecimal("7499.00")
        );

        when(productCatalog.getProduct(10L))
                .thenReturn(product);

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    ReflectionTestUtils.setField(order, "id", 42L);
                    return order;
                });

        OrderResponse response = orderService.createOrder(request);

        assertEquals(42L, response.id());
        assertEquals(OrderStatus.PENDING, response.status());
        assertEquals(1, response.items().size());

        assertEquals(10L, response.items().get(0).productId());
        assertEquals("Sony Headphones", response.items().get(0).productName());
        assertEquals(2, response.items().get(0).quantity());
        assertEquals(
                0,
                new BigDecimal("7499.00")
                        .compareTo(response.items().get(0).unitPrice())
        );

        ArgumentCaptor<Order> orderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository).save(orderCaptor.capture());

        Order savedOrder = orderCaptor.getValue();

        assertEquals(OrderStatus.PENDING, savedOrder.getStatus());
        assertEquals(1, savedOrder.getItems().size());
        assertSame(savedOrder, savedOrder.getItems().get(0).getOrder());

        ArgumentCaptor<OutboxEvent> outboxCaptor =
                ArgumentCaptor.forClass(OutboxEvent.class);

        verify(outboxEventRepository).save(outboxCaptor.capture());

        OutboxEvent outboxEvent = outboxCaptor.getValue();

        assertEquals("orders.created", outboxEvent.getTopic());
        assertEquals("42", outboxEvent.getMessageKey());
        assertNull(outboxEvent.getPublishedAt());

        OrderCreatedEvent event = jsonMapper.readValue(
                outboxEvent.getPayload(),
                OrderCreatedEvent.class
        );

        assertEquals(42L, event.orderId());
        assertEquals(1, event.items().size());
        assertEquals(10L, event.items().getFirst().productId());
        assertEquals(2, event.items().getFirst().quantity());

        verify(productCatalog).getProduct(10L);
    }

    @Test
    void createOrder_shouldFailWhenProductNotFound() {

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new CreateOrderItemRequest(999L, 1))
        );

        when(productCatalog.getProduct(999L))
                .thenThrow(new ProductNotFoundException(999L));

        assertThrows(
                ProductNotFoundException.class,
                () -> orderService.createOrder(request)
        );

        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void getOrder_shouldReturnOrder() {

        Long orderId = 1L;

        Order order = new Order();
        order.addItem(new OrderItem(
                10L,
                "Sony Headphones",
                2,
                new BigDecimal("7499.00")
        ));

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrder(orderId);

        assertEquals(OrderStatus.PENDING, response.status());
        assertEquals(1, response.items().size());
        assertEquals("Sony Headphones", response.items().get(0).productName());
        assertEquals(2, response.items().get(0).quantity());

        verify(orderRepository).findById(orderId);
    }

    @Test
    void getOrder_shouldThrowWhenNotFound() {

        Long orderId = 999L;

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.empty());

        OrderNotFoundException exception = assertThrows(
                OrderNotFoundException.class,
                () -> orderService.getOrder(orderId)
        );

        assertEquals("Order not found: 999", exception.getMessage());

        verify(orderRepository).findById(orderId);
    }
}