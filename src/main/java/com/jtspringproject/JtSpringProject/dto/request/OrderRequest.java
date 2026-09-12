package com.jtspringproject.JtSpringProject.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * An order request.
 *
 * <p>Note there is no {@code userId} field. The owning user is taken from the
 * authenticated principal; the previous endpoint accepted it as a query
 * parameter, letting an anonymous caller place orders against any account.
 */
public class OrderRequest {

    @NotEmpty(message = "An order must contain at least one item")
    @Valid
    private List<Item> items;

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public static class Item {

        @NotNull(message = "Product id is required")
        private Integer productId;

        @Min(value = 1, message = "Quantity must be at least 1")
        private int quantity;

        public Integer getProductId() {
            return productId;
        }

        public void setProductId(Integer productId) {
            this.productId = productId;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}
