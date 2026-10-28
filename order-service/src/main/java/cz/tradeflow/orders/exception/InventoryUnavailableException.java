package cz.tradeflow.orders.exception;

public class InventoryUnavailableException extends RuntimeException {

    public InventoryUnavailableException(Throwable cause) {
        super("Inventory service is unavailable", cause);
    }
}
