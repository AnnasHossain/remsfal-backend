package de.remsfal.notification.control;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import de.remsfal.core.json.notification.NativePushSubscriptionJson;
import de.remsfal.core.model.notification.NativePushTransport;
import de.remsfal.notification.control.port.NativePushSender;
import de.remsfal.notification.control.port.PushChannel;
import de.remsfal.notification.entity.dao.NativePushSubscriptionRepository;
import de.remsfal.notification.entity.dto.NativePushSubscriptionEntity;
import de.remsfal.notification.model.PushMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.Response.Status;
import org.jboss.logging.Logger;

@ApplicationScoped
public class NativePushController implements PushChannel {

    private static final Logger LOG = Logger.getLogger(NativePushController.class);
    private static final Pattern APNS_TOKEN = Pattern.compile("(?:[0-9a-fA-F]{2})+");

    @Inject
    Instance<NativePushSender> senders;

    @Inject
    NativePushSubscriptionRepository repository;

    public UUID register(final UUID userId, final NativePushSubscriptionJson subscription) {
        final NativePushTransport transport = subscription.getTransport() == null
            ? NativePushTransport.FCM : subscription.getTransport();
        requireSender(transport);

        String token = subscription.getToken();
        if (transport == NativePushTransport.APNS) {
            if (!APNS_TOKEN.matcher(token).matches()) {
                throw new BadRequestException("Invalid APNs device token");
            }
            token = token.toLowerCase(Locale.ROOT);
        }
        return repository.saveForUser(userId, transport, token, subscription.getPreviousSubscriptionId())
            .orElseThrow(() -> new ClientErrorException("Token is already registered to another user",
                Status.CONFLICT));
    }

    @Override
    public void sendToUser(final UUID userId, final PushMessage message) {
        for (NativePushSubscriptionEntity subscription : repository.findForUser(userId)) {
            final NativePushSender sender = findSender(subscription.getTransport());
            if (sender == null || !sender.isEnabled()) {
                continue;
            }
            try {
                send(sender, subscription, message);
            } catch (RuntimeException e) {
                LOG.warnf("Push failed for %s subscription %s",
                    subscription.getTransport(), subscription.getId());
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
            }
        }
    }

    public void sendTest(final UUID userId, final UUID id) {
        final NativePushSubscriptionEntity subscription = repository.findOwned(userId, id)
            .orElseThrow(NotFoundException::new);
        final NativePushSender sender = requireSender(subscription.getTransport());
        final boolean accepted;
        try {
            accepted = send(sender, subscription, PushMessage.test());
        } catch (RuntimeException e) {
            LOG.warnf("Test push failed for %s subscription %s",
                subscription.getTransport(), id);
            throw new ServerErrorException("Push delivery failed", Status.BAD_GATEWAY);
        }
        if (!accepted) {
            throw new ClientErrorException("Subscription expired", Status.GONE);
        }
    }

    private boolean send(final NativePushSender sender, final NativePushSubscriptionEntity subscription,
                         final PushMessage message) {
        if (sender.send(subscription.getToken(), message) == NativePushSender.Result.UNREGISTERED) {
            repository.deleteExpired(subscription);
            return false;
        }
        return true;
    }

    private NativePushSender findSender(final NativePushTransport transport) {
        return senders.stream().filter(sender -> sender.transport() == transport).findFirst().orElse(null);
    }

    private NativePushSender requireSender(final NativePushTransport transport) {
        final NativePushSender sender = findSender(transport);
        if (sender == null || !sender.isEnabled()) {
            throw new ServiceUnavailableException("Push transport is unavailable");
        }
        return sender;
    }

    public void deleteSubscription(final UUID userId, final UUID id) {
        repository.deleteOwned(userId, id);
    }

    @Override
    public void deleteSubscriptions(final UUID userId) {
        repository.deleteForUser(userId);
    }
}
