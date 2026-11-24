package de.remsfal.notification.control.port;

import java.util.UUID;

import de.remsfal.notification.model.PushMessage;

public interface PushChannel {

    void sendToUser(UUID userId, PushMessage message);

    void deleteSubscriptions(UUID userId);
}
