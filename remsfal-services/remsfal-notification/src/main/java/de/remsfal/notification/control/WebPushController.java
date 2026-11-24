package de.remsfal.notification.control;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.core.Response.Status;

import org.jboss.logging.Logger;

import de.remsfal.core.json.notification.WebPushSubscriptionJson;
import de.remsfal.notification.entity.dao.WebPushSubscriptionRepository;
import de.remsfal.notification.entity.dto.WebPushSubscriptionEntity;

@ApplicationScoped
public class WebPushController {

    private static final byte[] TEST_PAYLOAD = "{\"type\":\"test\"}".getBytes(StandardCharsets.UTF_8);

    @Inject
    WebPushDeliveryController delivery;

    @Inject
    WebPushSubscriptionRepository repository;

    @Inject
    Logger logger;

    @Nullable
    public String getPublicKey() {
        return delivery.getPublicKey();
    }

    public UUID saveSubscription(final UUID userId, final WebPushSubscriptionJson subscription) {
        delivery.requireEnabled();
        delivery.validate(subscription);
        return repository.saveForUser(userId, subscription)
            .orElseThrow(() -> new ClientErrorException("Subscription already belongs to another account",
                Status.CONFLICT));
    }

    public void deleteSubscription(final UUID userId, final UUID id) {
        repository.deleteOwned(userId, id);
    }

    public void deleteSubscriptions(final UUID userId) {
        repository.deleteForUser(userId);
    }

    public void sendTest(final UUID userId, final UUID id) {
        delivery.requireEnabled();
        final WebPushSubscriptionEntity subscription = repository.findOwned(userId, id)
            .orElseThrow(NotFoundException::new);
        if (!send(subscription, TEST_PAYLOAD, 60)) {
            throw new ClientErrorException("Subscription expired", Status.GONE);
        }
    }

    public void sendIssueStatusUpdate(final UUID userId, final UUID issueId) {
        if (!delivery.isEnabled()) {
            return;
        }
        final byte[] payload = ("{\"type\":\"issue-status-changed\",\"issueId\":\"" + issueId + "\"}")
            .getBytes(StandardCharsets.UTF_8);
        for (WebPushSubscriptionEntity subscription : repository.findForUser(userId)) {
            try {
                send(subscription, payload, 3600);
            } catch (ServerErrorException e) {
                // The failure of one browser should not prevent delivery to the other browsers.
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
            }
        }
    }

    private boolean send(final WebPushSubscriptionEntity subscription, final byte[] payload, final int ttl) {
        final boolean active;
        try {
            active = delivery.send(subscription, payload, ttl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw deliveryFailure(subscription, e);
        } catch (IOException | RuntimeException e) {
            throw deliveryFailure(subscription, e);
        }
        if (!active) {
            repository.deleteExpired(subscription);
        }
        return active;
    }

    private ServerErrorException deliveryFailure(final WebPushSubscriptionEntity subscription, final Exception error) {
        logger.warnv("Web Push failed for subscription {0} ({1})", subscription.getId(),
            error.getClass().getSimpleName());
        return new ServerErrorException("Push delivery failed", Status.BAD_GATEWAY);
    }

}
