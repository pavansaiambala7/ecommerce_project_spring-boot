package com.jtspringproject.JtSpringProject.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.jtspringproject.JtSpringProject.models.Cart;
import com.jtspringproject.JtSpringProject.models.CartProduct;

public class CartResponse {

    private int cartId;
    private List<Item> items;
    private BigDecimal total;
    private int itemCount;

    public CartResponse() {
    }

    public static CartResponse from(Cart cart) {
        CartResponse dto = new CartResponse();
        dto.cartId = cart.getId();
        dto.items = cart.getItems().stream().map(Item::from).toList();
        dto.total = cart.getItems().stream()
                .map(CartProduct::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.itemCount = cart.getItems().stream().mapToInt(CartProduct::getQuantity).sum();
        return dto;
    }

    public int getCartId() {
        return cartId;
    }

    public void setCartId(int cartId) {
        this.cartId = cartId;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public static class Item {

        private int productId;
        private String productName;
        private String image;
        private BigDecimal unitPrice;
        private int quantity;
        private BigDecimal lineTotal;
        private boolean inStock;

        public static Item from(CartProduct cartProduct) {
            Item item = new Item();
            item.productId = cartProduct.getProduct().getId();
            item.productName = cartProduct.getProduct().getName();
            item.image = cartProduct.getProduct().getImage();
            item.unitPrice = cartProduct.getProduct().getPrice();
            item.quantity = cartProduct.getQuantity();
            item.lineTotal = cartProduct.getLineTotal();
            item.inStock = cartProduct.getProduct().getQuantity() >= cartProduct.getQuantity();
            return item;
        }

        public int getProductId() {
            return productId;
        }

        public void setProductId(int productId) {
            this.productId = productId;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getLineTotal() {
            return lineTotal;
        }

        public void setLineTotal(BigDecimal lineTotal) {
            this.lineTotal = lineTotal;
        }

        public boolean isInStock() {
            return inStock;
        }

        public void setInStock(boolean inStock) {
            this.inStock = inStock;
        }
    }
}
