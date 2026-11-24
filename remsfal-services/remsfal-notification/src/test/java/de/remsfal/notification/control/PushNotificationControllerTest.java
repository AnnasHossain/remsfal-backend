package de.remsfal.notification.control;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.inject.Instance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.remsfal.core.json.ImmutableUserJson;
import de.remsfal.core.json.eventing.ImmutableIssueEventJson;
import de.remsfal.core.json.eventing.IssueEventJson.IssueEventType;
import de.remsfal.core.json.project.ImmutableRentalAgreementJson;
import de.remsfal.core.json.project.ImmutableTenantJson;
import de.remsfal.core.json.ticketing.ImmutableIssueJson;
import de.remsfal.core.model.notification.NativePushTransport;
import de.remsfal.core.model.ticketing.IssueModel.IssueStatus;
import de.remsfal.notification.control.port.NativePushSender;
import de.remsfal.notification.control.port.PushChannel;
import de.remsfal.notification.entity.dao.NativePushSubscriptionRepository;
import de.remsfal.notification.entity.dto.NativePushSubscriptionEntity;
import de.remsfal.notification.model.PushMessage;
import de.remsfal.test.TestData;

class PushNotificationControllerTest {

    private static final UUID TENANT = TestData.USER_ID;
    private static final UUID MANAGER = TestData.USER_ID_2;
    private static final UUID ISSUE_ID = UUID.randomUUID();
    private static final String ANDROID_TOKEN = "test-android-token";
    private static final String IOS_TOKEN = "1234abcd";

    private final WebPushController web = mock(WebPushController.class);
    private final NativePushSender android = mock(NativePushSender.class);
    private final NativePushSender ios = mock(NativePushSender.class);
    private final NativePushSubscriptionRepository subscriptions = mock(NativePushSubscriptionRepository.class);
    private final PushNotificationController controller = new PushNotificationController();

    @BeforeEach
    void setUp() {
        final NativePushController nativePush = new NativePushController();
        nativePush.repository = subscriptions;
        nativePush.senders = beans(List.of(android, ios));
        controller.channels = beans(List.<PushChannel>of(nativePush));
        controller.webPushController = web;
        final NativePushSubscriptionEntity androidSubscription = subscription(
            android, NativePushTransport.FCM, ANDROID_TOKEN);
        final NativePushSubscriptionEntity iosSubscription = subscription(ios, NativePushTransport.APNS, IOS_TOKEN);
        when(subscriptions.findForUser(TENANT)).thenReturn(List.of(androidSubscription, iosSubscription));
    }

    @Test
    void sendsViaAllThreeTransportsWhenManagerChangesTheirOwnVisibleIssue() {
        controller.sendIssueStatusUpdate(statusUpdate(MANAGER));
        verifyDeliveries();
    }

    @Test
    void sendsViaAllThreeTransportsToTheTenantWhoReportedTheIssue() {
        controller.sendIssueStatusUpdate(statusUpdate(TENANT));
        verifyDeliveries();
    }

    @Test
    void sendsNothingOnCreationOrForAnUnrelatedRentalAgreement() {
        final ImmutableIssueEventJson event = statusUpdate(MANAGER);
        controller.sendIssueStatusUpdate(event.withIssueEventType(IssueEventType.ISSUE_CREATED));
        controller.sendIssueStatusUpdate(event.withIssue(ImmutableIssueJson.copyOf(event.getIssue())
            .withAgreementId(UUID.randomUUID())));
        verifyNoInteractions(web, subscriptions);
    }

    private void verifyDeliveries() {
        verify(web).sendIssueStatusUpdate(TENANT, ISSUE_ID);
        verify(android).send(ANDROID_TOKEN, PushMessage.issueStatusChanged(ISSUE_ID));
        verify(ios).send(IOS_TOKEN, PushMessage.issueStatusChanged(ISSUE_ID));
        verify(subscriptions).findForUser(TENANT);
    }

    private ImmutableIssueEventJson statusUpdate(final UUID reporterId) {
        return ImmutableIssueEventJson.builder()
            .issueEventType(IssueEventType.ISSUE_UPDATED)
            .issueId(ISSUE_ID)
            .previousStatus(IssueStatus.OPEN)
            .principal(ImmutableUserJson.builder().id(MANAGER).build())
            .issue(ImmutableIssueJson.builder()
                .id(ISSUE_ID)
                .reporterId(reporterId)
                .agreementId(TestData.AGREEMENT_ID)
                .visibleToTenants(true)
                .status(IssueStatus.IN_PROGRESS)
                .build())
            .rentalAgreement(ImmutableRentalAgreementJson.builder()
                .id(TestData.AGREEMENT_ID)
                .tenants(List.of(ImmutableTenantJson.builder().userId(TENANT).build(),
                    ImmutableTenantJson.builder().userId(TENANT).build()))
                .build())
            .build();
    }

    private NativePushSubscriptionEntity subscription(final NativePushSender sender,
        final NativePushTransport transport, final String token) {
        when(sender.transport()).thenReturn(transport);
        when(sender.isEnabled()).thenReturn(true);
        when(sender.send(token, PushMessage.issueStatusChanged(ISSUE_ID))).thenReturn(NativePushSender.Result.ACCEPTED);
        final NativePushSubscriptionEntity subscription = mock(NativePushSubscriptionEntity.class);
        when(subscription.getTransport()).thenReturn(transport);
        when(subscription.getToken()).thenReturn(token);
        return subscription;
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> beans(final List<T> candidates) {
        final Instance<T> instance = mock(Instance.class);
        when(instance.iterator()).thenAnswer(invocation -> candidates.iterator());
        when(instance.stream()).thenAnswer(invocation -> candidates.stream());
        return instance;
    }
}
