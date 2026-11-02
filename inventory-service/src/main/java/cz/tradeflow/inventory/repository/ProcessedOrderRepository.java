package cz.tradeflow.inventory.repository;

import cz.tradeflow.inventory.event.ReservationResultEvent.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProcessedOrderRepository {
    private final JdbcTemplate jdbcTemplate;

    public boolean claim(Long orderId) {
        return jdbcTemplate.update("""
                INSERT INTO processed_orders (order_id, status) VALUES (?, 'RESERVED')
                ON CONFLICT (order_id) DO NOTHING
                """, orderId) == 1;
    }

    public void complete(Long orderId, Status status) {
        jdbcTemplate.update("UPDATE processed_orders SET status = ? WHERE order_id = ?",
                status.name(), orderId);
    }
}
