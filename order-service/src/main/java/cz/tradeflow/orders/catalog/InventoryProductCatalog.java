package cz.tradeflow.orders.catalog;

import cz.tradeflow.orders.exception.InventoryUnavailableException;
import cz.tradeflow.orders.exception.ProductNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
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
        try {
            return restClient.get()
                    .uri("/api/products/{id}", productId)
                    .retrieve()
                    .body(ProductInfo.class);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ProductNotFoundException(productId);
        } catch (HttpServerErrorException | ResourceAccessException exception) {
            throw new InventoryUnavailableException(exception);
        }
    }
}
