package cz.tradeflow.orders.event;

public record ReservationResultEvent(Long orderId, Status status) {
    public enum Status { RESERVED, REJECTED }
}
