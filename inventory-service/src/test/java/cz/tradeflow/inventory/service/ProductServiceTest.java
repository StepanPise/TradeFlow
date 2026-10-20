
package cz.tradeflow.inventory.service;

import cz.tradeflow.inventory.dto.ProductResponse;
import cz.tradeflow.inventory.entity.Product;
import cz.tradeflow.inventory.exception.ProductNotFoundException;
import cz.tradeflow.inventory.mapper.ProductMapper;
import cz.tradeflow.inventory.repository.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private final ProductMapper productMapper = new ProductMapper();

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(
                productRepository,
                productMapper
        );
    }

    @Test
    void getProduct_shouldReturnProductWhenExists() {
        Long productId = 1L;

        Product product = new Product();
        product.setName("Sony Headphones");
        product.setPrice(new BigDecimal("7499.00"));

        when(productRepository.findById(productId))
                .thenReturn(Optional.of(product));

        ProductResponse response = productService.getProduct(productId);

        assertNotNull(response);
        assertEquals("Sony Headphones", response.name());
        assertEquals(0, new BigDecimal("7499.00")
                .compareTo(response.price()));

        verify(productRepository).findById(productId);
    }

    @Test
    void getProduct_shouldThrowWhenNotFound() {
        Long productId = 999L;

        when(productRepository.findById(productId))
                .thenReturn(Optional.empty());

        assertThrows(
                ProductNotFoundException.class,
                () -> productService.getProduct(productId)
        );
    }

    @Test
    void getAllProducts_shouldReturnMappedProducts() {
        Product product1 = new Product();
        product1.setName("Sony Headphones");
        product1.setPrice(new BigDecimal("7499.00"));

        Product product2 = new Product();
        product2.setName("Logitech Mouse");
        product2.setPrice(new BigDecimal("2499.00"));

        when(productRepository.findAll())
                .thenReturn(List.of(product1, product2));

        List<ProductResponse> responses =
                productService.getAllProducts();

        assertEquals(2, responses.size());
        assertEquals("Sony Headphones", responses.get(0).name());
        assertEquals("Logitech Mouse", responses.get(1).name());

        verify(productRepository).findAll();
    }
}
