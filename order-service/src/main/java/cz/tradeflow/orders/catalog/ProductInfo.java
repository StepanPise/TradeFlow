package cz.tradeflow.orders.catalog;

import java.math.BigDecimal;

public record ProductInfo(
        Long id,
        String name,
        BigDecimal price
) {}