package de.remsfal.notification.boundary.push;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.remsfal.core.model.notification.NativePushTransport;
import de.remsfal.notification.control.port.NativePushSender;
import de.remsfal.notification.model.PushMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ApnsPushProducer implements NativePushSender {

    private static final Logger LOG = Logger.getLogger(ApnsPushProducer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Duration PROVIDER_TOKEN_LIFETIME = Duration.ofMinutes(45);
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    private final HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .connectTimeout(TIMEOUT)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    @Inject
    ObjectMapper json;

    @ConfigProperty(name = "de.remsfal.apns.team-id")
    Optional<String> teamId;

    @ConfigProperty(name = "de.remsfal.apns.key-id")
    Optional<String> keyId;

    @ConfigProperty(name = "de.remsfal.apns.private-key-path")
    Optional<String> privateKeyPath;

    @ConfigProperty(name = "de.remsfal.apns.topic")
    String topic;

    @ConfigProperty(name = "de.remsfal.apns.sandbox")
    boolean sandbox;

    private String cachedProviderToken;
    private Instant refreshAt = Instant.EPOCH;

    @Override
    public NativePushTransport transport() {
        return NativePushTransport.APNS;
    }

    @Override
    public boolean isEnabled() {
        return teamId.isPresent() && keyId.isPresent() && privateKeyPath.isPresent();
    }

    @Override
    public Result send(final String token, final PushMessage message) {
        final String host = sandbox ? "api.sandbox.push.apple.com" : "api.push.apple.com";
        try {
            final HttpRequest request = HttpRequest.newBuilder(URI.create("https://" + host + "/3/device/" + token))
                .timeout(TIMEOUT)
                .header("authorization", "bearer " + providerToken())
                .header("apns-topic", topic)
                .header("apns-push-type", "alert")
                .header("apns-expiration",
                    Long.toString(Instant.now().plusSeconds(message.ttlSeconds()).getEpochSecond()))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload(message)))
                .build();
            final int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 200) {
                return Result.ACCEPTED;
            }
            if (status == 410) {
                return Result.UNREGISTERED;
            }
            LOG.warnf("APNs rejected a push (HTTP %d)", status);
            throw new IllegalStateException("APNs send failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("APNs send interrupted");
        } catch (IOException | GeneralSecurityException e) {
            LOG.warnf("APNs request failed (%s)", e.getClass().getSimpleName());
            throw new IllegalStateException("APNs request failed");
        }
    }

    private byte[] payload(final PushMessage message) throws IOException {
        final Map<String, Object> payload = new HashMap<>(message.data());
        payload.put("aps", Map.of(
            "alert", Map.of("title", message.title(), "body", message.body()),
            "sound", "default"));
        return json.writeValueAsBytes(payload);
    }

    private synchronized String providerToken() throws IOException, GeneralSecurityException {
        final Instant now = Instant.now();
        if (now.isBefore(refreshAt)) {
            return cachedProviderToken;
        }

        final String header = BASE64.encodeToString(json.writeValueAsBytes(
            Map.of("alg", "ES256", "kid", keyId.orElseThrow())));
        final String claims = BASE64.encodeToString(json.writeValueAsBytes(
            Map.of("iss", teamId.orElseThrow(), "iat", now.getEpochSecond())));
        final String contents = header + "." + claims;
        final Signature es256 = Signature.getInstance("SHA256withECDSAinP1363Format");
        es256.initSign(readKey());
        es256.update(contents.getBytes(StandardCharsets.US_ASCII));
        cachedProviderToken = contents + "." + BASE64.encodeToString(es256.sign());
        refreshAt = now.plus(PROVIDER_TOKEN_LIFETIME);
        return cachedProviderToken;
    }

    private PrivateKey readKey() throws IOException, GeneralSecurityException {
        final String pem = Files.readString(Path.of(privateKeyPath.orElseThrow()))
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "");
        final byte[] encoded = Base64.getMimeDecoder().decode(pem);
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }
}
