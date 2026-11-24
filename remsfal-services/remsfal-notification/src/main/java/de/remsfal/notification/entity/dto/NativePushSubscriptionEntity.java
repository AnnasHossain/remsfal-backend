package de.remsfal.notification.entity.dto;

import java.util.UUID;

import de.remsfal.common.entity.MetaDataEntity;
import de.remsfal.core.model.notification.NativePushTransport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "push_subscription")
public class NativePushSubscriptionEntity extends MetaDataEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NativePushTransport transport;

    @Column(nullable = false, length = 512)
    private String token;

    public UUID getId() {
        return id;
    }

    public NativePushTransport getTransport() {
        return transport;
    }

    public String getToken() {
        return token;
    }
}
