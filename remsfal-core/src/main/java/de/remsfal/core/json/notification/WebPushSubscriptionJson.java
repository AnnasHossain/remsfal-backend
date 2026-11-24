package de.remsfal.core.json.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.immutables.value.Value.Immutable;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import de.remsfal.core.ImmutableStyle;

@Immutable
@ImmutableStyle
@JsonDeserialize(as = ImmutableWebPushSubscriptionJson.class)
public interface WebPushSubscriptionJson {

    @NotBlank
    @Size(max = 2048)
    String getEndpoint();

    @Valid
    @NotNull
    WebPushSubscriptionKeysJson getKeys();

}
