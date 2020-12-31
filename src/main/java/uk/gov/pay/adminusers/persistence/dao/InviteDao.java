package uk.gov.pay.adminusers.persistence.dao;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import jakarta.persistence.EntityManager;
import uk.gov.pay.adminusers.persistence.entity.InviteEntity;

import java.util.List;
import java.util.Optional;

@Transactional
public class InviteDao extends JpaDao<InviteEntity> {

    @Inject
    protected InviteDao(Provider<EntityManager> entityManager) {
        super(entityManager, InviteEntity.class);
    }

    public Optional<InviteEntity> findByCode(String code) {

        String query = "SELECT invite FROM InviteEntity invite " +
                "WHERE invite.code = :code";

        return entityManager.get()
                .createQuery(query, InviteEntity.class)
                .setParameter("code", code)
                .getResultList().stream().findFirst();
    }

    public List<InviteEntity> findByEmail(String email) {

        String query = "SELECT invite FROM InviteEntity invite " +
                "WHERE invite.email = :email";

        return entityManager.get()
                .createQuery(query, InviteEntity.class)
                .setParameter("email", email)
                .getResultList();
    }

    public List<InviteEntity> findAllByServiceId(String serviceId) {

        String query = "SELECT invite FROM InviteEntity invite " +
                "WHERE invite.service.externalId = :serviceId";

        return entityManager.get()
                .createQuery(query, InviteEntity.class)
                .setParameter("serviceId", serviceId)
                .getResultList();
    }
}
