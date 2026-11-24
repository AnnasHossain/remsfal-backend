package de.remsfal.notification.entity.dao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import de.remsfal.core.model.notification.NativePushTransport;
import de.remsfal.notification.entity.dto.NativePushSubscriptionEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class NativePushSubscriptionRepository implements PanacheRepositoryBase<NativePushSubscriptionEntity, UUID> {

    @Transactional
    public Optional<UUID> saveForUser(final UUID userId, final NativePushTransport transport,
                                      final String token, final UUID previousId) {
        final int saved = getEntityManager().createNativeQuery("""
            INSERT INTO push_subscription (id, user_id, transport, token)
            VALUES (:id, :userId, :transport, :token)
            ON CONFLICT (transport, token) DO UPDATE SET modified_at = CURRENT_TIMESTAMP
            WHERE push_subscription.user_id = EXCLUDED.user_id
            """)
            .setParameter("id", UUID.randomUUID())
            .setParameter("userId", userId)
            .setParameter("transport", transport.name())
            .setParameter("token", token)
            .executeUpdate();
        if (saved == 0) {
            return Optional.empty();
        }

        final UUID id = find("transport = ?1 and token = ?2", transport, token).singleResult().getId();
        if (previousId != null && !previousId.equals(id)) {
            delete("id = ?1 and userId = ?2 and transport = ?3", previousId, userId, transport);
        }
        return Optional.of(id);
    }

    @Transactional
    public List<NativePushSubscriptionEntity> findForUser(final UUID userId) {
        return list("userId", userId);
    }

    @Transactional
    public Optional<NativePushSubscriptionEntity> findOwned(final UUID userId, final UUID id) {
        return find("userId = ?1 and id = ?2", userId, id).firstResultOptional();
    }

    @Transactional
    public void deleteOwned(final UUID userId, final UUID id) {
        delete("userId = ?1 and id = ?2", userId, id);
    }

    @Transactional
    public void deleteExpired(final NativePushSubscriptionEntity subscription) {
        delete("id = ?1 and modifiedAt = ?2", subscription.getId(), subscription.getModifiedAt());
    }

    @Transactional
    public void deleteForUser(final UUID userId) {
        delete("userId", userId);
    }
}
