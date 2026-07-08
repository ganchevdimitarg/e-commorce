package com.ganchevdimitarg.payment.service.impl;

import com.ganchevdimitarg.payment.dao.CardDao;
import com.ganchevdimitarg.payment.dao.CustomerDao;
import com.ganchevdimitarg.payment.domain.AppCard;
import com.ganchevdimitarg.payment.domain.AppCustomer;
import com.ganchevdimitarg.payment.dto.CardResponse;
import com.ganchevdimitarg.payment.dto.CreateCardCommand;
import com.ganchevdimitarg.payment.exception.NotFoundException;
import com.ganchevdimitarg.payment.gateway.GatewayCard;
import com.ganchevdimitarg.payment.gateway.PaymentGateway;
import com.ganchevdimitarg.payment.service.CardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cards
 * You can store multiple cards on a customer in order to charge the customer later.
 * cardId: <a href="https://stripe.com/docs/api/cards">...</a>
 *
 * <p>The owning customer is always resolved from the gateway-authenticated {@code userId}
 * (the {@code X-User-Id} header) — never a caller-supplied id — so a caller can only ever
 * register or list cards against their own customer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CardServiceImpl implements CardService {
    private final CardDao cardDao;
    private final CustomerDao customerDao;
    private final PaymentGateway paymentGateway;
    private final CardPersistence cardPersistence;

    /**
     * Registers a new card for the authenticated user's customer and links it locally. The
     * card-creation call to the provider is deliberately outside any DB transaction: it is
     * enrolled with an idempotency key so a client retry cannot register a duplicate card,
     * and persistence is delegated to {@link CardPersistence} so a DB-commit failure after a
     * successful registration cannot orphan the provider card silently — it already exists at
     * the provider under a stable key. The local customer that owns the card is resolved from
     * the authenticated user before the mutating provider call.
     *
     * @param userId         authenticated user id
     * @param command        a client-side-tokenised Stripe source token
     * @param idempotencyKey key passed through to the payment provider so retries dedupe
     * @return card id
     */
    @Override
    @PreAuthorize("hasAuthority('SCOPE_payment.write')")
    public CardResponse createCard(String userId, CreateCardCommand command, String idempotencyKey) {
        AppCustomer appCustomer = getAppCustomer(userId);

        // Provider call is OUTSIDE any DB transaction; Stripe dedupes on the idempotency key so
        // a client retry cannot register a duplicate card. The local row is written afterwards.
        GatewayCard card = paymentGateway.createCard(appCustomer.getCustomerId(), command.token(), idempotencyKey);

        cardPersistence.persistCard(card, appCustomer);

        log.info("Method createCard: Create card successful: {}", card.id());
        return new CardResponse(card.id(), appCustomer.getCustomerId());
    }

    /**
     * Lists the provider card ids belonging to the authenticated user's customer.
     *
     * @param userId authenticated user id
     * @return ids of all cards owned by the customer
     */
    @Override
    @PreAuthorize("hasAuthority('SCOPE_payment.read')")
    public Set<String> getCards(String userId) {
        AppCustomer appCustomer = getAppCustomer(userId);
        return paymentGateway.listCardIds(appCustomer.getCustomerId());
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_payment.read')")
    public Set<String> getCustomerCards(String userId) {
        return cardDao.findAppCardsByCustomerId(getAppCustomer(userId).getCustomerId())
                .stream()
                .map(AppCard::getCardId)
                .collect(Collectors.toSet());
    }

    private AppCustomer getAppCustomer(String userId) {
        return customerDao.findByUsername(userId).orElseThrow(() -> {
            log.warn("Customer for the authenticated user does not exist in db customers");
            return new NotFoundException("Customer", userId);
        });
    }
}
