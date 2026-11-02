package cz.tradeflow.inventory.event;

public record ReservationResultEvent(Long orderId, Status status) {
    public enum Status { RESERVED, REJECTED }
}
