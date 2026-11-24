package de.remsfal.notification.control.port;

import de.remsfal.core.model.notification.NativePushTransport;
import de.remsfal.notification.model.PushMessage;

public interface NativePushSender {

    enum Result { ACCEPTED, UNREGISTERED }

    NativePushTransport transport();

    boolean isEnabled();

    Result send(String token, PushMessage message);
}
