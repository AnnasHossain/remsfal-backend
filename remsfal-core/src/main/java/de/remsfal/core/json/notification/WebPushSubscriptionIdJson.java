package de.remsfal.core.json.notification;

import java.util.UUID;

import org.immutables.value.Value.Immutable;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import de.remsfal.core.ImmutableStyle;

@Immutable
@ImmutableStyle
@JsonDeserialize(as = ImmutableWebPushSubscriptionIdJson.class)
public interface WebPushSubscriptionIdJson {

    UUID getId();

}
