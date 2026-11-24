package de.remsfal.core.json.notification;

import java.util.UUID;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import de.remsfal.core.ImmutableStyle;
import de.remsfal.core.model.notification.NativePushTransport;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = ImmutableNativePushSubscriptionJson.class)
public interface NativePushSubscriptionJson {

    @NotBlank
    @Size(max = 512)
    String getToken();

    /** Defaults to FCM for clients sending only a token. */
    @Nullable
    NativePushTransport getTransport();

    @Nullable
    UUID getPreviousSubscriptionId();
}
