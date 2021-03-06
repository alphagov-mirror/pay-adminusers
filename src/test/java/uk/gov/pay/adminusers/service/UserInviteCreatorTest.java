package uk.gov.pay.adminusers.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.pay.adminusers.app.config.AdminUsersConfig;
import uk.gov.pay.adminusers.app.config.LinksConfig;
import uk.gov.pay.adminusers.model.Invite;
import uk.gov.pay.adminusers.model.InviteUserRequest;
import uk.gov.pay.adminusers.model.SecondFactorMethod;
import uk.gov.pay.adminusers.model.Service;
import uk.gov.pay.adminusers.model.ServiceName;
import uk.gov.pay.adminusers.model.ServiceRole;
import uk.gov.pay.adminusers.model.User;
import uk.gov.pay.adminusers.persistence.dao.InviteDao;
import uk.gov.pay.adminusers.persistence.dao.RoleDao;
import uk.gov.pay.adminusers.persistence.dao.ServiceDao;
import uk.gov.pay.adminusers.persistence.dao.UserDao;
import uk.gov.pay.adminusers.persistence.entity.InviteEntity;
import uk.gov.pay.adminusers.persistence.entity.RoleEntity;
import uk.gov.pay.adminusers.persistence.entity.ServiceEntity;
import uk.gov.pay.adminusers.persistence.entity.ServiceRoleEntity;
import uk.gov.pay.adminusers.persistence.entity.UserEntity;
import uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity;
import uk.gov.service.payments.commons.model.SupportedLanguage;

import javax.ws.rs.WebApplicationException;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt;
import static uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid;
import static uk.gov.pay.adminusers.model.InviteRequest.FIELD_EMAIL;
import static uk.gov.pay.adminusers.model.InviteRequest.FIELD_ROLE_NAME;
import static uk.gov.pay.adminusers.model.InviteUserRequest.FIELD_SENDER;
import static uk.gov.pay.adminusers.model.InviteUserRequest.FIELD_SERVICE_EXTERNAL_ID;
import static uk.gov.pay.adminusers.model.Role.role;
import static uk.gov.pay.adminusers.persistence.entity.Role.ADMIN;

public class UserInviteCreatorTest {

    private static final String SELFSERVICE_URL = "http://selfservice";

    private RoleDao mockRoleDao = mock(RoleDao.class);
    private ServiceDao mockServiceDao = mock(ServiceDao.class);
    private UserDao mockUserDao = mock(UserDao.class);
    private InviteDao mockInviteDao = mock(InviteDao.class);
    private AdminUsersConfig mockConfig = mock(AdminUsersConfig.class);
    private NotificationService mockNotificationService = mock(NotificationService.class);
    private LinksConfig linksConfig = mock(LinksConfig.class);

    private UserInviteCreator userInviteCreator;
    private ArgumentCaptor<InviteEntity> expectedInvite = ArgumentCaptor.forClass(InviteEntity.class);
    private String senderEmail = "sender@example.com";
    private String email = "invited@example.com";
    private int serviceId = 1;
    private String serviceExternalId = "3453rmeuty87t";
    private String senderExternalId = "12345";
    private String roleName = "view-only";

    @BeforeEach
    public void setUp() {
        LinksConfig mockLinks = mock(LinksConfig.class);
        when(mockLinks.getSelfserviceUrl()).thenReturn(SELFSERVICE_URL);
        when(mockConfig.getLinks()).thenReturn(mockLinks);
        userInviteCreator = new UserInviteCreator(mockInviteDao, mockUserDao, mockRoleDao, linksConfig, mockNotificationService, mockServiceDao);
    }

    @Test
    public void create_shouldSendNotificationOnSuccessfulInvite() {

        mockInviteSuccessForNonExistingUserNonExistingInvite();

        when(mockNotificationService.sendInviteEmail(eq(senderEmail), eq(email), matches("^http://selfservice/invites/[0-9a-z]{32}$")))
                .thenReturn("random-notify-id");

        userInviteCreator.doInvite(inviteRequestFrom(senderExternalId, email, roleName));

        verify(mockInviteDao).persist(expectedInvite.capture());
        InviteEntity savedInvite = expectedInvite.getValue();

        assertThat(savedInvite.getEmail(), is(email));
        assertThat(savedInvite.getOtpKey(), is(notNullValue()));
        assertThat(savedInvite.getCode(), is(notNullValue()));
    }

