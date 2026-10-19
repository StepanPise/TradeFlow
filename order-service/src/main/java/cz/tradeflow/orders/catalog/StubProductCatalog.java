package cz.tradeflow.orders.catalog;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class StubProductCatalog implements ProductCatalog {

    private static final Map<Long, ProductInfo> PRODUCTS = Map.of(
            10L, new ProductInfo(
                    10L,
                    "Sony Headphones",
                    new BigDecimal("7499.00")
            ),
            11L, new ProductInfo(
                    11L,
                    "Logitech Mouse",
                    new BigDecimal("2499.00")
            ),
            12L, new ProductInfo(
                    12L,
                    "Samsung SSD",
                    new BigDecimal("1899.00")
            )
    );

    @Override
    public ProductInfo getProduct(Long productId) {
        ProductInfo product = PRODUCTS.get(productId);

        if (product == null) {
            throw new IllegalArgumentException(
                    "Product not found: " + productId
            );
        }

        return product;
    }
}