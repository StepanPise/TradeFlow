package cz.tradeflow.inventory.mapper;

import cz.tradeflow.inventory.dto.ProductResponse;
import cz.tradeflow.inventory.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStockQuantity()
        );
    }
}