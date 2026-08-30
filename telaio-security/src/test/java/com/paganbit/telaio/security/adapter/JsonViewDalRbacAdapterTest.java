package com.paganbit.telaio.security.adapter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.paganbit.telaio.core.adapter.DalOperationType;
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
import java.util.Set;

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
    void input_unknownPayloadKey_isDropped() {
        // A payload key that maps to no introspected property must be pruned, not passed through.
        doReturn(List.of(Roles.PUBLIC)).when(auth).getAuthorities();

        Map<String, Object> input = new HashMap<>();
        input.put("title", "T");
        input.put("bogus", "B");

        var result = adapter.filterInput(DalOperationType.CREATE, input, auth);

        assertEquals(1, result.size());
        assertTrue(result.containsKey("title"));
        assertFalse(result.containsKey("bogus"));
    }

    @Test
    void input_arrayOfScalars_isKeptUnchanged() {
        // Scalar array elements have no nested properties to prune and must survive as-is.
        NestedAdapter nestedAdapter = new NestedAdapter();
        nestedAdapter.setObjectMapper(JsonMapper.builder().build());
        doReturn(List.of(Roles.PUBLIC)).when(auth).getAuthorities();

        Map<String, Object> input = new HashMap<>();
        input.put("name", "N");
        input.put("tags", List.of("a", "b"));

        var result = nestedAdapter.filterInput(DalOperationType.CREATE, input, auth);

        assertEquals(List.of("a", "b"), result.get("tags"));
    }

    @Test
    void input_getterOnlyProperty_isIntrospectedViaGetter() {
        // A getter-only property has no mutator and no field: viewsOf must skip the null members and
        // read @JsonView off the getter. Introspection reads annotations only, so the getter never runs.
        // Keeping the key in the payload is harmless: with no mutator the value can never bind to the
        // bean — this pins the introspection fallback (mutator -> field -> primary -> getter), not a write.
        ExplodingAdapter explodingAdapter = new ExplodingAdapter();
        explodingAdapter.setObjectMapper(JsonMapper.builder().build());

        Map<String, Object> input = new HashMap<>();
        input.put("boom", "defused");

        var result = explodingAdapter.filterInput(DalOperationType.CREATE, input, auth);

        assertTrue(result.containsKey("boom"), "getter-only property in view must be writable-visible");
    }

    @Test
    void propertyInfo_equalsHonorsTypeAndViewContent() {
        var mapper = JsonMapper.builder().build();
        var stringType = mapper.constructType(String.class);
        var info = new JsonViewDalRbacAdapter.PropertyInfo(
            stringType, new Class<?>[]{Views.Public.class});
        var sameContent = new JsonViewDalRbacAdapter.PropertyInfo(
            stringType, new Class<?>[]{Views.Public.class});
        var otherType = new JsonViewDalRbacAdapter.PropertyInfo(
            mapper.constructType(Long.class), new Class<?>[]{Views.Public.class});
        var otherViews = new JsonViewDalRbacAdapter.PropertyInfo(
            stringType, new Class<?>[]{Views.Internal.class});

        // The Object-typed locals keep the reflexive and cross-type cases free of IDE/Sonar
        // self-comparison and inconvertible-type warnings: both branches belong to the equals
        // contract of the handwritten implementation and must stay exercised.
        Object notAPropertyInfo = "not a PropertyInfo";
        assertEquals(info, info);
        assertEquals(info, sameContent);
        assertNotEquals(info, otherType);
        assertNotEquals(info, otherViews);
        // info must be the FIRST argument: JUnit invokes equals on it, and the point is exercising
        // PropertyInfo.equals' non-PropertyInfo rejection — swapped, String.equals runs instead.
        assertNotEquals(info, notAPropertyInfo);
    }

    @Test
    void propertyInfo_hashCodeMatchesForEqualContent() {
        var mapper = JsonMapper.builder().build();
        var stringType = mapper.constructType(String.class);
        var info = new JsonViewDalRbacAdapter.PropertyInfo(
            stringType, new Class<?>[]{Views.Public.class});
        var sameContent = new JsonViewDalRbacAdapter.PropertyInfo(
            stringType, new Class<?>[]{Views.Public.class});

        assertEquals(info.hashCode(), sameContent.hashCode());
    }

    @Test
    void propertyInfo_toStringListsTypeAndViews() {
        var mapper = JsonMapper.builder().build();
        var info = new JsonViewDalRbacAdapter.PropertyInfo(
            mapper.constructType(String.class), new Class<?>[]{Views.Public.class});

        String text = info.toString();

        assertTrue(text.startsWith("PropertyInfo[type="));
        assertTrue(text.contains(Views.Public.class.getName()));
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

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> explodingAdapter.filterOutput(DalOperationType.READ, entity, auth));
        assertTrue(exception.getMessage().startsWith("Failed to filter output for type"));
    }

    @Test
    void shouldThrowWhenExposedTypeCannotBeResolved() {
        // A raw subclass carries no resolvable type argument; the type is resolved in the constructor.
        assertThrows(IllegalStateException.class, RawJsonViewAdapter::new);
    }

    // ------------------------------------------------------------------------
    // Filter-field check (reads)
    // ------------------------------------------------------------------------

    @Test
    void filter_publicRole_mayReferenceOnlyPublicProperties() {
        doReturn(List.of(Roles.PUBLIC)).when(auth).getAuthorities();

        assertTrue(adapter.canFilterOn("title", auth));
        assertFalse(adapter.canFilterOn("secret", auth), "Internal-only: filtering is denied");
        assertFalse(adapter.canFilterOn("hidden", auth), "no @JsonView: visible in no view, like on output");
    }

    @Test
    void filter_internalRole_inheritsPublicAndAddsInternal() {
        doReturn(List.of(Roles.INTERNAL)).when(auth).getAuthorities();

        assertTrue(adapter.canFilterOn("title", auth));
        assertTrue(adapter.canFilterOn("secret", auth));
        assertFalse(adapter.canFilterOn("hidden", auth));
    }

    @Test
    void filter_withoutView_deniesEveryField() {
        doReturn(List.of()).when(auth).getAuthorities();

        assertFalse(adapter.canFilterOn("title", auth));
    }

    @Test
    void filter_nestedPath_isCheckedAgainstTheElementBean() {
        NestedAdapter nested = new NestedAdapter();
        nested.setObjectMapper(JsonMapper.builder().build());
        doReturn(List.of(Roles.PUBLIC)).when(auth).getAuthorities();

        assertTrue(nested.canFilterOn("child.visible", auth));
        assertFalse(nested.canFilterOn("child.secret", auth));
        assertTrue(nested.canFilterOn("members.visible", auth), "collections resolve against their element type");
        assertFalse(nested.canFilterOn("members.hidden", auth));
        assertTrue(nested.canFilterOn("tags", auth));
        assertFalse(nested.canFilterOn("tags.length", auth), "a segment applied to a scalar never resolves");
        assertFalse(nested.canFilterOn("child.nope", auth));
        assertFalse(nested.canFilterOn("nope", auth));
    }

    @Test
    void filter_renamedProperty_acceptsWireAndJavaNameAlike() {
        // A filter may spell a property by its @JsonProperty wire name or by its Java name (the backends
        // accept both), so the view must be enforced under both spellings — or the Java name would bypass it.
        AliasedAdapter aliased = new AliasedAdapter();
        aliased.setObjectMapper(JsonMapper.builder().build());
        doReturn(List.of(Roles.PUBLIC)).when(auth).getAuthorities();

        assertTrue(aliased.canFilterOn("full_name", auth));
        assertTrue(aliased.canFilterOn("name", auth));
        assertFalse(aliased.canFilterOn("secret_code", auth));
        assertFalse(aliased.canFilterOn("secret", auth));
    }

    @Test
    void filter_writeOnlyPropertyInTheView_isDeniedLikeOnOutput() {
        // In the view, yet never serialized: the response does not carry it, so neither may a filter.
        AliasedAdapter aliased = new AliasedAdapter();
        aliased.setObjectMapper(JsonMapper.builder().build());
        doReturn(List.of(Roles.PUBLIC)).when(auth).getAuthorities();
        AliasedDoc doc = new AliasedDoc();
        doc.setToken("t");

        JsonNode output = (JsonNode) aliased.filterOutput(DalOperationType.READ, doc, auth);

        assertFalse(output.has("token"));
        assertFalse(aliased.canFilterOn("token", auth));
    }

    @Test
    void filter_belowAMapProperty_acceptsAnyKeyWhenTheMapIsInView() {
        BagAdapter bag = new BagAdapter();
        bag.setObjectMapper(JsonMapper.builder().build());
        doReturn(List.of(Roles.PUBLIC)).when(auth).getAuthorities();

        assertTrue(bag.canFilterOn("attributes.any.key", auth), "map keys are not declared properties");
        assertFalse(bag.canFilterOn("hiddenAttributes.key", auth), "the map itself is out of view");
    }

    @Test
    void filter_perAccessorViews_followTheGetterLikeSerialization() {
        // ssn: writable by Public (setter), visible to Internal only (getter) — the filter must follow the
        // getter, exactly like the response does, or Public could bisect ssn through `ssn ~ '123*'`.
        // note: the reverse split — readable by Public (getter), writable by Internal (setter).
        AccessorAdapter accessor = new AccessorAdapter();
        accessor.setObjectMapper(JsonMapper.builder().build());
        doReturn(List.of(Roles.PUBLIC)).when(auth).getAuthorities();
        AccessorDoc doc = new AccessorDoc();
        doc.setSsn("123-45-6789");
        doc.setNote("n");

        JsonNode output = (JsonNode) accessor.filterOutput(DalOperationType.READ, doc, auth);
        Map<String, Object> input = accessor.filterInput(
            DalOperationType.UPDATE, new HashMap<>(Map.of("ssn", "x", "note", "y")), auth);

        assertFalse(output.has("ssn"), "the response follows the getter view (Internal)");
        assertFalse(accessor.canFilterOn("ssn", auth), "so must the filter");
        assertTrue(output.has("note"));
        assertTrue(accessor.canFilterOn("note", auth));
        assertEquals(Set.of("ssn"), input.keySet(), "input follows the setter view: ssn writable, note not");
    }

    @Test
    void filter_belowAMapOfBeans_checksTheValueBeanAgainstTheView() {
        // Jackson applies the view to map values too: a hidden property of the value bean is pruned from
        // the response, so it must not be reachable through `members.<key>.secret` either.
        BagAdapter bag = new BagAdapter();
        bag.setObjectMapper(JsonMapper.builder().build());
        doReturn(List.of(Roles.PUBLIC)).when(auth).getAuthorities();

        assertTrue(bag.canFilterOn("members.alice.visible", auth));
        assertFalse(bag.canFilterOn("members.alice.secret", auth));
        assertFalse(bag.canFilterOn("members.alice.hidden", auth));
        assertFalse(bag.canFilterOn("members.alice.nope", auth));
        assertTrue(bag.canFilterOn("members.alice", auth), "the key alone addresses the visible value");
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

    private static class AliasedAdapter extends JsonViewDalRbacAdapter<AliasedDoc> {
        @Override
        protected Class<?> resolveView(DalOperationType operation, Authentication authentication) {
            return authentication.getAuthorities().contains(Roles.PUBLIC) ? Views.Public.class : null;
        }
    }

    private static class AccessorAdapter extends JsonViewDalRbacAdapter<AccessorDoc> {
        @Override
        protected Class<?> resolveView(DalOperationType operation, Authentication authentication) {
            return authentication.getAuthorities().contains(Roles.PUBLIC) ? Views.Public.class : null;
        }
    }

    private static class BagAdapter extends JsonViewDalRbacAdapter<Bag> {
        @Override
        protected Class<?> resolveView(DalOperationType operation, Authentication authentication) {
            return authentication.getAuthorities().contains(Roles.PUBLIC) ? Views.Public.class : null;
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
        @JsonView(Views.Public.class)
        private List<String> tags;
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

    @Getter
    @Setter
    @NoArgsConstructor
    static class AliasedDoc {
        @JsonView(Views.Public.class)
        @JsonProperty("full_name")
        private String name;
        @JsonView(Views.Internal.class)
        @JsonProperty("secret_code")
        private String secret;
        @JsonView(Views.Public.class)
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private String token;
    }

    /**
     * Views declared per accessor: {@code ssn} is Public on the setter and Internal on the getter,
     * {@code note} the other way round.
     */
    static class AccessorDoc {
        private String ssn;
        private String note;

        @JsonView(Views.Internal.class)
        public String getSsn() {
            return ssn;
        }

        @JsonView(Views.Public.class)
        public void setSsn(String ssn) {
            this.ssn = ssn;
        }

        @JsonView(Views.Public.class)
        public String getNote() {
            return note;
        }

        @JsonView(Views.Internal.class)
        public void setNote(String note) {
            this.note = note;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class Bag {
        @JsonView(Views.Public.class)
        private Map<String, Object> attributes;
        private Map<String, Object> hiddenAttributes;
        @JsonView(Views.Public.class)
        private Map<String, Member> members;
    }

    static class ExplodingDoc {
        // Deliberately NOT an IllegalStateException: the wrap assertion must be able to tell the
        // adapter's wrapper apart from the raw getter failure leaking through unwrapped.
        @SuppressWarnings("unused")
        @JsonView(Views.Public.class)
        public String getBoom() {
            throw new UnsupportedOperationException("boom");
        }
    }
}
