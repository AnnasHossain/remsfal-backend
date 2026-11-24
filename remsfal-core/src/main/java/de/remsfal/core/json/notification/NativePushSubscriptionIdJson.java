package de.remsfal.core.json.notification;

import java.util.UUID;

import de.remsfal.core.ImmutableStyle;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableStyle
public interface NativePushSubscriptionIdJson {
    UUID getId();
}
