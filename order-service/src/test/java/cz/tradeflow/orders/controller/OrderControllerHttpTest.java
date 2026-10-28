package cz.tradeflow.orders.controller;

import com.sun.net.httpserver.HttpServer;
import cz.tradeflow.orders.catalog.InventoryProductCatalog;
import cz.tradeflow.orders.entity.Order;
import cz.tradeflow.orders.event.OrderCreatedEvent;
import cz.tradeflow.orders.event.OrderEventPublisher;
import cz.tradeflow.orders.exception.GlobalExceptionHandler;
import cz.tradeflow.orders.exception.InventoryUnavailableException;
import cz.tradeflow.orders.exception.ProductNotFoundException;
import cz.tradeflow.orders.mapper.OrderMapper;
import cz.tradeflow.orders.repository.OrderRepository;
import cz.tradeflow.orders.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.client.RestClient;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class OrderControllerHttpTest {

    private static final String VALID_ORDER = """
            {"items":[{"productId":1,"quantity":2}]}
            """;

    private final OrderRepository repository = mock(OrderRepository.class);
    private final OrderEventPublisher publisher = mock(OrderEventPublisher.class);
    private final AtomicInteger inventoryRequests = new AtomicInteger();
    private HttpServer inventory;
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        inventory = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        inventory.start();
        InventoryProductCatalog catalog = new InventoryProductCatalog(
                RestClient.builder(), "http://127.0.0.1:" + inventory.getAddress().getPort());
        OrderService service = new OrderService(repository, new OrderMapper(), catalog, publisher);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = standaloneSetup(new OrderController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (inventory != null) {
            inventory.stop(0);
        }
        if (validator != null) {
            validator.close();
        }
    }

    @Test
    void createOrder_shouldUseProductFromHttpAndPublishEvent() throws Exception {
        inventoryResponds(200, """
                {"id":1,"name":"Headphones","price":7499.00,"stockQuantity":12}
                """);
        when(repository.save(any(Order.class))).thenAnswer(call -> {
            Order order = call.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 42L);
            return order;
        });

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("Headphones"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(7499.00));

        ArgumentCaptor<OrderCreatedEvent> event = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(publisher).publishOrderCreated(event.capture());
        assertEquals(42L, event.getValue().orderId());
        assertEquals(1, event.getValue().items().size());
        assertEquals(1L, event.getValue().items().getFirst().productId());
        assertEquals(2, event.getValue().items().getFirst().quantity());
        assertEquals(1, inventoryRequests.get());
    }

    @Test
    void createOrder_shouldMapInventory404ToProductNotFound() throws Exception {
        inventoryResponds(404, "{\"internalDetail\":\"not exposed\"}");

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Product not found: 1"))
                .andExpect(result -> assertInstanceOf(
                        ProductNotFoundException.class, result.getResolvedException()));

        assertEquals(1, inventoryRequests.get());
        verifyNoInteractions(repository, publisher);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void createOrder_shouldReturn503ForInventoryServerErrors(int inventoryStatus) throws Exception {
        inventoryResponds(inventoryStatus, "{\"internalDetail\":\"not exposed\"}");

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("Inventory service is unavailable"))
                .andExpect(result -> assertInstanceOf(
                        InventoryUnavailableException.class, result.getResolvedException()));

        assertEquals(1, inventoryRequests.get());
        verifyNoInteractions(repository, publisher);
    }

    @Test
    void createOrder_shouldReturn503WhenInventoryCannotBeReached() throws Exception {
        inventory.stop(0);
        inventory = null;

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("Inventory service is unavailable"))
                .andExpect(result -> assertInstanceOf(
                        InventoryUnavailableException.class, result.getResolvedException()));

        verifyNoInteractions(repository, publisher);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"items\":[null]}",
            "{\"items\":[]}",
            "{\"items\":null}",
            "{}",
            "{\"items\":[{\"productId\":1,\"quantity\":0}]}"
    })
    void createOrder_shouldRejectInvalidItemsBeforeCallingInventory(String request) throws Exception {
        inventoryResponds(200, "{}");

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertInstanceOf(
                        MethodArgumentNotValidException.class,
                        result.getResolvedException()));

        assertEquals(0, inventoryRequests.get());
        verifyNoInteractions(repository, publisher);
    }

    @Test
    void getOrder_shouldKeepExisting404Response() throws Exception {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/orders/42"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Order not found: 42"));
    }

    private void inventoryResponds(int status, String body) {
        inventory.createContext("/api/products/1", exchange -> {
            inventoryRequests.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            } finally {
                exchange.close();
            }
        });
    }
}
