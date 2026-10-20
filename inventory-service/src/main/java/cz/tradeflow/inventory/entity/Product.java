package cz.tradeflow.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    @Setter
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    @Setter
    private BigDecimal price;

    @Column(nullable = false)
    private int stockQuantity;
}
