package de.remsfal.notification.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.interaso.webpush.VapidKeys;

import de.remsfal.core.json.ImmutableUserJson;
import de.remsfal.core.json.eventing.IssueEventJson.IssueEventType;
import de.remsfal.core.json.eventing.ImmutableIssueEventJson;
import de.remsfal.core.json.project.ImmutableRentalAgreementJson;
import de.remsfal.core.json.project.ImmutableTenantJson;
import de.remsfal.core.json.ticketing.ImmutableIssueJson;
import de.remsfal.core.model.ticketing.IssueModel.IssueStatus;
import de.remsfal.notification.control.PushNotificationController;
import de.remsfal.notification.control.WebPushController;
import de.remsfal.notification.control.WebPushDeliveryController;
import de.remsfal.test.AbstractTest;
import de.remsfal.test.TestData;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.restassured.http.Cookie;

@QuarkusTest
class WebPushResourceTest extends AbstractTest {

    private static final String BASE_PATH = "/notification/v1/web-push";
    private static final String SUBSCRIPTIONS = BASE_PATH + "/subscriptions";

    @Inject
    WebPushController controller;

    @Inject
    PushNotificationController pushController;

    @InjectSpy
    WebPushDeliveryController delivery;

    @BeforeEach
    void setUp() throws Exception {
        controller.deleteSubscriptions(TestData.USER_ID);
        controller.deleteSubscriptions(TestData.USER_ID_2);
        doReturn(true).when(delivery).isEnabled();
        doNothing().when(delivery).requireEnabled();
        doReturn(true).when(delivery).send(any(), any(), eq(60));
        doReturn(true).when(delivery).send(any(), any(), eq(3600));
    }

    @Test
    void tenantCanRegisterTestAndRemoveOnlyTheirOwnSubscription() throws Exception {
        final String endpoint = "https://updates.push.services.mozilla.com/wpush/v2/" + UUID.randomUUID();
        final String body = subscription(endpoint);
        given().when().get(BASE_PATH + "/public-key").then().statusCode(401);

        final String id = register(tenantCookie(), body);
        assertNotNull(id);
        given().cookie(tenantCookie()).contentType(MediaType.APPLICATION_JSON).body(body)
            .when().post(SUBSCRIPTIONS).then().statusCode(200).body("id", equalTo(id));
        given().cookie(otherCookie()).contentType(MediaType.APPLICATION_JSON).body(body)
            .when().post(SUBSCRIPTIONS).then().statusCode(409);
        given().cookie(otherCookie()).when().post(SUBSCRIPTIONS + "/" + id + "/test").then().statusCode(404);
        given().cookie(otherCookie()).when().delete(SUBSCRIPTIONS + "/" + id).then().statusCode(204);

        given().cookie(tenantCookie()).when().post(SUBSCRIPTIONS + "/" + id + "/test").then().statusCode(204);
        verify(delivery).send(any(), any(), eq(60));
        given().cookie(tenantCookie()).when().delete(SUBSCRIPTIONS + "/" + id).then().statusCode(204);
        given().cookie(tenantCookie()).when().post(SUBSCRIPTIONS + "/" + id + "/test").then().statusCode(404);
    }

    @Test
    void rejectsUntrustedPushEndpoint() {
        given().cookie(tenantCookie()).contentType(MediaType.APPLICATION_JSON)
            .body(subscription("https://updates.push.services.mozilla.com@localhost/internal"))
            .when().post(SUBSCRIPTIONS).then().statusCode(400);
    }

    @Test
    void removesExpiredSubscription() throws Exception {
        doReturn(false).when(delivery).send(any(), any(), eq(60));
        final String id = register(tenantCookie(),
            subscription("https://updates.push.services.mozilla.com/wpush/v2/" + UUID.randomUUID()));

        given().cookie(tenantCookie()).when().post(SUBSCRIPTIONS + "/" + id + "/test").then().statusCode(410);
        given().cookie(tenantCookie()).when().post(SUBSCRIPTIONS + "/" + id + "/test").then().statusCode(404);
    }

    @Test
    void onlyTenantReporterReceivesPushForVisibleStatusChange() throws Exception {
        final String id = register(tenantCookie(),
            subscription("https://updates.push.services.mozilla.com/wpush/v2/" + UUID.randomUUID()));
        register(otherCookie(),
            subscription("https://updates.push.services.mozilla.com/wpush/v2/" + UUID.randomUUID()));

        final UUID issueId = UUID.randomUUID();
        final ImmutableIssueJson issue = ImmutableIssueJson.builder()
            .id(issueId)
            .reporterId(TestData.USER_ID)
            .agreementId(TestData.AGREEMENT_ID)
            .visibleToTenants(true)
            .status(IssueStatus.IN_PROGRESS)
            .build();
        final ImmutableIssueEventJson event = ImmutableIssueEventJson.builder()
            .issueEventType(IssueEventType.ISSUE_UPDATED)
            .issueId(issueId)
            .issue(issue)
            .previousStatus(IssueStatus.OPEN)
            .principal(ImmutableUserJson.builder().id(TestData.USER_ID_2).build())
            .rentalAgreement(ImmutableRentalAgreementJson.builder()
                .id(TestData.AGREEMENT_ID)
                .tenants(List.of(ImmutableTenantJson.builder().userId(TestData.USER_ID).build()))
                .build())
            .build();

        clearInvocations(delivery);
        pushController.sendIssueStatusUpdate(event.withPreviousStatus(null));
        pushController.sendIssueStatusUpdate(event.withPreviousStatus(IssueStatus.IN_PROGRESS));
        pushController.sendIssueStatusUpdate(event.withIssue(issue.withVisibleToTenants(false)));
        pushController.sendIssueStatusUpdate(event.withRentalAgreement(null));
        verify(delivery, never()).send(any(), any(), anyInt());

        pushController.sendIssueStatusUpdate(event);
        verify(delivery, times(1)).send(any(), any(), eq(3600));
        verify(delivery).send(argThat(
            subscription -> subscription.getId().toString().equals(id)), any(), eq(3600));
    }

    private String register(final Cookie cookie, final String body) {
        return given().cookie(cookie).contentType(MediaType.APPLICATION_JSON).body(body)
            .when().post(SUBSCRIPTIONS).then().statusCode(200).extract().path("id");
    }

    private Cookie tenantCookie() {
        return buildCookie(TestData.USER_ID, TestData.USER_EMAIL, TestData.USER_NAME,
            Map.of(), Map.of(), Map.of(TestData.AGREEMENT_ID.toString(), TestData.PROJECT_ID.toString()));
    }

    private Cookie otherCookie() {
        return buildCookie(TestData.USER_ID_2, TestData.USER_EMAIL_2,
            TestData.USER_FIRST_NAME_2 + " " + TestData.USER_LAST_NAME_2,
            Map.of(), Map.of(), Map.of());
    }

    private String subscription(final String endpoint) {
        final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return """
            {"endpoint":"%s","keys":{"p256dh":"%s","auth":"%s"}}
            """.formatted(endpoint, encoder.encodeToString(VapidKeys.generate().getApplicationServerKey()),
                encoder.encodeToString(new byte[16]));
    }

}
