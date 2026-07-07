package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.AbstractIntegrationTest;
import com.ganchevdimitarg.payment.dto.CreateChargeCommand;
import com.ganchevdimitarg.payment.service.ChargeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regresses method security on {@link ChargeServiceImpl}: {@code @PreAuthorize}
 * enforcement only happens behind a Spring proxy inside an application context, so
 * this must run as a full {@code @SpringBootTest} IT rather than a plain-Mockito unit
 * test.
 */
class ChargeAuthorizationIT extends AbstractIntegrationTest {

    @Autowired
    private ChargeService chargeService;

    @Test
    @WithMockUser(authorities = "SCOPE_payment.read")
    void should_denyWrite_when_callerHasReadScopeOnly() {
        CreateChargeCommand command = new CreateChargeCommand(
                "john.doe", "cus_1", "card_1", 500L, "usd", "john@doe.com");

        assertThatThrownBy(() -> chargeService.createCharge(command, "idem-1"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
