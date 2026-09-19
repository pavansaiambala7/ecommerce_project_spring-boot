package com.jtspringproject.JtSpringProject.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jtspringproject.JtSpringProject.dao.cartDao;
import com.jtspringproject.JtSpringProject.dao.cartProductDao;
import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;
import com.jtspringproject.JtSpringProject.exception.ResourceNotFoundException;
import com.jtspringproject.JtSpringProject.models.Cart;
import com.jtspringproject.JtSpringProject.models.CartProduct;
import com.jtspringproject.JtSpringProject.models.Order;
import com.jtspringproject.JtSpringProject.models.OrderItem;
import com.jtspringproject.JtSpringProject.models.Product;
import com.jtspringproject.JtSpringProject.models.ShippingAddress;
import com.jtspringproject.JtSpringProject.models.User;

/**
 * Shopping cart operations.
 *
 * <p>Every method takes the owning user id, which callers must resolve from the
 * authenticated principal. There is no way to address another customer's cart.
 */
@Service
public class cartService {

    private final cartDao cartDao;
    private final cartProductDao cartProductDao;
    private final productService productService;
    private final userService userService;
    private final OrderService orderService;
    private final AddressService addressService;

    public cartService(cartDao cartDao, cartProductDao cartProductDao, productService productService,
            userService userService, OrderService orderService, AddressService addressService) {
        this.cartDao = cartDao;
        this.cartProductDao = cartProductDao;
        this.productService = productService;
        this.userService = userService;
        this.orderService = orderService;
        this.addressService = addressService;
    }

    @Transactional
    public Cart getOrCreateCart(int userId) {
        return cartDao.findByCustomerId(userId).orElseGet(() -> {
            User user = userService.requireUserById(userId);
            return cartDao.save(new Cart(user));
        });
    }

    @Transactional(readOnly = true)
    public Cart getCart(int userId) {
        return cartDao.findByCustomerId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No cart for the current user."));
    }

    @Transactional(readOnly = true)
    public List<CartProduct> getItems(int userId) {
        return cartDao.findByCustomerId(userId)
                .map(Cart::getItems)
                .orElseGet(ArrayList::new);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotal(int userId) {
        return getItems(userId).stream()
                .map(CartProduct::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Adds a product, accumulating onto any existing line for that product.
     */
    @Transactional
    public CartProduct addItem(int userId, int productId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessRuleException("Quantity must be at least 1.");
        }
        Cart cart = getOrCreateCart(userId);
        Product product = productService.requireProduct(productId);

        CartProduct existing = cartProductDao.findByCartIdAndProductId(cart.getId(), productId).orElse(null);
        int newQuantity = existing == null ? quantity : existing.getQuantity() + quantity;

        assertStock(product, newQuantity);

        if (existing != null) {
            existing.setQuantity(newQuantity);
            return cartProductDao.save(existing);
        }
        return cartProductDao.save(new CartProduct(cart, product, quantity));
    }

    @Transactional
    public CartProduct updateQuantity(int userId, int productId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessRuleException("Quantity must be at least 1. Remove the item instead.");
        }
        Cart cart = getCart(userId);
        CartProduct item = cartProductDao.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " is not in the cart."));

        assertStock(item.getProduct(), quantity);
        item.setQuantity(quantity);
        return cartProductDao.save(item);
    }

    /**
     * Removes a product from the cart.
     *
     * <p>The line is removed from the cart's own collection rather than deleted
     * directly. {@code Cart.items} cascades ALL, so while the loaded cart still
     * holds the line, a delete of that row is undone by the cascade at flush
     * time: the API answered "Item removed", the row stayed, and the Delete
     * button in the cart appeared to do nothing. Taking it out of the
     * collection lets orphanRemoval do the deletion, which cannot be undone by
     * the parent.
     */
    @Transactional
    public void removeItem(int userId, int productId) {
        Cart cart = getCart(userId);
        CartProduct item = cart.getItems().stream()
                .filter(line -> line.getProduct().getId() == productId)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " is not in the cart."));

        cart.getItems().remove(item);
        cartDao.save(cart);
    }

    /** Empties the cart. Same reasoning as {@link #removeItem}. */
    @Transactional
    public void clear(int userId) {
        cartDao.findByCustomerId(userId).ifPresent(cart -> {
            cart.getItems().clear();
            cartDao.save(cart);
        });
    }

    /**
     * Converts the cart into an order and empties it.
     *
     * <p>Stock validation and decrement happen inside {@code OrderService} under a
     * row lock, so the check here is only to fail fast with a clearer message.
     *
     * <p>The address must belong to the caller. It is resolved before anything
     * else so a bad address id fails without touching stock.
     */
    @Transactional
    public Order checkout(int userId, int addressId) {
        ShippingAddress shipping = addressService.requireOwned(userId, addressId).toShippingAddress();
        Cart cart = getCart(userId);
        List<CartProduct> cartItems = cart.getItems();

        if (cartItems.isEmpty()) {
            throw new BusinessRuleException("Cannot check out an empty cart.");
        }

        List<OrderItem> orderItems = new ArrayList<>();
        for (CartProduct cartItem : cartItems) {
            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItems.add(orderItem);
        }

        Order order = orderService.createOrder(userId, orderItems, shipping);
        // Emptied through the cart, not by deleting the rows: see removeItem.
        cart.getItems().clear();
        cartDao.save(cart);
        return order;
    }

    private void assertStock(Product product, int requested) {
        if (product.getQuantity() < requested) {
            throw new BusinessRuleException("Only " + product.getQuantity() + " of "
                    + product.getName() + " available.");
        }
    }
}
