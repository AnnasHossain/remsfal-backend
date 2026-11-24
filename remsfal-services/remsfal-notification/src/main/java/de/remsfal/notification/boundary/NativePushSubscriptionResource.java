package de.remsfal.notification.boundary;

import java.util.UUID;

import de.remsfal.common.boundary.AbstractResource;
import de.remsfal.core.api.notification.NativePushSubscriptionEndpoint;
import de.remsfal.core.json.notification.ImmutableNativePushSubscriptionIdJson;
import de.remsfal.core.json.notification.NativePushSubscriptionIdJson;
import de.remsfal.core.json.notification.NativePushSubscriptionJson;
import de.remsfal.notification.control.NativePushController;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@Authenticated
@RequestScoped
public class NativePushSubscriptionResource extends AbstractResource implements NativePushSubscriptionEndpoint {

    @Inject
    NativePushController controller;

    @Override
    public NativePushSubscriptionIdJson registerPushSubscription(final NativePushSubscriptionJson subscription) {
        return ImmutableNativePushSubscriptionIdJson.builder()
            .id(controller.register(principal.getId(), subscription))
            .build();
    }

    @Override
    public void sendTestPush(final UUID id) {
        controller.sendTest(principal.getId(), id);
    }

    @Override
    public void unregisterPushSubscription(final UUID id) {
        controller.deleteSubscription(principal.getId(), id);
    }
}
