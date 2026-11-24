package de.remsfal.notification.boundary.push;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;

import de.remsfal.core.model.notification.NativePushTransport;
import de.remsfal.notification.control.port.NativePushSender;
import de.remsfal.notification.model.PushMessage;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class FirebasePushProducer implements NativePushSender {

    private static final Logger LOG = Logger.getLogger(FirebasePushProducer.class);

    @ConfigProperty(name = "fcm.service-account-path")
    Optional<String> serviceAccountPath;

    private FirebaseMessaging messaging;

    @Override
    public NativePushTransport transport() {
        return NativePushTransport.FCM;
    }

    @Override
    public boolean isEnabled() {
        return serviceAccountPath.isPresent();
    }

    @Override
    public Result send(final String token, final PushMessage message) {
        final Message request = Message.builder()
            .setToken(token)
            .setNotification(Notification.builder().setTitle(message.title()).setBody(message.body()).build())
            .setAndroidConfig(AndroidConfig.builder()
                .setTtl(message.ttlSeconds() * 1000L)
                .setNotification(AndroidNotification.builder().setChannelId("default_notification_channel_id").build())
                .build())
            .putAllData(message.data())
            .build();
        try {
            messaging().send(request);
            return Result.ACCEPTED;
        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                return Result.UNREGISTERED;
            }
            LOG.warnf("FCM rejected a push (%s)", e.getMessagingErrorCode());
            throw new IllegalStateException("FCM send failed");
        }
    }

    private synchronized FirebaseMessaging messaging() {
        if (messaging == null) {
            try (var key = Files.newInputStream(Path.of(serviceAccountPath.orElseThrow()))) {
                final FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(ServiceAccountCredentials.fromStream(key))
                    .setConnectTimeout(5000)
                    .setReadTimeout(5000)
                    .build();
                messaging = FirebaseMessaging.getInstance(FirebaseApp.initializeApp(options, "remsfal-push"));
            } catch (IOException e) {
                LOG.warn("Could not read the FCM service account");
                throw new IllegalStateException("FCM configuration failed");
            }
        }
        return messaging;
    }
}
