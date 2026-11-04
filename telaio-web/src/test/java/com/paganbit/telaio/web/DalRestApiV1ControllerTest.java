package com.paganbit.telaio.web;

import com.paganbit.telaio.core.adapter.DalOperationAdapter;
import com.paganbit.telaio.core.adapter.DalOperationAdapterRegistry;
import com.paganbit.telaio.core.adapter.DalOperationType;
import com.paganbit.telaio.core.adapter.DelegatingDalOperationAdapter;
import com.paganbit.telaio.core.exception.DalInvalidFilterException;
import com.paganbit.telaio.core.exception.DalInvalidSortException;
import com.paganbit.telaio.core.exception.DalNotFoundException;
import com.paganbit.telaio.core.exception.DalOperationNotExposedException;
import com.paganbit.telaio.core.registry.DalDefinitionEntry;
import com.paganbit.telaio.core.registry.DalManager;
import com.paganbit.telaio.web.annotation.DalWeb;
import com.paganbit.telaio.web.channel.DalChannels;
import com.paganbit.telaio.web.exception.TelaioWebExceptionHandler;
import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.parser.InvalidSyntaxException;
import com.turkraft.springfilter.parser.node.FieldNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ContextConfiguration(classes = {
    DalRestApiV1Controller.class,
    DalRestApiV1ControllerTestConfig.class,
    TelaioWebExceptionHandler.class,
})
@WebMvcTest(controllers = DalRestApiV1Controller.class)
@AutoConfigureMockMvc(addFilters = false)
class DalRestApiV1ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DalManager dalManager;

    @MockitoBean
    private DalOperationAdapterRegistry adapterRegistry;

    @MockitoBean
    private FilterStringConverter filterStringConverter;

    @BeforeEach
    void setUp() {
        doReturn(new DalRestApiV1ControllerTestConfig.MockDalService())
            .when(dalManager).getServiceByName("company");

        var adapter = new DelegatingDalOperationAdapter<>(
            new DalRestApiV1ControllerTestConfig.MockDalService()
        );
        doReturn(adapter).when(adapterRegistry).get(DalChannels.REST, "company");
        doThrow(new DalNotFoundException("unknown")).when(adapterRegistry).get(DalChannels.REST, "unknown");
    }

    @Test
    void createCompany_shouldReturnCreated() throws Exception {
        mockMvc.perform(post("/dal/v1/company")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Test Company\"}")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void readCompanies_shouldReturnPage() throws Exception {
        // Explicit paging params, so the resolved Pageable (and thus page.size) is deterministic
        // and independent of the default page size.
        mockMvc.perform(get("/dal/v1/company")
                .param("page", "0")
                .param("size", "5")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.content[0].name").value("Company One"))
            .andExpect(jsonPath("$.content[1].id").value(2))
            .andExpect(jsonPath("$.content[1].name").value("Company Two"))
            // PagedModel wire shape: pagination metadata nested under "page" (owned by Telaio,
            // independent of the host's Spring Data page serialization mode).
            .andExpect(jsonPath("$.page.size").value(5))
            .andExpect(jsonPath("$.page.number").value(0))
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @Test
    void readOneCompany_shouldReturnEntity() throws Exception {
        mockMvc.perform(get("/dal/v1/company/1")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updateCompany_shouldReturnOk() throws Exception {
        mockMvc.perform(patch("/dal/v1/company/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Updated Company\"}")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    void updateCompany_shouldReturnNoContent() throws Exception {
        DalRestApiV1ControllerTestConfig.MockDalService.setMockUpdateWithNoContent(true);

        mockMvc.perform(patch("/dal/v1/company/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Updated Company\"}")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        DalRestApiV1ControllerTestConfig.MockDalService.setMockUpdateWithNoContent(false);
    }

    @Test
    void deleteCompany_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/dal/v1/company/1"))
            .andExpect(status().isNoContent());
    }

    @Test
    void readCompanies_malformedFilter_shouldReturnBadRequest() throws Exception {
        doThrow(new InvalidSyntaxException("mismatched input '(' expecting {...}"))
            .when(filterStringConverter).convert("(((");

        mockMvc.perform(get("/dal/v1/company")
                .param("q", "(((")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Malformed filter expression"));
    }

    @Test
    void readCompanies_unknownFilterFunction_shouldReturnBadRequest() throws Exception {
        // Turkraft's parser rejects a function name it has no definition for with an
        // UnsupportedOperationException; that is client input, not a server fault.
        doThrow(new UnsupportedOperationException("Unrecognized function `nosuchfn`"))
            .when(filterStringConverter).convert("nosuchfn(name) : 1");

        mockMvc.perform(get("/dal/v1/company")
                .param("q", "nosuchfn(name) : 1")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Invalid filter expression"));
    }

    @Test
    void readCompanies_filterRejectedByBackend_shouldReturnBadRequest() throws Exception {
        // A well-formed filter the backend cannot apply (unknown field, unconvertible literal) surfaces
        // from the adapter as DalInvalidFilterException — on every backend — and maps to a generic 400.
        FieldNode unknownField = new FieldNode("nope");
        doReturn(unknownField).when(filterStringConverter).convert("nope:1");
        @SuppressWarnings("unchecked")
        DelegatingDalOperationAdapter<Long> rejecting = mock(DelegatingDalOperationAdapter.class);
        doThrow(DalInvalidFilterException.unknownField("nope", "nope"))
            .when(rejecting).read(any(), any());
        doReturn(rejecting).when(adapterRegistry).get(DalChannels.REST, "strict");

        mockMvc.perform(get("/dal/v1/strict")
                .param("q", "nope:1")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Invalid filter expression"));
    }

    @Test
    void readCompanies_sortRejectedByBackend_shouldReturnBadRequest() throws Exception {
        // A sort the read cannot honor (unknown property, hidden property) surfaces from the adapter as
        // DalInvalidSortException — on every backend and from the security interceptor — and maps to a
        // generic 400 distinct from the filter detail.
        @SuppressWarnings("unchecked")
        DelegatingDalOperationAdapter<Long> rejecting = mock(DelegatingDalOperationAdapter.class);
        doThrow(DalInvalidSortException.unknownProperty("nope", "nope"))
            .when(rejecting).read(any(), any());
        doReturn(rejecting).when(adapterRegistry).get(DalChannels.REST, "strict");

        mockMvc.perform(get("/dal/v1/strict")
                .param("sort", "nope,desc")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Invalid sort parameter"));
    }

    @Test
    void readUnknownDal_shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/dal/v1/unknown")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("DAL service not found: unknown"));
    }

    @Test
    void writeOnReadOnlyDal_shouldReturn405WithAllowListingTheExposedSiblings() throws Exception {
        // The REST chain of a @DalWeb({READ, READ_ONE}) DAL rejects DELETE with core's exception; the
        // handler recomputes what the item URI still serves from the same @DalWeb: GET only.
        DalOperationAdapter<Object> adapter = mock();
        doThrow(new DalOperationNotExposedException("articles", DalOperationType.DELETE, DalChannels.REST))
            .when(adapter).delete(any());
        doReturn(adapter).when(adapterRegistry).get(DalChannels.REST, "articles");
        doReturn(new DalDefinitionEntry("articles", ReadOnlyDalService.class))
            .when(dalManager).getDefinitionByName("articles");
        doReturn(new DalRestApiV1ControllerTestConfig.MockDalService())
            .when(dalManager).getServiceByName("articles");

        mockMvc.perform(delete("/dal/v1/articles/1"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", "GET"))
            .andExpect(content().string(""));
    }

    @Test
    void itemRouteOfAppendOnlyDal_shouldReturn404WithEmptyBody() throws Exception {
        // @DalWeb({CREATE, READ}): the item URI serves nothing, so its existence is not revealed.
        DalOperationAdapter<Object> adapter = mock();
        doThrow(new DalOperationNotExposedException("feed", DalOperationType.READ_ONE, DalChannels.REST))
            .when(adapter).readOne(any());
        doReturn(adapter).when(adapterRegistry).get(DalChannels.REST, "feed");
        doReturn(new DalDefinitionEntry("feed", AppendOnlyDalService.class))
            .when(dalManager).getDefinitionByName("feed");
        doReturn(new DalRestApiV1ControllerTestConfig.MockDalService())
            .when(dalManager).getServiceByName("feed");

        mockMvc.perform(get("/dal/v1/feed/1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(content().string(""));
    }

    @Test
    void dalHiddenFromRest_shouldAnswerLikeAnUnknownName() throws Exception {
        // @DalWeb({}) builds no REST chain: the registry miss is the same 404 an unknown name gets.
        doThrow(new DalNotFoundException("hidden")).when(adapterRegistry).get(DalChannels.REST, "hidden");

        mockMvc.perform(get("/dal/v1/hidden").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("DAL service not found: hidden"));
    }

    @Test
    void updateCompany_concurrentModification_shouldReturnConflict() throws Exception {
        DalOperationAdapter<Object> adapter = mock();
        doThrow(new OptimisticLockingFailureException("Row was updated by another transaction"))
            .when(adapter).update(any(), anyMap());
        doReturn(adapter).when(adapterRegistry).get(DalChannels.REST, "company");

        mockMvc.perform(patch("/dal/v1/company/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Updated Company\"}")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.detail")
                .value("The resource was modified concurrently; re-read and retry"));
    }

    @Test
    void deleteCompany_concurrentModification_shouldReturnConflict() throws Exception {
        DalOperationAdapter<Object> adapter = mock();
        doThrow(new OptimisticLockingFailureException("Row was updated or deleted by another transaction"))
            .when(adapter).delete(any());
        doReturn(adapter).when(adapterRegistry).get(DalChannels.REST, "company");

        mockMvc.perform(delete("/dal/v1/company/1"))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.detail")
                .value("The resource was modified concurrently; re-read and retry"));
    }

    @DalWeb(operations = {DalOperationType.READ, DalOperationType.READ_ONE})
    static class ReadOnlyDalService extends DalRestApiV1ControllerTestConfig.MockDalService {
    }

    @DalWeb(operations = {DalOperationType.CREATE, DalOperationType.READ})
    static class AppendOnlyDalService extends DalRestApiV1ControllerTestConfig.MockDalService {
    }
}
