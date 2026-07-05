package com.ganchevdimitarg.order.domain;

/**
 * Order lifecycle states and the sole authority for legal transitions.
 * DELIVERED, CANCELLED and PAYMENT_FAILED are terminal; CANCELLED is reachable only from
 * PLACED or PAID. PAYMENT_FAILED marks an order whose charge could not be captured (or was
 * refunded after a post-charge failure) and is reachable only from PLACED.
 */
public enum OrderStatus {
    PLACED, PAID, SHIPPED, DELIVERED, CANCELLED, PAYMENT_FAILED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PLACED             -> target == PAID || target == CANCELLED || target == PAYMENT_FAILED;
            case PAID               -> target == SHIPPED || target == CANCELLED;
            case SHIPPED            -> target == DELIVERED;
            case DELIVERED, CANCELLED, PAYMENT_FAILED -> false;
        };
    }
}
