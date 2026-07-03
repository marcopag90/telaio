package io.paganbit.telaio.security.adapter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import io.paganbit.telaio.core.adapter.DalOperationType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class JsonViewDalRbacAdapterTest {

    @Mock
    private Authentication auth;

    private TestJsonViewAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TestJsonViewAdapter();
        adapter.setObjectMapper(JsonMapper.builder().build());
    }

    @Test
    void output_publicRole_keepsOnlyPublicView() {
        doReturn(List.of(Roles.PUBLIC)).when(auth).getAuthorities();

        JsonNode result = (JsonNode) adapter.filterOutput(DalOperationType.READ, new Doc("T", "S", "H"), auth);

        assertEquals("T", result.get("title").asString());
        assertFalse(result.has("secret"), "secret is Internal-only");
        assertFalse(result.has("hidden"), "un-annotated field is excluded (secure by default)");
    }

    @Test
    void output_internalRole_inheritsPublicAndAddsInternal() {
        doReturn(List.of(Roles.INTERNAL)).when(auth).getAuthorities();

        JsonNode result = (JsonNode) adapter.filterOutput(DalOperationType.READ, new Doc("T", "S", "H"), auth);

        assertEquals("T", result.get("title").asString(), "Public field visible in Internal view (inheritance)");
        assertEquals("S", result.get("secret").asString());
        assertFalse(result.has("hidden"));
    }

    @Test
    void output_noMatchingRole_deniesEverything() {
        doReturn(List.of(new SimpleGrantedAuthority("guest"))).when(auth).getAuthorities();

        JsonNode result = (JsonNode) adapter.filterOutput(DalOperationType.READ, new Doc("T", "S", "H"), auth);

        assertTrue(result.isEmpty(), "no field is exposed when no role matches");
    }

    @Test
    void output_keepsReadOnlyPropertyVisibleInView() {
        // Regression: a read-only property (e.g., a generated id) that IS in the active view must survive
        // output filtering. Serializing-then-rebinding to an entity would drop it, because Jackson
        // ignores @JsonProperty(access = READ_ONLY) on deserialization.
        ReadOnlyAdapter readOnlyAdapter = new ReadOnlyAdapter();
        readOnlyAdapter.setObjectMapper(JsonMapper.builder().build());
        doReturn(List.of(Roles.PUBLIC)).when(auth).getAuthorities();

        JsonNode result = (JsonNode) readOnlyAdapter.filterOutput(
            DalOperationType.READ, new ReadOnlyDoc(42L, "T"), auth);

        assertEquals(42L, result.get("id").asLong(), "read-only id in view must be returned");
        assertEquals("T", result.get("title").asString());
    }

    @Test
    void input_publicRole_keepsOnlyPublicWritable() {
        doReturn(List.of(Roles.PUBLIC)).when(auth).getAuthorities();

        Map<String, Object> input = new HashMap<>();
        input.put("title", "T");
        input.put("secret", "S");
        input.put("hidden", "H");

        var result = adapter.filterInput(DalOperationType.CREATE, input, auth);

        assertEquals(1, result.size());
        assertTrue(result.containsKey("title"));
        assertFalse(result.containsKey("secret"));
        assertFalse(result.containsKey("hidden"));
    }

    @Test
    void input_internalRole_keepsPublicAndInternal() {
        doReturn(List.of(Roles.INTERNAL)).when(auth).getAuthorities();

        Map<String, Object> input = new HashMap<>();
        input.put("title", "T");
        input.put("secret", "S");
        input.put("hidden", "H");

        var result = adapter.filterInput(DalOperationType.UPDATE, input, auth);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("title"));
        assertTrue(result.containsKey("secret"));
        assertFalse(result.containsKey("hidden"));
    }

    @Test
    void input_readOperation_passesThroughUnchanged() {
        Map<String, Object> input = Map.of("title", "T");
        assertSame(input, adapter.filterInput(DalOperationType.READ, input, auth));
    }

    @Test
    void input_emptyPayload_passesThroughUnchanged() {
        Map<String, Object> input = Map.of();
        assertSame(input, adapter.filterInput(DalOperationType.CREATE, input, auth));
    }

    @Test
    void input_noMatchingRole_deniesAllFields() {
        // resolveView returns null (no matching role) → no field is in any view, so the payload is emptied.
        doReturn(List.of(new SimpleGrantedAuthority("guest"))).when(auth).getAuthorities();

        Map<String, Object> input = new HashMap<>();
        input.put("title", "T");
        input.put("secret", "S");

        var result = adapter.filterInput(DalOperationType.CREATE, input, auth);

        assertTrue(result.isEmpty());
    }

    @Test
    void input_prunesNestedObjectAndCollectionFields() {
        // Exercises the recursive descent: a nested object, a nested collection, and the container-type
        // unwrapping that resolves the element bean class for both.
        NestedAdapter nestedAdapter = new NestedAdapter();
        nestedAdapter.setObjectMapper(JsonMapper.builder().build());
        doReturn(List.of(Roles.PUBLIC)).when(auth).getAuthorities();

        Map<String, Object> input = new HashMap<>();
        input.put("name", "N");
        input.put("child", member("visible", "internalOnly", "unmapped"));
        input.put("members", List.of(
            member("a", "x", "y"),
            member("b", "x", "y")
        ));

        var result = nestedAdapter.filterInput(DalOperationType.CREATE, input, auth);

        assertTrue(result.containsKey("name"));

        Map<?, ?> child = (Map<?, ?>) result.get("child");
        assertTrue(child.containsKey("visible"));
        assertFalse(child.containsKey("secret"), "Internal-only nested field excluded from Public view");
        assertFalse(child.containsKey("hidden"), "un-annotated nested field excluded (secure by default)");

        List<?> members = (List<?>) result.get("members");
        assertEquals(2, members.size());
        Map<?, ?> first = (Map<?, ?>) members.getFirst();
        assertTrue(first.containsKey("visible"));
        assertFalse(first.containsKey("secret"));
        assertFalse(first.containsKey("hidden"));
    }

    @Test
    void output_nullEntity_returnsNull() {
        assertNull(adapter.filterOutput(DalOperationType.READ, null, auth));
    }

    @Test
    void output_serializationFailure_isWrappedInIllegalStateException() {
        // A property whose getter throws makes Jackson serialization fail; the adapter must surface it as
        // an IllegalStateException rather than leak the raw databind exception.
        ExplodingAdapter explodingAdapter = new ExplodingAdapter();
        explodingAdapter.setObjectMapper(JsonMapper.builder().build());
        ExplodingDoc entity = new ExplodingDoc();

        assertThrows(IllegalStateException.class,
            () -> explodingAdapter.filterOutput(DalOperationType.READ, entity, auth));
    }

    @Test
    void shouldThrowWhenExposedTypeCannotBeResolved() {
        // A raw subclass carries no resolvable type argument; the type is resolved in the constructor.
        assertThrows(IllegalStateException.class, RawJsonViewAdapter::new);
    }

    private static Map<String, Object> member(String visible, String secret, String hidden) {
        Map<String, Object> map = new HashMap<>();
        map.put("visible", visible);
        map.put("secret", secret);
        map.put("hidden", hidden);
        return map;
    }

    // ------------------------------------------------------------------------
    // Support types
    // ------------------------------------------------------------------------

    private static final class Views {
        static class Public {
        }

        static class Internal extends Public {
        }
    }

    private static final class Roles {
        static final GrantedAuthority PUBLIC = new SimpleGrantedAuthority("public");
        static final GrantedAuthority INTERNAL = new SimpleGrantedAuthority("internal");
    }

    private static class TestJsonViewAdapter extends JsonViewDalRbacAdapter<Doc> {
        @Override
        protected Class<?> resolveView(DalOperationType operation, Authentication authentication) {
            if (authentication.getAuthorities().contains(Roles.INTERNAL)) {
                return Views.Internal.class;
            }
            if (authentication.getAuthorities().contains(Roles.PUBLIC)) {
                return Views.Public.class;
            }
            return null;
        }
    }

    private static class ReadOnlyAdapter extends JsonViewDalRbacAdapter<ReadOnlyDoc> {
        @Override
        protected Class<?> resolveView(DalOperationType operation, Authentication authentication) {
            return authentication.getAuthorities().contains(Roles.PUBLIC) ? Views.Public.class : null;
        }
    }

    private static class NestedAdapter extends JsonViewDalRbacAdapter<Parent> {
        @Override
        protected Class<?> resolveView(DalOperationType operation, Authentication authentication) {
            return authentication.getAuthorities().contains(Roles.PUBLIC) ? Views.Public.class : null;
        }
    }

    private static class ExplodingAdapter extends JsonViewDalRbacAdapter<ExplodingDoc> {
        @Override
        protected Class<?> resolveView(DalOperationType operation, Authentication authentication) {
            return Views.Public.class;
        }
    }

    @SuppressWarnings("rawtypes")
    private static class RawJsonViewAdapter extends JsonViewDalRbacAdapter {
        @Override
        protected Class<?> resolveView(DalOperationType operation, Authentication authentication) {
            return null;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class Doc {
        @JsonView(Views.Public.class)
        private String title;
        @JsonView(Views.Internal.class)
        private String secret;
        private String hidden;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class ReadOnlyDoc {
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @JsonView(Views.Public.class)
        private Long id;
        @JsonView(Views.Public.class)
        private String title;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class Parent {
        @JsonView(Views.Public.class)
        private String name;
        @JsonView(Views.Public.class)
        private Member child;
        @JsonView(Views.Public.class)
        private List<Member> members;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class Member {
        @JsonView(Views.Public.class)
        private String visible;
        @JsonView(Views.Internal.class)
        private String secret;
        private String hidden;
    }

    static class ExplodingDoc {
        @JsonView(Views.Public.class)
        public String getBoom() {
            throw new IllegalStateException("boom");
        }
    }
}