    @Test
    public void shouldReturnEmpty_ifServiceNotFound() {
        when(mockServiceDao.findByExternalId(serviceExternalId)).thenReturn(Optional.empty());
        InviteUserRequest inviteUserRequest = inviteRequestFrom(senderEmail, email, roleName);
        Optional<Invite> invite = userInviteCreator.doInvite(inviteUserRequest);

        assertFalse(invite.isPresent());
    }

    @Test
    public void create_shouldStillCreateTheInviteFailingOnSendingEmail() {

        mockInviteSuccessForNonExistingUserNonExistingInvite();

        when(mockNotificationService.sendInviteEmail(eq(senderEmail), eq(email), matches("^http://selfservice/invites/[0-9a-z]{32}$")))
                .thenThrow(AdminUsersExceptions.userNotificationError());

        userInviteCreator.doInvite(inviteRequestFrom(senderExternalId, email, roleName));

        verify(mockInviteDao).persist(expectedInvite.capture());
        InviteEntity savedInvite = expectedInvite.getValue();

        assertThat(savedInvite.getEmail(), is(email));
        assertThat(savedInvite.getOtpKey(), is(notNullValue()));
        assertThat(savedInvite.getCode(), is(notNullValue()));
    }

    @Test
    public void create_shouldFailWithConflict_WhenValidInviteExistsInvitingUserIsDifferent() {

        ServiceEntity service = new ServiceEntity();
        service.setId(serviceId);
        service.setExternalId(serviceExternalId);
        String inviteCode = "code";

        UserEntity someOtherSender = new UserEntity();
        String someOtherSenderId = "7834ny0t7cr";
        someOtherSender.setExternalId(someOtherSenderId);
        someOtherSender.setEmail(senderEmail);
        RoleEntity role = new RoleEntity(role(ADMIN.getId(), "admin", "Admin Role"));
        someOtherSender.addServiceRole(new ServiceRoleEntity(service, role));

        when(mockServiceDao.findByExternalId(serviceExternalId)).thenReturn(Optional.of(service));
        when(mockUserDao.findByEmail(email)).thenReturn(Optional.empty());

        InviteEntity anInvite = anInvite(email, inviteCode, "otpKey", someOtherSender, service, role);


        when(mockInviteDao.findByEmail(email)).thenReturn(List.of(anInvite));
        InviteUserRequest inviteUserRequest = inviteRequestFrom(senderExternalId, email, roleName);

        WebApplicationException webApplicationException = assertThrows(WebApplicationException.class, ()
                -> userInviteCreator.doInvite(inviteUserRequest));
        assertThat(webApplicationException.getMessage(), is("HTTP 409 Conflict"));
    }

    @Test
    public void create_shouldFailWithPreConditionFailed_ifUserAlreadyInService() {

        ServiceEntity service = new ServiceEntity();
        service.setId(serviceId);
        service.setExternalId(serviceExternalId);

        UserEntity existingUser = new UserEntity();
        existingUser.setExternalId("7834ny0t7cr");
        existingUser.setEmail(email);
        RoleEntity role = new RoleEntity(role(ADMIN.getId(), "admin", "Admin Role"));
        existingUser.addServiceRole(new ServiceRoleEntity(service, role));

        when(mockServiceDao.findByExternalId(serviceExternalId)).thenReturn(Optional.of(service));
        when(mockUserDao.findByEmail(email)).thenReturn(Optional.of(existingUser));

        InviteUserRequest inviteUserRequest = inviteRequestFrom(senderExternalId, email, roleName);
        WebApplicationException webApplicationException = assertThrows(WebApplicationException.class,
                () -> userInviteCreator.doInvite(inviteUserRequest));
        assertThat(webApplicationException.getMessage(), is("HTTP 412 Precondition Failed"));
    }

    @Test
    public void create_shouldResendTheSameInviteEmail_ifAValidInviteExistsForTheSameServiceBySameSender_forNewUser() {

        //Given
        when(mockUserDao.findByEmail(email)).thenReturn(Optional.empty());
        InviteEntity anInvite = mockInviteSuccessExistingInvite();
        when(mockNotificationService.sendInviteEmail(eq(senderEmail), eq(email), matches("^http://selfservice/invites/[0-9a-z]{32}$")))
                .thenReturn("random-notify-id");


        //When
        InviteUserRequest inviteUserRequest = inviteRequestFrom(senderExternalId, email, roleName);
        Optional<Invite> invite = userInviteCreator.doInvite(inviteUserRequest);

        //Then
        assertThat(invite.isPresent(), is(true));
        assertThat(invite.get().getCode(), is(anInvite.getCode()));
        assertThat(invite.get().getEmail(), is(anInvite.getEmail()));
    }

