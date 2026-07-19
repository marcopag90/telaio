package com.paganbit.telaio.web.exception;

import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.exception.DalEntityNotFoundException;
import com.paganbit.telaio.core.exception.DalEntityValidationException;
import com.paganbit.telaio.core.exception.DalNotFoundException;
import com.paganbit.telaio.core.exception.DalRegistryException;
import com.paganbit.telaio.rest.contract.DalIdCodecException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(classes = {
    TelaioWebExceptionHandler.class,
    TelaioWebExceptionHandlerTest.RestExceptionHandlerController.class
})
@WebMvcTest
@AutoConfigureMockMvc(addFilters = false)
class TelaioWebExceptionHandlerTest {

    private static final String ENTITY_VALIDATION_EXCEPTION = "/entity-validation-exception";
    private static final String OPERATION_NOT_EXPOSED_405 = "/operation-not-exposed-405";
    private static final String OPERATION_NOT_EXPOSED_404 = "/operation-not-exposed-404";
    private static final String ENTITY_NOT_FOUND_EXCEPTION = "/entity-not-found-exception";
    private static final String DAL_NOT_FOUND_EXCEPTION = "/dal-not-found-exception";
    private static final String RESOURCE_NOT_FOUND_EXCEPTION = "/resource-not-found-exception";
    private static final String REGISTRY_EXCEPTION = "/registry-exception";
    private static final String OPTIMISTIC_LOCK_EXCEPTION = "/optimistic-lock-exception";
    private static final String MALFORMED_ID_EXCEPTION = "/malformed-id-exception";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void handleDalEntityValidationException() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(ENTITY_VALIDATION_EXCEPTION))
            .andExpect(MockMvcResultMatchers.status().isBadRequest())
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
        mockMvc.perform(MockMvcRequestBuilders.get(OPERATION_NOT_EXPOSED_405))
            .andExpect(MockMvcResultMatchers.status().isMethodNotAllowed())
            .andExpect(header().string("Allow", "GET"))
            .andExpect(content().string(""));
    }

    @Test
    void handleOperationNotExposed_withoutSiblings_shouldReturn404() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(OPERATION_NOT_EXPOSED_404))
            .andExpect(MockMvcResultMatchers.status().isNotFound())
            .andExpect(content().string(""));
    }

    @Test
    void handleEntityNotFound_shouldReturn404ProblemDetail() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(ENTITY_NOT_FOUND_EXCEPTION))
            .andExpect(MockMvcResultMatchers.status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Not Found"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("Object was not found for id: [42]"));
    }

    @Test
    void handleDalNotFound_shouldReturn404ProblemDetail() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(DAL_NOT_FOUND_EXCEPTION))
            .andExpect(MockMvcResultMatchers.status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("DAL service not found: feed"));
    }

    @Test
    void handleResourceNotFound_shouldReturn404ProblemDetail() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(RESOURCE_NOT_FOUND_EXCEPTION))
            .andExpect(MockMvcResultMatchers.status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("Resource not found with ID: 7"));
    }

    @Test
    void handleOptimisticLockingFailure_shouldReturn409ProblemDetailWithGenericDetail() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(OPTIMISTIC_LOCK_EXCEPTION))
            .andExpect(MockMvcResultMatchers.status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Conflict"))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.detail").value("The resource was modified concurrently; re-read and retry"));
    }

    @Test
    void handleMalformedId_shouldReturn400ProblemDetailWithGenericDetail() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(MALFORMED_ID_EXCEPTION))
            .andExpect(MockMvcResultMatchers.status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Bad Request"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Malformed resource identifier"));
    }

    @Test
    void handleRegistryException_shouldReturn500ProblemDetailWithoutLeakingInternals() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(REGISTRY_EXCEPTION))
            .andExpect(MockMvcResultMatchers.status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Internal Server Error"))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @RestController
    static class RestExceptionHandlerController {

        @GetMapping(ENTITY_VALIDATION_EXCEPTION)
        public void throwDalEntityValidationException() {
            throw new DalEntityValidationException(List.of(
                new FieldError("objectName", "fieldName", "rejectedValue", false, null, null, "fieldMessage")
            ));
        }

        @GetMapping(OPERATION_NOT_EXPOSED_405)
        public void throwOperationNotExposedWithSiblings() {
            throw new DalOperationNotExposedException(
                "feed", DalOperationType.DELETE, Set.of(HttpMethod.GET), false);
        }

        @GetMapping(OPERATION_NOT_EXPOSED_404)
        public void throwOperationNotExposedWithoutSiblings() {
            throw new DalOperationNotExposedException(
                "feed", DalOperationType.READ_ONE, Set.of(), true);
        }

        @GetMapping(ENTITY_NOT_FOUND_EXCEPTION)
        public void throwDalEntityNotFoundException() {
            throw new DalEntityNotFoundException(Object.class, 42L);
        }

        @GetMapping(DAL_NOT_FOUND_EXCEPTION)
        public void throwDalNotFoundException() {
            throw new DalNotFoundException("feed");
        }

        @GetMapping(RESOURCE_NOT_FOUND_EXCEPTION)
        public void throwDalResourceNotFoundException() {
            throw new DalResourceNotFoundException(7);
        }

        @GetMapping(REGISTRY_EXCEPTION)
        public void throwDalRegistryException() {
            throw new DalRegistryException("boom");
        }

        @GetMapping(MALFORMED_ID_EXCEPTION)
        public void throwDalIdCodecException() {
            throw new DalIdCodecException("Failed to decode composite ID from Base64 (length 10)",
                new IllegalArgumentException("Illegal base64 character"));
        }

        @GetMapping(OPTIMISTIC_LOCK_EXCEPTION)
        public void throwOptimisticLockingFailureException() {
            // Parent of Spring's ObjectOptimisticLockingFailureException (the translation of a
            // JPA OptimisticLockException on a versioned entity) — the type the handler maps.
            throw new OptimisticLockingFailureException(
                "Row was updated or deleted by another transaction");
        }
    }
}