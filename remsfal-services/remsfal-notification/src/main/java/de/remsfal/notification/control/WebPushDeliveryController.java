package de.remsfal.notification.control;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ServiceUnavailableException;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.interaso.webpush.VapidKeys;
import com.interaso.webpush.WebPush;

import de.remsfal.core.json.notification.WebPushSubscriptionJson;
import de.remsfal.notification.entity.dto.WebPushSubscriptionEntity;

@ApplicationScoped
public class WebPushDeliveryController {

    private static final Set<String> PUSH_HOSTS = Set.of(
        "fcm.googleapis.com", "updates.push.services.mozilla.com", "web.push.apple.com", "notify.windows.com");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @ConfigProperty(name = "de.remsfal.web-push.public-key")
    Optional<String> publicKey;

    @ConfigProperty(name = "de.remsfal.web-push.private-key")
    Optional<String> privateKey;

    @ConfigProperty(name = "de.remsfal.web-push.subject")
    Optional<String> subject;

    private WebPush webPush;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    @PostConstruct
    void configure() {
        if (publicKey.isEmpty() && privateKey.isEmpty() && subject.isEmpty()) {
            return;
        }
        webPush = new WebPush(subject.orElseThrow(),
            VapidKeys.fromUncompressedBytes(publicKey.orElseThrow(), privateKey.orElseThrow()));
    }

    public boolean isEnabled() {
        return webPush != null;
    }

    @Nullable
    public String getPublicKey() {
        return publicKey.orElse(null);
    }

    public void requireEnabled() {
        if (!isEnabled()) {
            throw new ServiceUnavailableException("Web Push is unavailable");
        }
    }

    public void validate(final WebPushSubscriptionJson subscription) {
        endpointUri(subscription.getEndpoint());
        try {
            final byte[] p256dh = decode(subscription.getKeys().getP256dh());
            if (p256dh.length != 65 || p256dh[0] != 4 || decode(subscription.getKeys().getAuth()).length != 16) {
                throw new BadRequestException("Invalid Web Push subscription keys");
            }
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid Web Push subscription keys");
        }
    }

    public boolean send(final WebPushSubscriptionEntity subscription, final byte[] payload, final int ttl)
        throws IOException, InterruptedException {
        final URI endpoint = endpointUri(subscription.getEndpoint());
        final byte[] body = webPush.getBody(payload, decode(subscription.getP256dh()), decode(subscription.getAuth()));
        final HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
            .timeout(TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        webPush.getHeaders(endpoint.toString(), ttl, null, null).forEach(request::header);
        final int status = httpClient.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        return webPush.getSubscriptionState(status, null) == WebPush.SubscriptionState.ACTIVE;
    }

    private static URI endpointUri(final String endpoint) {
        final URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid Web Push endpoint");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
            || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getRawUserInfo() != null
            || uri.getRawFragment() != null || uri.getRawPath().isEmpty()) {
            throw new BadRequestException("Invalid Web Push endpoint");
        }
        final String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!PUSH_HOSTS.contains(host) && !host.endsWith(".push.apple.com") && !host.endsWith(".notify.windows.com")) {
            throw new BadRequestException("Unsupported Web Push service");
        }
        return uri;
    }

    private static byte[] decode(final String value) {
        return Base64.getUrlDecoder().decode(value);
    }

}
