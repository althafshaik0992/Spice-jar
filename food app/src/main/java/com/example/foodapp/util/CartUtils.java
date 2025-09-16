package com.example.foodapp.util;

public class CartUtils {

    /**
     * Calculates the total number of items in the cart by summing the quantities of all CartItems.
     * @param cart The Cart object.
     * @return The total number of items.
     */
    public static int getCartTotalQuantity(Cart cart) {
        if (cart == null || cart.getItems() == null) {
            return 0;
        }
        return cart.getItems().stream()
                .mapToInt(CartItem::getQty)
                .sum();
    }
}
