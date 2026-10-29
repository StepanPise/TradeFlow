package cz.tradeflow.orders.service;

import cz.tradeflow.orders.catalog.ProductCatalog;
import cz.tradeflow.orders.catalog.ProductInfo;
import cz.tradeflow.orders.dto.CreateOrderItemRequest;
import cz.tradeflow.orders.dto.CreateOrderRequest;
import cz.tradeflow.orders.dto.OrderResponse;
import cz.tradeflow.orders.entity.Order;
import cz.tradeflow.orders.entity.OrderItem;
import cz.tradeflow.orders.event.OrderCreatedEvent;
import cz.tradeflow.orders.exception.OrderNotFoundException;
import cz.tradeflow.orders.mapper.OrderMapper;
import cz.tradeflow.orders.outbox.OutboxEvent;
import cz.tradeflow.orders.outbox.OutboxEventRepository;
import cz.tradeflow.orders.repository.OrderRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ProductCatalog productCatalog;
    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {

        Order order = new Order();

        for (CreateOrderItemRequest itemRequest : request.items()){

            ProductInfo product = productCatalog.getProduct(
                    itemRequest.productId()
            );

            OrderItem item = new OrderItem(
                    product.id(),
                    product.name(),
                    itemRequest.quantity(),
                    product.price()
            );

            order.addItem(item);
        }

        Order savedOrder = orderRepository.save(order);

        OrderCreatedEvent event = new OrderCreatedEvent(
                savedOrder.getId(),
                savedOrder.getItems().stream()
                        .map(item -> new OrderCreatedEvent.OrderCreatedItem(
                                item.getProductId(),
                                item.getQuantity()
                        ))
                        .toList()
        );

        String payload = jsonMapper.writeValueAsString(event);

        OutboxEvent outboxEvent = new OutboxEvent(
                "orders.created",
                savedOrder.getId().toString(),
                payload
        );

        outboxEventRepository.save(outboxEvent);

        return orderMapper.toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        return orderMapper.toResponse(order);
    }

    }
