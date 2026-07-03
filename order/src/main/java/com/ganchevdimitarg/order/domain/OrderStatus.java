package com.ganchevdimitarg.order.domain;

/**
 * Order lifecycle states and the sole authority for legal transitions.
 * DELIVERED and CANCELLED are terminal; CANCELLED is reachable only from
 * PLACED or PAID.
 */
public enum OrderStatus {
    PLACED, PAID, SHIPPED, DELIVERED, CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PLACED             -> target == PAID || target == CANCELLED;
            case PAID               -> target == SHIPPED || target == CANCELLED;
            case SHIPPED            -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