    @Test
    public void create_shouldErrorForbidden_ifSenderCannotInviteUsersToTheSpecifiedService() {
        InviteEntity inviteEntity = mockInviteSuccessForNonExistingUserNonExistingInvite();
        inviteEntity.getSender().getServicesRoles().clear();

        InviteUserRequest inviteUserRequest = inviteRequestFrom(senderExternalId, email, roleName);
        WebApplicationException webApplicationException = assertThrows(WebApplicationException.class,
                () -> userInviteCreator.doInvite(inviteUserRequest));
        assertThat(webApplicationException.getMessage(), is("HTTP 403 Forbidden"));
    }

    @Test
    public void create_shouldResendTheSameInviteEmail_ifAValidInviteExistsForTheSameServiceBySameSender_forExistingUser() {

        //Given
        when(mockUserDao.findByEmail(email)).thenReturn(Optional.of(UserEntity.from(aUser(email))));
        InviteEntity anInvite = mockInviteSuccessExistingInvite();
        when(mockNotificationService.sendInviteExistingUserEmail(eq(senderEmail), eq(email), matches("^http://selfservice/invites/[0-9a-z]{32}$"),
                eq(anInvite.getService().getServiceNames().get(SupportedLanguage.ENGLISH).getName()))).thenReturn("random-notify-id");

        InviteUserRequest inviteUserRequest = inviteRequestFrom(senderExternalId, email, roleName);
        Optional<Invite> invite = userInviteCreator.doInvite(inviteUserRequest);

        assertThat(invite.isPresent(), is(true));
        assertThat(invite.get().getCode(), is(anInvite.getCode()));
        assertThat(invite.get().getEmail(), is(anInvite.getEmail()));
    }

    @Test
    public void create_shouldResendTheSameInviteEmail_ifAValidInviteExistsForTheSameServiceBySameSender_forExistingUser_evenIfNotifyThrowsAnError() {

        //Given
        when(mockUserDao.findByEmail(email)).thenReturn(Optional.of(UserEntity.from(aUser(email))));
        InviteEntity anInvite = mockInviteSuccessExistingInvite();
        when(mockNotificationService.sendInviteExistingUserEmail(eq(senderEmail), eq(email), matches("^http://selfservice/invites/[0-9a-z]{32}$"),
                eq(anInvite.getService().getServiceNames().get(SupportedLanguage.ENGLISH).getName())))
                .thenThrow(AdminUsersExceptions.userNotificationError());

        InviteUserRequest inviteUserRequest = inviteRequestFrom(senderExternalId, email, roleName);
        Optional<Invite> invite = userInviteCreator.doInvite(inviteUserRequest);

        assertThat(invite.isPresent(), is(true));
        assertThat(invite.get().getCode(), is(anInvite.getCode()));
        assertThat(invite.get().getEmail(), is(anInvite.getEmail()));
    }

    @Test
    public void create_shouldOnlyConsider_nonExpiredNonDisabledSameService_whenCheckingForExistingInvite() {

        InviteEntity validInvite = mockInviteSuccessExistingInvite();
        InviteEntity expiredInvite = new InviteEntity();
        expiredInvite.setExpiryDate(ZonedDateTime.now().minusDays(2));
        expiredInvite.setService(validInvite.getService());

        InviteEntity disabledInvite = new InviteEntity();
        disabledInvite.setDisabled(true);
        disabledInvite.setExpiryDate(ZonedDateTime.now().plusDays(1));
        disabledInvite.setService(validInvite.getService());

        InviteEntity emptyServiceInvite = new InviteEntity();
        emptyServiceInvite.setExpiryDate(ZonedDateTime.now().plusDays(1));

        InviteEntity nonMatchingServiceInvite = new InviteEntity();
        ServiceEntity serviceEntity = ServiceEntity.from(Service.from(new ServiceName("another-service")));
        nonMatchingServiceInvite.setService(serviceEntity);
        nonMatchingServiceInvite.setExpiryDate(ZonedDateTime.now().plusDays(1));

        when(mockInviteDao.findByEmail(email)).thenReturn(List.of(expiredInvite, disabledInvite, emptyServiceInvite, nonMatchingServiceInvite, validInvite));

        when(mockUserDao.findByEmail(email)).thenReturn(Optional.of(UserEntity.from(aUser(email))));
        when(mockNotificationService.sendInviteExistingUserEmail(eq(senderEmail), eq(email), matches("^http://selfservice/invites/[0-9a-z]{32}$"),
                eq(validInvite.getService().getServiceNames().get(SupportedLanguage.ENGLISH).getName()))).thenReturn("random-notify-id");

        InviteUserRequest inviteUserRequest = inviteRequestFrom(senderExternalId, email, roleName);
        Optional<Invite> invite = userInviteCreator.doInvite(inviteUserRequest);

        assertThat(invite.isPresent(), is(true));
        assertThat(invite.get().getCode(), is(validInvite.getCode()));
        assertThat(invite.get().getEmail(), is(validInvite.getEmail()));
    }

