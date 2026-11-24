package de.remsfal.notification.entity.dto;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import de.remsfal.common.entity.MetaDataEntity;

@Entity
@Table(name = "web_push_subscriptions")
public class WebPushSubscriptionEntity extends MetaDataEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true, length = 2048)
    private String endpoint;

    @Column(nullable = false, length = 88)
    private String p256dh;

    @Column(nullable = false, length = 24)
    private String auth;

    public UUID getId() {
        return id;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }

}
