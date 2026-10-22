package cz.tradeflow.orders.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryProductCatalog implements ProductCatalog {

    private final RestClient restClient;

    public InventoryProductCatalog(RestClient.Builder builder,
            @Value("${inventory.service.url}") String inventoryUrl
    ) {
        this.restClient = builder
                .baseUrl(inventoryUrl)
                .build();
    }

    @Override
    public ProductInfo getProduct(Long productId) {
        return restClient.get()
                .uri("/api/products/{id}", productId)
                .retrieve()
                .body(ProductInfo.class);
    }
}
