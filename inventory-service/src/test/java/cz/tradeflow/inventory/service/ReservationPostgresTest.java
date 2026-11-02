package cz.tradeflow.inventory.service;

import cz.tradeflow.inventory.event.OrderCreatedEvent;
import cz.tradeflow.inventory.event.OrderCreatedEvent.OrderCreatedItem;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@EnabledIfEnvironmentVariable(named = "TRADEFLOW_POSTGRES_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationPostgresTest {
    static final String SCHEMA = "reservation_test_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired ReservationService service;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.url", () ->
                "jdbc:postgresql://localhost:5434/inventory_db?currentSchema=" + SCHEMA);
    }

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE outbox_events, processed_orders, products RESTART IDENTITY");
        jdbc.update("INSERT INTO products (id, name, price, stock_quantity) VALUES (1, 'First', 10, 10), (2, 'Second', 20, 2)");
    }

    @AfterAll
    void cleanup() {
        jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @Test
    void concurrentDuplicateReservesAggregatedStockOnce() throws Exception {
        var event = new OrderCreatedEvent(100L, List.of(new OrderCreatedItem(1L, 3), new OrderCreatedItem(1L, 4)));
        concurrently(event, event);
        assertEquals(3, stock());
        assertEquals(1, count("processed_orders"));
        assertEquals(1, count("outbox_events"));
        assertEquals("RESERVED", jdbc.queryForObject("SELECT status FROM processed_orders", String.class));
    }

    @Test
    void rejectsInsufficientAndMissingProductsWithoutPartialDeduction() {
        service.reserve(new OrderCreatedEvent(100L, List.of(new OrderCreatedItem(1L, 3), new OrderCreatedItem(2L, 3))));
        service.reserve(new OrderCreatedEvent(101L, List.of(new OrderCreatedItem(1L, 3), new OrderCreatedItem(999L, 1))));
        assertEquals(10, stock());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM processed_orders WHERE status = 'REJECTED'", Integer.class));
        assertEquals(2, count("outbox_events"));
    }

    @Test
    void concurrentOrdersCannotOversell() throws Exception {
        concurrently(new OrderCreatedEvent(100L, List.of(new OrderCreatedItem(1L, 7))),
                new OrderCreatedEvent(101L, List.of(new OrderCreatedItem(1L, 7))));
        assertEquals(3, stock());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM processed_orders WHERE status = 'RESERVED'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM processed_orders WHERE status = 'REJECTED'", Integer.class));
        assertEquals(2, count("outbox_events"));
    }

    @Test
    void outboxFailureRollsBackStockAndClaim() {
        jdbc.execute("ALTER TABLE outbox_events ADD CONSTRAINT fail_test CHECK (topic = 'impossible')");
        try {
            assertThrows(RuntimeException.class, () -> service.reserve(
                    new OrderCreatedEvent(100L, List.of(new OrderCreatedItem(1L, 7)))));
            assertEquals(10, stock());
            assertEquals(0, count("processed_orders"));
            assertEquals(0, count("outbox_events"));
        } finally {
            jdbc.execute("ALTER TABLE outbox_events DROP CONSTRAINT fail_test");
        }
        service.reserve(new OrderCreatedEvent(100L, List.of(new OrderCreatedItem(1L, 7))));
        assertEquals(3, stock());
    }

    void concurrently(OrderCreatedEvent first, OrderCreatedEvent second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a = executor.submit(() -> { start.await(); service.reserve(first); return null; });
            var b = executor.submit(() -> { start.await(); service.reserve(second); return null; });
            start.countDown();
            a.get(15, TimeUnit.SECONDS);
            b.get(15, TimeUnit.SECONDS);
        }
    }

    int stock() {
        return jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = 1", Integer.class);
    }

    int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