    private InviteEntity mockInviteSuccessExistingInvite() {
        ServiceEntity service = new ServiceEntity();
        service.addOrUpdateServiceName(ServiceNameEntity.from(SupportedLanguage.ENGLISH, Service.DEFAULT_NAME_VALUE));
        service.setId(serviceId);
        service.setExternalId(serviceExternalId);

        UserEntity sameSender = new UserEntity();
        sameSender.setExternalId(senderExternalId);
        sameSender.setEmail(senderEmail);
        RoleEntity role = new RoleEntity(role(ADMIN.getId(), "admin", "Admin Role"));
        sameSender.addServiceRole(new ServiceRoleEntity(service, role));

        when(mockServiceDao.findByExternalId(serviceExternalId)).thenReturn(Optional.of(service));
        when(linksConfig.getSelfserviceInvitesUrl()).thenReturn("http://selfservice/invites");

        String inviteCode = randomUuid();
        InviteEntity anInvite = anInvite(email, inviteCode, "otpKey", sameSender, service, role);
        when(mockInviteDao.findByEmail(email)).thenReturn(List.of(anInvite));
        return anInvite;
    }

    private InviteEntity mockInviteSuccessForNonExistingUserNonExistingInvite() {

        ServiceEntity service = new ServiceEntity();
        service.setId(serviceId);
        service.setExternalId(serviceExternalId);

        when(mockUserDao.findByEmail(email)).thenReturn(Optional.empty());
        when(mockInviteDao.findByEmail(email)).thenReturn(emptyList());
        when(mockServiceDao.findByExternalId(serviceExternalId)).thenReturn(Optional.of(service));
        when(mockRoleDao.findByRoleName(roleName)).thenReturn(Optional.of(new RoleEntity()));
        when(linksConfig.getSelfserviceInvitesUrl()).thenReturn("http://selfservice/invites");

        UserEntity senderUser = new UserEntity();
        senderUser.setExternalId(senderExternalId);
        senderUser.setEmail(senderEmail);
        RoleEntity role = new RoleEntity(role(ADMIN.getId(), "admin", "Admin Role"));
        senderUser.addServiceRole(new ServiceRoleEntity(service, role));
        when(mockUserDao.findByExternalId(senderExternalId)).thenReturn(Optional.of(senderUser));

        String inviteCode = "code";
        InviteEntity anInvite = anInvite(email, inviteCode, "otpKey", senderUser, service, role);
        when(mockInviteDao.findByCode(inviteCode)).thenReturn(Optional.of(anInvite));

        doNothing().when(mockInviteDao).persist(any(InviteEntity.class));

        return anInvite;
    }

    private InviteEntity anInvite(String email, String code, String otpKey, UserEntity userEntity, ServiceEntity serviceEntity, RoleEntity roleEntity) {
        InviteEntity inviteEntity = new InviteEntity(email, code, otpKey, roleEntity);
        inviteEntity.setSender(userEntity);
        inviteEntity.setService(serviceEntity);
        return inviteEntity;
    }

    private InviteUserRequest inviteRequestFrom(String senderExternalId, String email, String roleName) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put(FIELD_SENDER, senderExternalId);
        json.put(FIELD_EMAIL, email);
        json.put(FIELD_ROLE_NAME, roleName);
        json.put(FIELD_SERVICE_EXTERNAL_ID, serviceExternalId);
        return InviteUserRequest.from(json);
    }

    private User aUser(String email) {
        Service service = Service.from(serviceId, serviceExternalId, new ServiceName(Service.DEFAULT_NAME_VALUE));
        ServiceRole serviceRole = ServiceRole.from(service, role(ADMIN.getId(), "Admin", "Administrator"));
        return User.from(randomInt(), randomUuid(), "a-username", "random-password", email,
                "784rh", "8948924", Collections.singletonList(serviceRole), null,
                SecondFactorMethod.SMS, null, null, null);
    }

}
