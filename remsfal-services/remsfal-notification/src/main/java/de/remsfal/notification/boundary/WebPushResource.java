package de.remsfal.notification.boundary;

import java.util.UUID;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import de.remsfal.common.boundary.AbstractResource;
import de.remsfal.core.api.notification.WebPushEndpoint;
import de.remsfal.core.json.notification.ImmutableWebPushPublicKeyJson;
import de.remsfal.core.json.notification.ImmutableWebPushSubscriptionIdJson;
import de.remsfal.core.json.notification.WebPushPublicKeyJson;
import de.remsfal.core.json.notification.WebPushSubscriptionIdJson;
import de.remsfal.core.json.notification.WebPushSubscriptionJson;
import de.remsfal.notification.control.WebPushController;
import io.quarkus.security.Authenticated;

@Authenticated
@RequestScoped
public class WebPushResource extends AbstractResource implements WebPushEndpoint {

    @Inject
    WebPushController controller;

    @Override
    public WebPushPublicKeyJson getWebPushPublicKey() {
        return ImmutableWebPushPublicKeyJson.builder().publicKey(controller.getPublicKey()).build();
    }

    @Override
    public WebPushSubscriptionIdJson saveWebPushSubscription(final WebPushSubscriptionJson subscription) {
        return ImmutableWebPushSubscriptionIdJson.builder()
            .id(controller.saveSubscription(principal.getId(), subscription))
            .build();
    }

    @Override
    public void deleteWebPushSubscription(final UUID id) {
        controller.deleteSubscription(principal.getId(), id);
    }

    @Override
    public void sendWebPushTest(final UUID id) {
        controller.sendTest(principal.getId(), id);
    }

}
