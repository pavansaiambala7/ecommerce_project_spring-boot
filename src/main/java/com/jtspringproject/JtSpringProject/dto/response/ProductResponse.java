package com.jtspringproject.JtSpringProject.dto.response;

import java.math.BigDecimal;

import com.jtspringproject.JtSpringProject.models.Product;

/**
 * Public view of a product.
 *
 * <p>Deliberately omits the owning {@code customer} association: serialising the
 * entity directly exposed the owning user, and with it that user's password hash.
 */
public class ProductResponse {

    private int id;
    private String name;
    private String description;
    private String image;
    private BigDecimal price;
    /** List price, or null when the product is not discounted. */
    private BigDecimal mrp;
    /** Whole-number percentage off the MRP; 0 when there is no discount. */
    private int discountPercent;
    private int quantity;
    private int weight;
    private boolean inStock;
    private String brand;
    private BigDecimal rating;
    private int ratingCount;
    private CategoryResponse category;

    public ProductResponse() {
    }

    public static ProductResponse from(Product product) {
        if (product == null) {
            return null;
        }
        ProductResponse dto = new ProductResponse();
        dto.id = product.getId();
        dto.name = product.getName();
        dto.description = product.getDescription();
        dto.image = product.getImage();
        dto.price = product.getPrice();
        dto.mrp = product.getMrp();
        dto.discountPercent = discountPercent(product.getPrice(), product.getMrp());
        dto.quantity = product.getQuantity();
        dto.weight = product.getWeight();
        dto.inStock = product.getQuantity() > 0;
        dto.brand = product.getBrand();
        dto.rating = product.getRating();
        dto.ratingCount = product.getRatingCount();
        dto.category = CategoryResponse.from(product.getCategory());
        return dto;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public int getRatingCount() {
        return ratingCount;
    }

    public void setRatingCount(int ratingCount) {
        this.ratingCount = ratingCount;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public boolean isInStock() {
        return inStock;
    }

    public void setInStock(boolean inStock) {
        this.inStock = inStock;
    }

    public CategoryResponse getCategory() {
        return category;
    }

    public void setCategory(CategoryResponse category) {
        this.category = category;
    }

    public BigDecimal getMrp() {
        return mrp;
    }

    public void setMrp(BigDecimal mrp) {
        this.mrp = mrp;
    }

    public int getDiscountPercent() {
        return discountPercent;
    }

    public void setDiscountPercent(int discountPercent) {
        this.discountPercent = discountPercent;
    }

    /**
     * The same rounding as the {@code discount_percent} column in V14, so a
     * product loaded through JPA shows the badge a search result shows.
     */
    public static int discountPercent(BigDecimal price, BigDecimal mrp) {
        if (price == null || mrp == null || mrp.signum() <= 0 || mrp.compareTo(price) <= 0) {
            return 0;
        }
        return mrp.subtract(price).multiply(BigDecimal.valueOf(100))
                .divide(mrp, 0, java.math.RoundingMode.HALF_UP).intValue();
    }
}
