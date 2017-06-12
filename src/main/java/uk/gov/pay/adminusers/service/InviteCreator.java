package uk.gov.pay.adminusers.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import uk.gov.pay.adminusers.app.config.LinksConfig;
import uk.gov.pay.adminusers.logger.PayLoggerFactory;
import uk.gov.pay.adminusers.model.Invite;
import uk.gov.pay.adminusers.model.InviteServiceRequest;
import uk.gov.pay.adminusers.persistence.dao.InviteDao;
import uk.gov.pay.adminusers.persistence.dao.RoleDao;
import uk.gov.pay.adminusers.persistence.dao.UserDao;
import uk.gov.pay.adminusers.persistence.entity.InviteEntity;
import uk.gov.pay.adminusers.persistence.entity.UserEntity;

import javax.inject.Inject;
import java.util.Optional;

import static java.lang.String.format;
import static uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid;
import static uk.gov.pay.adminusers.model.InviteType.SERVICE;
import static uk.gov.pay.adminusers.service.AdminUsersExceptions.*;

public class InviteCreator {

    private static final Logger LOGGER = PayLoggerFactory.getLogger(InviteCreator.class);

    private final InviteDao inviteDao;
    private final UserDao userDao;
    private RoleDao roleDao;
    private final LinksBuilder linksBuilder;
    private LinksConfig linksConfig;
    private final NotificationService notificationService;

    @Inject
    public InviteCreator(InviteDao inviteDao, UserDao userDao, RoleDao roleDao, LinksBuilder linksBuilder,
                         LinksConfig linksConfig, NotificationService notificationService) {
        this.inviteDao = inviteDao;
        this.userDao = userDao;
        this.roleDao = roleDao;
        this.linksBuilder = linksBuilder;
        this.linksConfig = linksConfig;
        this.notificationService = notificationService;
    }

    @Transactional
    public Invite doCreate(InviteServiceRequest inviteServiceRequest) {
        String requestEmail = inviteServiceRequest.getEmail();
        Optional<UserEntity> anExistingUser = userDao.findByEmail(requestEmail);
        if (anExistingUser.isPresent()) {
            UserEntity user = anExistingUser.get();
            if (!user.isDisabled()) {
                sendUserExitsNotification(requestEmail, user.getExternalId());
            } else {
                sendUserDisabledNotification(requestEmail, user.getExternalId());
            }
            throw conflictingEmail(requestEmail);
        }

        Optional<InviteEntity> inviteOptional = inviteDao.findByEmail(requestEmail);
        if (inviteOptional.isPresent()) {
            InviteEntity foundInvite = inviteOptional.get();
            if (!foundInvite.isExpired() && !foundInvite.isDisabled()) {
                //TODO: what should we do here: Discuss with stephen m ??
                throw conflictingInvite(requestEmail);
            }
        }

        return roleDao.findByRoleName(inviteServiceRequest.getRoleName())
                .map(roleEntity -> {
                    InviteEntity inviteEntity = new InviteEntity(requestEmail, randomUuid(), inviteServiceRequest.getOtpKey(), null, null, roleEntity);
                    inviteEntity.setTelephoneNumber(inviteServiceRequest.getTelephoneNumber());
                    inviteEntity.setType(SERVICE);
                    inviteDao.persist(inviteEntity);
                    String inviteUrl = format("%s/%s", linksConfig.getSelfserviceInvitesUrl(), inviteEntity.getCode());
                    sendInviteNotification(inviteEntity, inviteUrl);
                    return linksBuilder.decorate(inviteEntity.toInvite(inviteUrl));
                })
                .orElseThrow(() -> internalServerError(format("Role [%s] not a valid role for creating a invite service request", inviteServiceRequest.getRoleName())));

    }

    private void sendUserDisabledNotification(String email, String userExternalId) {
        notificationService.sendServiceInviteUserDisabledEmail(email, linksConfig.getSupportUrl())
                .thenAcceptAsync(notificationId -> LOGGER.info("sent create service, user account disabled email successfully, notification id [{}]", notificationId))
                .exceptionally(exception -> {
                    LOGGER.error("error sending service creation, user account disabled email", exception);
                    return null;
                });
        LOGGER.info("Disabled existing user tried to create a service - user_id={}", userExternalId);
    }

    private void sendUserExitsNotification(String email, String userExternalId) {
        notificationService.sendServiceInviteUserExistsEmail(email, linksConfig.getSelfserviceLoginUrl(), linksConfig.getSelfserviceForgottenPasswordUrl(), linksConfig.getSupportUrl())
                .thenAcceptAsync(notificationId -> LOGGER.info("sent create service, user exists email successfully, notification id [{}]", notificationId))
                .exceptionally(exception -> {
                    LOGGER.error("error sending service creation, users exists email", exception);
                    return null;
                });
        LOGGER.info("Existing user tried to create a service - user_id={}", userExternalId);
    }

    private void sendInviteNotification(InviteEntity invite, String targetUrl) {
        notificationService.sendServiceInviteEmail(invite.getEmail(), targetUrl)
                .thenAcceptAsync(notificationId -> LOGGER.info("sent create service invitation email successfully, notification id [{}]", notificationId))
                .exceptionally(exception -> {
                    LOGGER.error("error sending create service invitation", exception);
                    return null;
                });
        LOGGER.info("New service creation invitation created");
    }

}
