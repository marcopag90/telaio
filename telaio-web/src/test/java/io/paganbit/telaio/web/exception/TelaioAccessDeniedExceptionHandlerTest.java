package io.paganbit.telaio.web.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringJUnitWebConfig(classes = {
    TelaioAccessDeniedExceptionHandler.class,
    TelaioAccessDeniedExceptionHandlerTest.RestExceptionHandlerController.class
})
@WebMvcTest
@AutoConfigureMockMvc(addFilters = false)
class TelaioAccessDeniedExceptionHandlerTest {

    private static final String ACCESS_DENIED = "/access-denied";
    private static final String ACCESS_DENIED_SUBCLASS = "/access-denied-subclass";
    private static final String SECRET_MESSAGE = "Not authorized to update entity with ID [42] in DAL [orders]";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void handleAccessDenied_shouldReturnGeneric403ProblemDetail() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(ACCESS_DENIED))
            .andExpect(MockMvcResultMatchers.status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Forbidden"))
            .andExpect(jsonPath("$.status").value(403))
            // No detail: the authorization failure must not reveal why it was denied (OWASP / CWE-209).
            .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    void handleAccessDenied_subclass_shouldAlsoMapAndNotLeakItsMessage() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(ACCESS_DENIED_SUBCLASS))
            .andExpect(MockMvcResultMatchers.status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("orders"))));
    }

    @RestController
    static class RestExceptionHandlerController {

        @GetMapping(ACCESS_DENIED)
        public void throwAccessDenied() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping(ACCESS_DENIED_SUBCLASS)
        public void throwAccessDeniedSubclass() {
            // Mirrors telaio-security's DalAccessDeniedException, whose resolver message must stay internal.
            throw new AccessDeniedException(SECRET_MESSAGE) {
            };
        }
    }
}
