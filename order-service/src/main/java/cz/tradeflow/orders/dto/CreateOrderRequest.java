package cz.tradeflow.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateOrderRequest(
        @NotEmpty List<@NotNull @Valid CreateOrderItemRequest> items
) {}
