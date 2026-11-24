package de.remsfal.core.json.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.immutables.value.Value.Immutable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import de.remsfal.core.ImmutableStyle;

@Immutable
@ImmutableStyle
@JsonDeserialize(as = ImmutableWebPushSubscriptionKeysJson.class)
public interface WebPushSubscriptionKeysJson {

    @NotBlank
    @Size(max = 88)
    @JsonProperty("p256dh")
    String getP256dh();

    @NotBlank
    @Size(max = 24)
    String getAuth();

}
