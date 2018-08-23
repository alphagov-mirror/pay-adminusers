package uk.gov.pay.adminusers.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import uk.gov.pay.adminusers.exception.ServiceNotFoundException;
import uk.gov.pay.adminusers.model.Service;
import uk.gov.pay.adminusers.model.ServiceUpdateRequest;
import uk.gov.pay.adminusers.model.UpdateMerchantDetailsRequest;
import uk.gov.pay.adminusers.persistence.dao.ServiceDao;
import uk.gov.pay.adminusers.persistence.entity.MerchantDetailsEntity;
import uk.gov.pay.adminusers.persistence.entity.ServiceEntity;
import uk.gov.pay.adminusers.persistence.entity.service.ServiceNameEntity;
import uk.gov.pay.adminusers.persistence.entity.service.SupportedLanguage;

import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid;

public class ServiceUpdaterTest {

    private static final String NON_EXISTENT_SERVICE_EXTERNAL_ID = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";

    private ServiceDao serviceDao = mock(ServiceDao.class);
    private ServiceUpdater updater;

    @Before
    public void before() throws Exception {
        updater = new ServiceUpdater(serviceDao);
    }

    @Test
    public void shouldUpdateNameSuccessfully() {
        String serviceId = randomUuid();
        String nameToUpdate = "new-name";
        ServiceUpdateRequest request = ServiceUpdateRequest.from(new ObjectNode(JsonNodeFactory.instance, ImmutableMap.of(
                "path", new TextNode("name"),
                "value", new TextNode(nameToUpdate),
                "op", new TextNode("replace"))));
        ServiceEntity serviceEntity = mock(ServiceEntity.class);

        when(serviceDao.findByExternalId(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(serviceEntity.toService()).thenReturn(Service.from());

        Optional<Service> maybeService = updater.doUpdate(serviceId, request);

        assertThat(maybeService.isPresent(), is(true));
        verify(serviceEntity, times(1)).setName(nameToUpdate);
        verify(serviceDao, times(1)).merge(serviceEntity);
    }

    @Test
    public void shouldSuccess_updateMerchantDetails() throws ServiceNotFoundException {
        String serviceId = randomUuid();
        String name = "name";
        String telephoneNumber = "03069990000";
        String addressLine1 = "something";
        String addressLine2 = "something";
        String addressCity = "something";
        String addressPostcode = "something";
        String addressCountry = "something";
        String email = "dd-merchant@example.com";

        MerchantDetailsEntity toUpdate = new MerchantDetailsEntity(
                name, telephoneNumber, addressLine1, addressLine2, addressCity, addressPostcode, addressCountry, email
        );
        UpdateMerchantDetailsRequest request = new UpdateMerchantDetailsRequest(
                name, telephoneNumber, addressLine1, addressLine2, addressCity, addressPostcode, addressCountry, email
        );
        ServiceEntity serviceEntity = mock(ServiceEntity.class);

        when(serviceDao.findByExternalId(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(serviceEntity.toService()).thenReturn(Service.from());

        Service service = updater.doUpdateMerchantDetails(serviceId, request);

        assertNotNull(service);
        verify(serviceEntity, times(1)).setMerchantDetailsEntity(toUpdate);
        verify(serviceDao, times(1)).merge(serviceEntity);
    }

    @Test(expected = ServiceNotFoundException.class)
    public void shouldError_updateMerchantDetails_whenServiceNotFound() throws ServiceNotFoundException {
        String name = "name";
        String telephoneNumber = "03069990000";
        String addressLine1 = "something";
        String addressLine2 = "something";
        String addressCity = "something";
        String addressPostcode = "something";
        String addressCountry = "something";
        String email = "merchant@example.com";

        UpdateMerchantDetailsRequest request = new UpdateMerchantDetailsRequest(
                name, telephoneNumber, addressLine1, addressLine2, addressCity, addressPostcode, addressCountry, email
        );
        ServiceEntity serviceEntity = mock(ServiceEntity.class);

        when(serviceDao.findByExternalId(NON_EXISTENT_SERVICE_EXTERNAL_ID)).thenReturn(Optional.empty());
        when(serviceEntity.toService()).thenReturn(Service.from());

        Service service = updater.doUpdateMerchantDetails(NON_EXISTENT_SERVICE_EXTERNAL_ID, request);
    }

    @Test
    public void shouldSuccess_updateCustomBranding_whenBrandingProvided() {
        String serviceId = randomUuid();
        ServiceUpdateRequest request = mock(ServiceUpdateRequest.class);
        ServiceEntity serviceEntity = mock(ServiceEntity.class);
        Map<String, Object> customBranding = ImmutableMap.of("image_url", "image url", "css_url", "css url");

        when(request.getPath()).thenReturn("custom_branding");
        when(request.valueAsObject()).thenReturn(customBranding);
        when(serviceDao.findByExternalId(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(serviceEntity.toService()).thenReturn(Service.from());

        Optional<Service> maybeService = updater.doUpdate(serviceId, request);

        assertThat(maybeService.isPresent(), is(true));
        verify(serviceEntity, times(1)).setCustomBranding(customBranding);
        verify(serviceDao, times(1)).merge(serviceEntity);
    }

    @Test
    public void shouldSuccess_updateCustomBranding_whenBrandingNotProvided() {
        String serviceId = randomUuid();
        ServiceUpdateRequest request = mock(ServiceUpdateRequest.class);
        ServiceEntity serviceEntity = mock(ServiceEntity.class);

        when(request.getPath()).thenReturn("custom_branding");
        when(request.valueAsObject()).thenReturn(null);
        when(serviceDao.findByExternalId(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(serviceEntity.toService()).thenReturn(Service.from());

        Optional<Service> maybeService = updater.doUpdate(serviceId, request);

        assertThat(maybeService.isPresent(), is(true));
        verify(serviceEntity, times(1)).setCustomBranding(null);
        verify(serviceDao, times(1)).merge(serviceEntity);
    }

    @Test
    public void shouldSuccess_whenAddGatewayAccountToService() {
        String serviceId = randomUuid();
        ServiceUpdateRequest request = mock(ServiceUpdateRequest.class);
        ServiceEntity serviceEntity = mock(ServiceEntity.class);
        List<String> gatewayAccountIdsToUpdate = asList("1", "2");

        when(request.getPath()).thenReturn("gateway_account_ids");
        when(request.valueAsList()).thenReturn(gatewayAccountIdsToUpdate);
        when(serviceDao.findByExternalId(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(serviceDao.checkIfGatewayAccountsUsed(gatewayAccountIdsToUpdate)).thenReturn(false);
        when(serviceEntity.toService()).thenReturn(Service.from());

        Optional<Service> maybeService = updater.doUpdate(serviceId, request);

        assertThat(maybeService.isPresent(), is(true));
        verify(serviceEntity, times(1)).addGatewayAccountIds(gatewayAccountIdsToUpdate.toArray(new String[0]));
        verify(serviceDao, times(1)).merge(serviceEntity);
    }

    @Test(expected = WebApplicationException.class)
    public void shouldError_IfAGatewayAccountAlreadyAssignedToAService() {
        String serviceId = randomUuid();
        ServiceUpdateRequest request = mock(ServiceUpdateRequest.class);
        ServiceEntity serviceEntity = mock(ServiceEntity.class);
        List<String> gatewayAccountIdsToUpdate = asList("1", "2");

        when(request.getPath()).thenReturn("gateway_account_ids");
        when(request.valueAsList()).thenReturn(gatewayAccountIdsToUpdate);
        when(serviceDao.findByExternalId(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(serviceDao.checkIfGatewayAccountsUsed(gatewayAccountIdsToUpdate)).thenReturn(true);

        Optional<Service> maybeService = updater.doUpdate(serviceId, request);

        assertThat(maybeService.isPresent(), is(false));
        verify(serviceEntity, times(0)).addGatewayAccountIds(gatewayAccountIdsToUpdate.toArray(new String[0]));
        verify(serviceDao, times(0)).merge(serviceEntity);
    }

    @Test
    public void shouldUpdateServiceNameSuccessfully() {
        String serviceId = randomUuid();
        String nameToUpdate = "new-cy-name";
        ServiceUpdateRequest request = ServiceUpdateRequest.from(new ObjectNode(JsonNodeFactory.instance, ImmutableMap.of(
                "path", new TextNode("service_name"),
                "value", new ObjectNode(JsonNodeFactory.instance, ImmutableMap.of("cy", new TextNode(nameToUpdate))),
                "op", new TextNode("replace"))));
        ServiceEntity serviceEntity = mock(ServiceEntity.class);

        when(serviceDao.findByExternalId(serviceId)).thenReturn(Optional.of(serviceEntity));
        when(serviceEntity.toService()).thenReturn(Service.from());

        InOrder inOrder = Mockito.inOrder(serviceDao, serviceEntity);

        Optional<Service> maybeService = updater.doUpdate(serviceId, request);

        assertThat(maybeService.isPresent(), is(true));
        ServiceNameEntity serviceNameEntity = ServiceNameEntity.from(SupportedLanguage.WELSH, nameToUpdate);
        inOrder.verify(serviceDao).findByExternalId(serviceId);
        inOrder.verify(serviceDao).merge(serviceEntity);
        inOrder.verify(serviceEntity).toService();
        verify(serviceEntity).addOrUpdateServiceName(serviceNameEntity);
    }
}
