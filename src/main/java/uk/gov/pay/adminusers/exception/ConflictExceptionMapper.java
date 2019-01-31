package uk.gov.pay.adminusers.exception;

import com.google.common.collect.ImmutableMap;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import java.util.Collections;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

public class ConflictExceptionMapper implements ExceptionMapper<ConflictException> {

    @Override
    public Response toResponse(ConflictException exception) {
        ImmutableMap<String, List<String>> entity = ImmutableMap.of("errors", Collections.singletonList(exception.getMessage()));
        return Response
                .status(Response.Status.CONFLICT)
                .entity(entity)
                .type(APPLICATION_JSON_TYPE)
                .build();
    }
}