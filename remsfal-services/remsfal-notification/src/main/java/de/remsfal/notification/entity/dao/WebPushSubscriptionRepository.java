package de.remsfal.notification.entity.dao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import de.remsfal.core.json.notification.WebPushSubscriptionJson;
import de.remsfal.notification.entity.dto.WebPushSubscriptionEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

@ApplicationScoped
public class WebPushSubscriptionRepository implements PanacheRepositoryBase<WebPushSubscriptionEntity, UUID> {

    @Transactional
    public Optional<UUID> saveForUser(final UUID userId, final WebPushSubscriptionJson subscription) {
        // An endpoint can only be refreshed by its owner, including concurrent registrations.
        final int saved = getEntityManager().createNativeQuery("""
            INSERT INTO web_push_subscriptions (id, user_id, endpoint, p256dh, auth)
            VALUES (:id, :userId, :endpoint, :p256dh, :auth)
            ON CONFLICT (endpoint) DO UPDATE
            SET p256dh = EXCLUDED.p256dh, auth = EXCLUDED.auth, modified_at = CURRENT_TIMESTAMP
            WHERE web_push_subscriptions.user_id = EXCLUDED.user_id
            """)
            .setParameter("id", UUID.randomUUID())
            .setParameter("userId", userId)
            .setParameter("endpoint", subscription.getEndpoint())
            .setParameter("p256dh", subscription.getKeys().getP256dh())
            .setParameter("auth", subscription.getKeys().getAuth())
            .executeUpdate();
        return saved == 0 ? Optional.empty() : find("endpoint", subscription.getEndpoint())
            .firstResultOptional().map(WebPushSubscriptionEntity::getId);
    }

    @Transactional
    public List<WebPushSubscriptionEntity> findForUser(final UUID userId) {
        return list("userId", userId);
    }

    @Transactional
    public Optional<WebPushSubscriptionEntity> findOwned(final UUID userId, final UUID id) {
        return find("userId = ?1 and id = ?2", userId, id).firstResultOptional();
    }

    @Transactional
    public void deleteOwned(final UUID userId, final UUID id) {
        delete("userId = ?1 and id = ?2", userId, id);
    }

    @Transactional
    public void deleteExpired(final WebPushSubscriptionEntity subscription) {
        delete("id = ?1 and modifiedAt = ?2", subscription.getId(), subscription.getModifiedAt());
    }

    @Transactional
    public void deleteForUser(final UUID userId) {
        delete("userId", userId);
    }

}
