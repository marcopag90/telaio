package com.paganbit.telaio.web.exception;

import com.paganbit.telaio.core.Dal;
import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.core.adapter.DalOperationAdapterRegistry;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.exception.*;
import com.paganbit.telaio.core.registry.DalDefinitionEntry;
import com.paganbit.telaio.core.registry.DalManager;
import com.paganbit.telaio.rest.contract.DalIdCodecException;
import com.paganbit.telaio.web.DalRestApiV1Controller;
import com.paganbit.telaio.web.DalRestApiV1ControllerTestConfig;
import com.paganbit.telaio.web.annotation.DalWeb;
import com.paganbit.telaio.web.channel.DalChannels;
import com.turkraft.springfilter.converter.FilterStringConverter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Drives every handler of {@link TelaioWebExceptionHandler} and {@link TelaioAccessDeniedExceptionHandler}
 * through the real {@link DalRestApiV1Controller}, with the REST adapter of the {@code company} DAL mocked
 * to raise each exception: the advices are scoped to that controller, so a throwing test controller would
 * never reach them. The access-denied advice comes from the imported web auto-configuration (Spring
 * Security is on the test classpath). The last test proves the scoping on a foreign controller.
 */
@ContextConfiguration(classes = {
    DalRestApiV1Controller.class,
    DalRestApiV1ControllerTestConfig.class,
    TelaioWebExceptionHandler.class,
    TelaioWebExceptionHandlerTest.ForeignController.class,
})
@WebMvcTest(controllers = DalRestApiV1Controller.class)
@AutoConfigureMockMvc(addFilters = false)
class TelaioWebExceptionHandlerTest {

    private static final String DAL = "company";
    private static final String COLLECTION = "/dal/v1/" + DAL;
    private static final String ITEM = COLLECTION + "/1";
    private static final String SECRET_MESSAGE = "Not authorized to update entity with ID [42] in DAL [orders]";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DalManager dalManager;

    @MockitoBean
    private DalOperationAdapterRegistry adapterRegistry;

    @MockitoBean
    private FilterStringConverter filterStringConverter;

    private DalOperationAdapter<Object> adapter;

    @BeforeEach
    void setUp() {
        adapter = mock();
        doReturn(adapter).when(adapterRegistry).get(DalChannels.REST, DAL);
        doReturn(new DalRestApiV1ControllerTestConfig.MockDalService()).when(dalManager).getServiceByName(DAL);
    }

