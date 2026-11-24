package de.remsfal.notification.control;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import de.remsfal.core.json.eventing.IssueEventJson;
import de.remsfal.core.json.eventing.IssueEventJson.IssueEventType;
import de.remsfal.core.json.project.TenantJson;
import de.remsfal.notification.control.port.PushChannel;
import de.remsfal.notification.model.PushMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PushNotificationController {

    private static final Logger LOG = Logger.getLogger(PushNotificationController.class);

    @Inject
    Instance<PushChannel> channels;

    @Inject
    WebPushController webPushController;

    public void sendIssueStatusUpdate(final IssueEventJson event) {
        if (!isTenantStatusUpdate(event)) {
            return;
        }
        final Set<UUID> recipients = event.getRentalAgreement().getTenants().stream()
            .map(TenantJson::getUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        final UUID reporterId = event.getIssue().getReporterId();
        if (recipients.contains(reporterId)) {
            recipients.retainAll(Set.of(reporterId));
        }
        recipients.remove(event.getPrincipal().getId());

        final PushMessage message = PushMessage.issueStatusChanged(event.getIssueId());
        for (UUID userId : recipients) {
            try {
                webPushController.sendIssueStatusUpdate(userId, event.getIssueId());
            } catch (RuntimeException e) {
                LOG.errorv(e, "Web Push dispatch failed for issue {0}", event.getIssueId());
            }
            for (PushChannel channel : channels) {
                try {
                    channel.sendToUser(userId, message);
                } catch (RuntimeException e) {
                    LOG.errorv(e, "Native push dispatch failed for issue {0}", event.getIssueId());
                }
            }
        }
    }

    @Transactional
    public void deleteSubscriptions(final UUID userId) {
        for (PushChannel channel : channels) {
            channel.deleteSubscriptions(userId);
        }
    }

    private static boolean isTenantStatusUpdate(final IssueEventJson event) {
        if (event.getIssueEventType() != IssueEventType.ISSUE_UPDATED || event.getPreviousStatus() == null
            || event.getIssue() == null || event.getIssueId() == null || event.getIssue().getStatus() == null
            || event.getPreviousStatus() == event.getIssue().getStatus()
            || !Boolean.TRUE.equals(event.getIssue().isVisibleToTenants()) || event.getIssue().getAgreementId() == null
            || event.getRentalAgreement() == null || event.getRentalAgreement().getTenants() == null
            || !Objects.equals(event.getIssue().getAgreementId(), event.getRentalAgreement().getId())) {
            return false;
        }
        return event.getIssue().getReporterId() != null
            && event.getPrincipal() != null && event.getPrincipal().getId() != null;
    }
}
