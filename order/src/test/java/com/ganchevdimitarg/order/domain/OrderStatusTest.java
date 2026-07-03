package com.ganchevdimitarg.order.domain;

import org.junit.jupiter.api.Test;

import static com.ganchevdimitarg.order.domain.OrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void should_allowForwardMoves_when_transitionIsLegal() {
        assertThat(PLACED.canTransitionTo(PAID)).isTrue();
        assertThat(PAID.canTransitionTo(SHIPPED)).isTrue();
        assertThat(SHIPPED.canTransitionTo(DELIVERED)).isTrue();
    }

    @Test
    void should_allowCancel_when_placedOrPaid() {
        assertThat(PLACED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(PAID.canTransitionTo(CANCELLED)).isTrue();
    }

    @Test
    void should_rejectCancel_when_shippedOrDelivered() {
        assertThat(SHIPPED.canTransitionTo(CANCELLED)).isFalse();
        assertThat(DELIVERED.canTransitionTo(CANCELLED)).isFalse();
    }

    @Test
    void should_rejectSkipsAndBackwardMoves_when_transitionIsIllegal() {
        assertThat(PLACED.canTransitionTo(SHIPPED)).isFalse();
        assertThat(PAID.canTransitionTo(DELIVERED)).isFalse();
        assertThat(PAID.canTransitionTo(PLACED)).isFalse();
    }

    @Test
    void should_beTerminal_when_deliveredOrCancelled() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(DELIVERED.canTransitionTo(target)).isFalse();
            assertThat(CANCELLED.canTransitionTo(target)).isFalse();
        }
    }
}
