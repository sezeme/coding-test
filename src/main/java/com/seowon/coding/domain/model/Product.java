package com.seowon.coding.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Product name is required")
    private String name;
    
    private String description;
    
    @Positive(message = "Price must be positive")
    private BigDecimal price;
    
    private int stockQuantity;
    
    private String category;
    
    // Business logic
    public boolean isInStock() {
        return stockQuantity > 0;
    }
    
    public void decreaseStock(int quantity) {
        if (quantity > stockQuantity) {
            throw new IllegalArgumentException("Not enough stock available");
        }
        stockQuantity -= quantity;
    }
    
    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        stockQuantity += quantity;
    }

    static RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    static int SCALE = 2;
    static BigDecimal VAT = BigDecimal.valueOf(1.1);

    public void setPrice(BigDecimal price) {
        this.price = price.setScale(SCALE, ROUNDING_MODE);
    }

    public void changePrice(BigDecimal percentage, boolean includeTax) {
        BigDecimal base = this.getPrice() == null ? BigDecimal.ZERO : this.getPrice();
        BigDecimal dividedPercentage = percentage.divide(BigDecimal.valueOf(100), ROUNDING_MODE);
        BigDecimal changed = base.add(base.multiply(dividedPercentage)); // 부동소수점 오류 가능
        if (includeTax) {
            changed = changed.multiply(VAT); // 하드코딩 VAT 10%, 지역/카테고리별 규칙 미반영
        }
        // 임의 반올림: 일관되지 않은 스케일/반올림 모드
        setPrice(changed);
    }
}