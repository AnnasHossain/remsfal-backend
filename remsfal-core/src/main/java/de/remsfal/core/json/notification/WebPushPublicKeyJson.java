package de.remsfal.core.json.notification;

import jakarta.annotation.Nullable;

import org.immutables.value.Value.Immutable;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import de.remsfal.core.ImmutableStyle;

@Immutable
@ImmutableStyle
@JsonDeserialize(as = ImmutableWebPushPublicKeyJson.class)
public interface WebPushPublicKeyJson {

    @Nullable
    String getPublicKey();

}