    @Test
    void handleDalEntityValidationException() throws Exception {
        doThrow(new DalEntityValidationException(List.of(
            new FieldError("objectName", "fieldName", "rejectedValue", false, null, null, "fieldMessage"))))
            .when(adapter).read(any(), any());

        mockMvc.perform(get(COLLECTION))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Bad Request"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Validation failed"))
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors").isNotEmpty())
            .andExpect(jsonPath("$.errors[0].object").value("objectName"))
            .andExpect(jsonPath("$.errors[0].field").value("fieldName"))
            .andExpect(jsonPath("$.errors[0].rejectValue").value("rejectedValue"))
            .andExpect(jsonPath("$.errors[0].message").value("fieldMessage"));
    }

    @Test
    void handleOperationNotExposed_withSiblings_shouldReturn405WithAllowHeader() throws Exception {
        doReturn(new DalDefinitionEntry(DAL, ReadOnlyDal.class)).when(dalManager).getDefinitionByName(DAL);
        doThrow(new DalOperationNotExposedException(DAL, DalOperationType.DELETE, DalChannels.REST))
            .when(adapter).delete(any());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(ITEM))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", "GET"))
            .andExpect(content().string(""));
    }

    @Test
    void handleOperationNotExposed_withoutSiblings_shouldReturn404() throws Exception {
        doReturn(new DalDefinitionEntry(DAL, AppendOnlyDal.class)).when(dalManager).getDefinitionByName(DAL);
        doThrow(new DalOperationNotExposedException(DAL, DalOperationType.READ_ONE, DalChannels.REST))
            .when(adapter).readOne(any());

        mockMvc.perform(get(ITEM))
            .andExpect(status().isNotFound())
            .andExpect(content().string(""));
    }

    @Test
    void handleEntityNotFound_shouldReturn404ProblemDetail() throws Exception {
        doThrow(new DalEntityNotFoundException(Object.class, 42L)).when(adapter).readOne(any());

        mockMvc.perform(get(ITEM))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Not Found"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("Object was not found for id: [42]"));
    }

    @Test
    void handleDalNotFound_shouldReturn404ProblemDetail() throws Exception {
        doThrow(new DalNotFoundException("feed")).when(adapterRegistry).get(DalChannels.REST, "feed");

        mockMvc.perform(get("/dal/v1/feed"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("DAL service not found: feed"));
    }

    @Test
    void handleOptimisticLockingFailure_shouldReturn409ProblemDetailWithGenericDetail() throws Exception {
        // Parent of Spring's ObjectOptimisticLockingFailureException (the translation of a JPA
        // OptimisticLockException on a versioned entity) — the type the handler maps.
        doThrow(new OptimisticLockingFailureException("Row was updated or deleted by another transaction"))
            .when(adapter).delete(any());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(ITEM))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Conflict"))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.detail").value("The resource was modified concurrently; re-read and retry"));
    }

    @Test
    void handleMalformedId_shouldReturn400ProblemDetailWithGenericDetail() throws Exception {
        doThrow(new DalIdCodecException("Failed to decode composite ID from Base64 (length 10)",
            new IllegalArgumentException("Illegal base64 character")))
            .when(adapter).readOne(any());

        mockMvc.perform(get(ITEM))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Bad Request"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Malformed resource identifier"));
    }

    @Test
    void handleInvalidFilter_shouldReturn400ProblemDetailWithGenericDetail() throws Exception {
        doThrow(DalInvalidFilterException.unknownField("nope", "nope")).when(adapter).read(any(), any());

        mockMvc.perform(get(COLLECTION))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Bad Request"))
            .andExpect(jsonPath("$.status").value(400))
            // Generic detail: the offending field name never reaches the client body.
            .andExpect(jsonPath("$.detail").value("Invalid filter expression"));
    }

    @Test
    void handleInvalidSort_shouldReturn400ProblemDetailWithGenericDetail() throws Exception {
        doThrow(DalInvalidSortException.unknownProperty("nope", "nope")).when(adapter).read(any(), any());

        mockMvc.perform(get(COLLECTION))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Bad Request"))
            .andExpect(jsonPath("$.status").value(400))
            // Generic detail: the offending property name never reaches the client body.
            .andExpect(jsonPath("$.detail").value("Invalid sort parameter"));
    }

    @Test
    void handleSortFieldNotReadable_shouldLandInTheSameHandlerWithTheSameGenericDetail() throws Exception {
        // The RBAC rejection must stay wire-indistinguishable from an unknown sort property.
        doThrow(new DalSortFieldNotReadableException("cost_price")).when(adapter).read(any(), any());

        mockMvc.perform(get(COLLECTION))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Invalid sort parameter"));
    }

    @Test
    void handleRegistryException_shouldReturn500ProblemDetailWithoutLeakingInternals() throws Exception {
        doThrow(new DalRegistryException("boom")).when(adapterRegistry).get(DalChannels.REST, "broken");

        mockMvc.perform(get("/dal/v1/broken"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Internal Server Error"))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void handleAccessDenied_shouldReturnGeneric403ProblemDetail() throws Exception {
        doThrow(new AccessDeniedException("denied")).when(adapter).read(any(), any());

        mockMvc.perform(get(COLLECTION))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Forbidden"))
            .andExpect(jsonPath("$.status").value(403))
            // No detail: the authorization failure must not reveal why it was denied (OWASP / CWE-209).
            .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    void handleAccessDenied_subclass_shouldAlsoMapAndNotLeakItsMessage() throws Exception {
        // Mirrors telaio-security's DalAccessDeniedException, whose resolver message must stay internal.
        doThrow(new AccessDeniedException(SECRET_MESSAGE) {
        }).when(adapter).read(any(), any());

        mockMvc.perform(get(COLLECTION))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("orders"))));
    }

    @Test
    void advices_shouldNotApplyToAForeignController() {
        // The same exceptions raised by a host controller are not Telaio's to translate: they propagate
        // untouched (MockMvc surfaces them as a ServletException wrapping the original).
        assertThatThrownBy(() -> mockMvc.perform(get(ForeignController.NOT_FOUND)))
            .isInstanceOf(ServletException.class)
            .hasCauseInstanceOf(DalEntityNotFoundException.class);
        assertThatThrownBy(() -> mockMvc.perform(get(ForeignController.ACCESS_DENIED)))
            .isInstanceOf(ServletException.class)
            .hasCauseInstanceOf(AccessDeniedException.class);
    }

    @RestController
    static class ForeignController {

        static final String NOT_FOUND = "/foreign/not-found";
        static final String ACCESS_DENIED = "/foreign/access-denied";

        @GetMapping(NOT_FOUND)
        public void notFound() {
            throw new DalEntityNotFoundException(Object.class, 1L);
        }

        @GetMapping(ACCESS_DENIED)
        public void accessDenied() {
            throw new AccessDeniedException("denied");
        }
    }

    @DalWeb(operations = {DalOperationType.READ, DalOperationType.READ_ONE})
    abstract static class ReadOnlyDal implements Dal<Object, Long> {
    }

    @DalWeb(operations = {DalOperationType.CREATE, DalOperationType.READ})
    abstract static class AppendOnlyDal implements Dal<Object, Long> {
    }
}
