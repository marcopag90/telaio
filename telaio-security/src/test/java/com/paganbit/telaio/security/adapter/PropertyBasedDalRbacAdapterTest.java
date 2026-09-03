package com.paganbit.telaio.security.adapter;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

import static com.paganbit.telaio.introspection.PropertyNameResolver.propertyName;
import static com.paganbit.telaio.introspection.PropertyNameResolver.propertyPath;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class PropertyBasedDalRbacAdapterTest {

    @Mock
    private Authentication mockAuthentication;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private TestPropertyBasedDalRbacAdapter defaultAdapter;
    private OperationSpecificAdapter customAdapter;
    private Map<String, Object> testInput;
    private User user;

    @BeforeEach
    void setUp() {
        defaultAdapter = new TestPropertyBasedDalRbacAdapter();
        defaultAdapter.setObjectMapper(objectMapper);
        customAdapter = new OperationSpecificAdapter();
        customAdapter.setObjectMapper(objectMapper);

        testInput = new HashMap<>();
        testInput.put("id", 1L);
        testInput.put("name", "Test Name");
        testInput.put("email", "test@example.com");
        testInput.put("role", "USER");

        Map<String, Object> addressMap = new HashMap<>();
        addressMap.put("street", "123 Main St");
        addressMap.put("city", "Anytown");
        addressMap.put("zipCode", "12345");
        testInput.put("address", addressMap);

        Address address = new Address("123 Main St", "Anytown", "12345");
        user = new User(1L, "Test Name", "test@example.com", "USER", address);
    }

    // ------------------------------------------------------------------------
    // Input filtering
    // ------------------------------------------------------------------------

    @Test
    void shouldFilterCreateInputBasedOnDefaultRoles() {
        // USER can write name, email, address
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        var result = defaultAdapter.filterInput(DalOperationType.CREATE, testInput, mockAuthentication);

        assertEquals(3, result.size());
        assertTrue(result.containsKey("name"));
        assertTrue(result.containsKey("email"));
        assertTrue(result.containsKey("address"));
        assertFalse(result.containsKey("id"));
        assertFalse(result.containsKey("role"));
    }

    @Test
    void shouldFilterUpdateInputBasedOnDefaultRoles() {
        // ADMIN can write everything
        doReturn(List.of(UserAuthority.ADMIN)).when(mockAuthentication).getAuthorities();

        var result = defaultAdapter.filterInput(DalOperationType.UPDATE, testInput, mockAuthentication);

        assertEquals(5, result.size());
        assertTrue(result.containsKey("id"));
        assertTrue(result.containsKey("name"));
        assertTrue(result.containsKey("email"));
        assertTrue(result.containsKey("role"));
        assertTrue(result.containsKey("address"));
    }

    @Test
    void shouldUseCustomCreateWritableFields() {
        // In custom adapter, ADMIN create only name & email
        doReturn(List.of(UserAuthority.ADMIN)).when(mockAuthentication).getAuthorities();

        var result = customAdapter.filterInput(DalOperationType.CREATE, testInput, mockAuthentication);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("name"));
        assertTrue(result.containsKey("email"));
    }

    @Test
    void shouldUseCustomUpdateWritableFields() {
        // In custom adapter, ADMIN update only role
        doReturn(List.of(UserAuthority.ADMIN)).when(mockAuthentication).getAuthorities();

        var result = customAdapter.filterInput(DalOperationType.UPDATE, testInput, mockAuthentication);

        assertEquals(1, result.size());
        assertTrue(result.containsKey("role"));
    }

    @Test
    void shouldNotFilterInputForReadOperations() {
        // Input filtering only applies to writes; read ops pass the payload through unchanged.
        var result = defaultAdapter.filterInput(DalOperationType.READ, testInput, mockAuthentication);
        assertSame(testInput, result);
    }

    // ------------------------------------------------------------------------
    // Output filtering
    // ------------------------------------------------------------------------

    @Test
    void shouldFilterCreateOutputForUserRole() {
        // USER can read id, name, email, address.street, address.city
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        JsonNode result = (JsonNode) defaultAdapter.filterOutput(DalOperationType.CREATE, user, mockAuthentication);

        assertUserViewFields(result);
    }

    @Test
    void shouldFilterReadOutputForUserRole() {
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        JsonNode result = (JsonNode) defaultAdapter.filterOutput(DalOperationType.READ, user, mockAuthentication);

        assertUserViewFields(result);
    }

    @Test
    void shouldFilterReadOneOutputForUserRole() {
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        JsonNode result = (JsonNode) defaultAdapter.filterOutput(DalOperationType.READ_ONE, user, mockAuthentication);

        assertEquals(user.getId().longValue(), result.get("id").asLong());
        assertEquals(user.getName(), result.get("name").asString());
        assertEquals(user.getEmail(), result.get("email").asString());
        assertFalse(result.has("role"));
    }

    @Test
    void shouldFilterUpdateOutputForUserRole() {
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        JsonNode result = (JsonNode) defaultAdapter.filterOutput(DalOperationType.UPDATE, user, mockAuthentication);

        assertEquals(user.getId().longValue(), result.get("id").asLong());
        assertEquals(user.getName(), result.get("name").asString());
        assertEquals(user.getEmail(), result.get("email").asString());
        assertFalse(result.has("role"));
    }

    @Test
    void shouldFilterOutputForAdminRole() {
        doReturn(List.of(UserAuthority.ADMIN)).when(mockAuthentication).getAuthorities();

        JsonNode result = (JsonNode) defaultAdapter.filterOutput(DalOperationType.READ, user, mockAuthentication);

        assertEquals(user.getId().longValue(), result.get("id").asLong());
        assertEquals(user.getName(), result.get("name").asString());
        assertEquals(user.getEmail(), result.get("email").asString());
        assertEquals(user.getRole(), result.get("role").asString());
        assertTrue(result.has("address"));
        assertEquals(user.getAddress().getStreet(), result.get("address").get("street").asString());
        assertEquals(user.getAddress().getCity(), result.get("address").get("city").asString());
        assertEquals(user.getAddress().getZipCode(), result.get("address").get("zipCode").asString());
    }

    @Test
    void shouldReturnNullWhenDtoIsNull() {
        assertNull(defaultAdapter.filterOutput(DalOperationType.READ, null, mockAuthentication));
    }

    private void assertUserViewFields(JsonNode result) {
        assertNotNull(result);
        assertEquals(user.getId().longValue(), result.get("id").asLong());
        assertEquals(user.getName(), result.get("name").asString());
        assertEquals(user.getEmail(), result.get("email").asString());
        assertFalse(result.has("role"));
        assertTrue(result.has("address"));
        assertEquals(user.getAddress().getStreet(), result.get("address").get("street").asString());
        assertEquals(user.getAddress().getCity(), result.get("address").get("city").asString());
        assertFalse(result.get("address").has("zipCode"));
    }

    // ------------------------------------------------------------------------
    // New capabilities: immutable records, inheritance, collections, JSON naming
    // ------------------------------------------------------------------------

    @Test
    void shouldFilterImmutableRecordOutput() {
        RecordAdapter adapter = new RecordAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        JsonNode result = (JsonNode) adapter.filterOutput(
            DalOperationType.READ, new PersonRecord(1L, "Alice", "top-secret"), mockAuthentication);

        assertEquals(1L, result.get("id").asLong());
        assertEquals("Alice", result.get("name").asString());
        assertFalse(result.has("secret"));
    }

    @Test
    void shouldFilterInheritedFields() {
        InheritanceAdapter adapter = new InheritanceAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        Dog dog = new Dog();
        dog.setSpecies("Canis familiaris"); // inherited field
        dog.setBreed("Beagle");

        JsonNode result = (JsonNode) adapter.filterOutput(DalOperationType.READ, dog, mockAuthentication);

        assertEquals("Canis familiaris", result.get("species").asString());
        assertFalse(result.has("breed"));
    }

    @Test
    void shouldFilterNestedCollectionOutput() {
        CollectionAdapter adapter = new CollectionAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        Team team = new Team("Platform", List.of(
            new Member("Bob", "100k"),
            new Member("Carol", "120k")
        ));

        JsonNode result = (JsonNode) adapter.filterOutput(DalOperationType.READ, team, mockAuthentication);

        assertEquals("Platform", result.get("name").asString());
        assertTrue(result.has("members"));
        assertEquals(2, result.get("members").size());
        assertEquals("Bob", result.get("members").get(0).get("name").asString());
        assertFalse(result.get("members").get(0).has("salary"));
        assertEquals("Carol", result.get("members").get(1).get("name").asString());
        assertFalse(result.get("members").get(1).has("salary"));
    }

    @Test
    void shouldHonorJsonPropertyRenameOnInput() {
        // Writable map authored with the Java name "name"; the entity renames it to "full_name" via
        // @JsonProperty, so the client sends "full_name". Input translation (deserialization view) must
        // match it and drop the non-writable "secret".
        RenameAdapter adapter = new RenameAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        Map<String, Object> input = new HashMap<>();
        input.put("full_name", "Visible");
        input.put("secret", "hidden");

        var result = adapter.filterInput(DalOperationType.CREATE, input, mockAuthentication);

        assertEquals(1, result.size());
        assertTrue(result.containsKey("full_name"));
        assertFalse(result.containsKey("secret"));
    }

    @Test
    void shouldHonorJsonPropertyRenameOnOutput() {
        RenameAdapter adapter = new RenameAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        JsonNode result = (JsonNode) adapter.filterOutput(
            DalOperationType.READ, new RenamedDto("Visible", "hidden"), mockAuthentication);

        assertEquals("Visible", result.get("full_name").asString());
        assertFalse(result.has("secret"));
    }

    @Test
    void shouldKeepReadOnlyPropertyVisibleInRole() {
        // Regression: a read-only property the role is allowed to read must survive output filtering.
        // Rebinding the pruned tree to an entity would drop it, because Jackson ignores
        // @JsonProperty(access = READ_ONLY) on deserialization.
        ReadOnlyAdapter adapter = new ReadOnlyAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        JsonNode result = (JsonNode) adapter.filterOutput(
            DalOperationType.READ, new ReadOnlyEntity(42L, "Visible", "hidden"), mockAuthentication);

        assertEquals(42L, result.get("id").asLong(), "read-only id allowed for the role must be returned");
        assertEquals("Visible", result.get("name").asString());
        assertFalse(result.has("secret"));
    }

    // ------------------------------------------------------------------------
    // Effective-field resolution & memoization
    // ------------------------------------------------------------------------

    @Test
    void shouldCombineFieldsFromMultipleRoles() {
        doReturn(Arrays.asList(UserAuthority.USER, UserAuthority.MANAGER)).when(mockAuthentication).getAuthorities();

        Set<String> result = defaultAdapter.getReadableFieldsFor(mockAuthentication);

        assertEquals(7, result.size());
        assertTrue(result.contains("id"));
        assertTrue(result.contains("name"));
        assertTrue(result.contains("email"));
        assertTrue(result.contains("address"));
        assertTrue(result.contains("address.street"));
        assertTrue(result.contains("address.city"));
        assertTrue(result.contains("address.zipCode"));
    }

    @Test
    void shouldReturnEmptySetWhenAuthIsNull() {
        Set<String> result = defaultAdapter.resolveEffectiveFields(null, defaultAdapter.readableFieldsByRole());
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptySetWhenFieldMapIsNull() {
        Set<String> result = defaultAdapter.resolveEffectiveFields(mockAuthentication, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldResolveFieldMapsOnlyOnceAcrossMultipleRequests() {
        doReturn(List.of(UserAuthority.ADMIN)).when(mockAuthentication).getAuthorities();
        CountingAdapter adapter = new CountingAdapter();
        adapter.setObjectMapper(objectMapper);

        for (int i = 0; i < 5; i++) {
            adapter.getReadableFieldsFor(mockAuthentication);
            adapter.filterInput(DalOperationType.CREATE, testInput, mockAuthentication);
            adapter.filterInput(DalOperationType.UPDATE, testInput, mockAuthentication);
        }

        assertEquals(1, adapter.readableCalls, "readableFieldsByRole should be resolved once");
        assertEquals(1, adapter.createWritableCalls, "createWritableFieldsByRole should be resolved once");
        assertEquals(1, adapter.updateWritableCalls, "updateWritableFieldsByRole should be resolved once");
    }

    // ------------------------------------------------------------------------
    // Input edge cases
    // ------------------------------------------------------------------------

    @Test
    void shouldReturnEmptyMapForEmptyCreateInput() {
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        var result = defaultAdapter.filterInput(DalOperationType.CREATE, Map.of(), mockAuthentication);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldNotFilterInputForReadOneAndDeleteOperations() {
        // Input filtering applies to writes only; READ_ONE and DELETE pass the payload through untouched.
        assertSame(testInput, defaultAdapter.filterInput(DalOperationType.READ_ONE, testInput, mockAuthentication));
        assertSame(testInput, defaultAdapter.filterInput(DalOperationType.DELETE, testInput, mockAuthentication));
    }

    @Test
    void shouldFilterNestedCollectionInput() {
        // 'members' is allowed only via its descendant 'members.name' (no bare 'members'), so each element
        // map is recursed into and its non-writable 'salary' is dropped.
        CollectionWriteAdapter adapter = new CollectionWriteAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        Map<String, Object> input = new HashMap<>();
        input.put("name", "Platform");
        input.put("members", List.of(
            new HashMap<>(Map.of("name", "Bob", "salary", "100k")),
            new HashMap<>(Map.of("name", "Carol", "salary", "120k"))
        ));

        var result = adapter.filterInput(DalOperationType.CREATE, input, mockAuthentication);

        assertEquals("Platform", result.get("name"));
        List<?> members = (List<?>) result.get("members");
        assertEquals(2, members.size());
        Map<?, ?> first = (Map<?, ?>) members.getFirst();
        assertTrue(first.containsKey("name"));
        assertFalse(first.containsKey("salary"));
        Map<?, ?> second = (Map<?, ?>) members.get(1);
        assertTrue(second.containsKey("name"));
        assertFalse(second.containsKey("salary"));
    }

    // ------------------------------------------------------------------------
    // Output edge cases & per-operation maps
    // ------------------------------------------------------------------------

    @Test
    void shouldReturnEmptyProjectionWhenRoleHasNoReadableFields() {
        // MANAGER has no entry in the record adapter's readable map → every property is pruned.
        RecordAdapter adapter = new RecordAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.MANAGER)).when(mockAuthentication).getAuthorities();

        JsonNode result = (JsonNode) adapter.filterOutput(
            DalOperationType.READ, new PersonRecord(1L, "Alice", "secret"), mockAuthentication);

        assertTrue(result.isEmpty(), "no readable fields for the role → all properties pruned");
    }

    @Test
    void shouldFilterOutputWithDefaultObjectMapperWhenNoneInjected() {
        // No setObjectMapper(): the adapter must still work standalone using its default JsonMapper.
        RecordAdapter adapter = new RecordAdapter();
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        JsonNode result = (JsonNode) adapter.filterOutput(
            DalOperationType.READ, new PersonRecord(7L, "Zoe", "secret"), mockAuthentication);

        assertEquals(7L, result.get("id").asLong());
        assertEquals("Zoe", result.get("name").asString());
        assertFalse(result.has("secret"));
    }

    @Test
    void shouldApplyPerOperationReadableMaps() {
        PerOperationReadableAdapter adapter = new PerOperationReadableAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        JsonNode create = (JsonNode) adapter.filterOutput(DalOperationType.CREATE, user, mockAuthentication);
        assertTrue(create.has("id"));
        assertTrue(create.has("name"));
        assertFalse(create.has("email"));

        JsonNode update = (JsonNode) adapter.filterOutput(DalOperationType.UPDATE, user, mockAuthentication);
        assertTrue(update.has("id"));
        assertTrue(update.has("email"));
        assertFalse(update.has("name"));

        // READ and READ_ONE share the base readable map → only id.
        JsonNode read = (JsonNode) adapter.filterOutput(DalOperationType.READ, user, mockAuthentication);
        assertTrue(read.has("id"));
        assertFalse(read.has("name"));
        assertFalse(read.has("email"));

        JsonNode readOne = (JsonNode) adapter.filterOutput(DalOperationType.READ_ONE, user, mockAuthentication);
        assertTrue(readOne.has("id"));
        assertFalse(readOne.has("name"));
        assertFalse(readOne.has("email"));
    }

    // ------------------------------------------------------------------------
    // Descendant-only grants (parent field reachable only through nested paths)
    // ------------------------------------------------------------------------

    @Test
    void input_descendantOnlyGrant_filtersNestedObjectRecursively() {
        // 'address' itself is not writable, only 'address.street' — the nested map must be
        // recursed into and reduced to the granted children.
        NestedWriteAdapter adapter = new NestedWriteAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        var result = adapter.filterInput(DalOperationType.CREATE, testInput, mockAuthentication);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("name"));
        Map<?, ?> address = (Map<?, ?>) result.get("address");
        assertEquals(Set.of("street"), address.keySet());
    }

    @Test
    void input_exactParentGrant_passesWholeSubtreeUnfiltered() {
        // Contract, not accident: an EXACT grant on a parent path ("address") admits the whole
        // subtree, including children that carry no grant of their own ("zipCode"). This is the
        // permissive input mirror of the strict output pruning proven in
        // output_parentGrantWithoutChildren_removesEmptiedNestedObject — grant a bare parent on the
        // writable side only when every nested field is meant to be writable.
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        var result = defaultAdapter.filterInput(DalOperationType.CREATE, testInput, mockAuthentication);

        Map<?, ?> address = (Map<?, ?>) result.get("address");
        assertEquals(Set.of("street", "city", "zipCode"), address.keySet());
    }

    @Test
    void input_descendantOnlyGrant_dropsNestedObjectWithNoGrantedChildren() {
        NestedWriteAdapter adapter = new NestedWriteAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        Map<String, Object> input = new HashMap<>();
        input.put("name", "N");
        input.put("address", Map.of("zipCode", "12345"));

        var result = adapter.filterInput(DalOperationType.CREATE, input, mockAuthentication);

        assertFalse(result.containsKey("address"), "nested map emptied by filtering must be dropped");
    }

    @Test
    void input_descendantOnlyGrant_ignoresScalarValueAtParentPath() {
        NestedWriteAdapter adapter = new NestedWriteAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        Map<String, Object> input = new HashMap<>();
        input.put("address", "not a nested object");

        var result = adapter.filterInput(DalOperationType.CREATE, input, mockAuthentication);

        assertTrue(result.isEmpty(), "a scalar at a descendant-only path cannot be granted");
    }

    @Test
    void input_listElements_scalarSkippedAndAllDeniedElementDropped() {
        // In a filtered list only element maps with surviving fields are kept: scalar elements and
        // elements whose fields are all denied disappear — here that empties the list entirely.
        CollectionWriteAdapter adapter = new CollectionWriteAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        Map<String, Object> input = new HashMap<>();
        input.put("name", "T");
        input.put("members", List.of("just a string", Map.of("salary", "100k")));

        var result = adapter.filterInput(DalOperationType.CREATE, input, mockAuthentication);

        assertTrue(result.containsKey("name"));
        assertFalse(result.containsKey("members"), "list with no surviving elements must be dropped");
    }

    @Test
    void output_descendantOnlyGrant_prunesNestedObjectToGrantedChildren() {
        DescendantReadableAdapter adapter = new DescendantReadableAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        JsonNode result = (JsonNode) adapter.filterOutput(DalOperationType.READ, user, mockAuthentication);

        assertTrue(result.has("id"));
        assertFalse(result.has("name"));
        JsonNode address = result.get("address");
        assertNotNull(address);
        assertTrue(address.has("street"));
        assertFalse(address.has("city"));
        assertFalse(address.has("zipCode"));
    }

    @Test
    void output_parentGrantWithoutChildren_removesEmptiedNestedObject() {
        // 'address' is readable but none of its children are: pruning empties the object, and an
        // empty object must not leak into the response.
        ParentOnlyReadableAdapter adapter = new ParentOnlyReadableAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        JsonNode result = (JsonNode) adapter.filterOutput(DalOperationType.READ, user, mockAuthentication);

        assertTrue(result.has("id"));
        assertFalse(result.has("address"), "nested object emptied by pruning must be removed");
    }

    @Test
    void output_descendantOnlyGrant_prunesArrayElements() {
        CollectionDescendantReadableAdapter adapter = new CollectionDescendantReadableAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();
        Team team = new Team("T", List.of(new Member("alice", "100k"), new Member("bob", "90k")));

        JsonNode result = (JsonNode) adapter.filterOutput(DalOperationType.READ, team, mockAuthentication);

        JsonNode members = result.get("members");
        assertNotNull(members);
        assertEquals(2, members.size());
        assertTrue(members.get(0).has("name"));
        assertFalse(members.get(0).has("salary"));
    }

    @Test
    void output_emptyArrayAtGrantedPath_isRemoved() {
        CollectionDescendantReadableAdapter adapter = new CollectionDescendantReadableAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();
        Team team = new Team("T", List.of());

        JsonNode result = (JsonNode) adapter.filterOutput(DalOperationType.READ, team, mockAuthentication);

        assertTrue(result.has("name"));
        assertFalse(result.has("members"), "an empty array must not leak into the response");
    }

    @Test
    void output_ungrantedArray_isRemovedEntirely() {
        TeamNameOnlyReadableAdapter adapter = new TeamNameOnlyReadableAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();
        Team team = new Team("T", List.of(new Member("alice", "100k")));

        JsonNode result = (JsonNode) adapter.filterOutput(DalOperationType.READ, team, mockAuthentication);

        assertTrue(result.has("name"));
        assertFalse(result.has("members"));
    }

    @Test
    void output_arrayOfScalars_isKeptUnchanged() {
        TaggedAdapter adapter = new TaggedAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();
        TaggedDoc doc = new TaggedDoc("T", List.of("a", "b"));

        JsonNode result = (JsonNode) adapter.filterOutput(DalOperationType.READ, doc, mockAuthentication);

        JsonNode tags = result.get("tags");
        assertNotNull(tags);
        assertEquals(2, tags.size());
        assertEquals("a", tags.get(0).asString());
    }

    @Test
    void output_nonObjectTree_isReturnedUntouched() {
        // An entity serializing to a non-object tree (here: an array) has no fields to prune.
        // Limitation pinned here: pruning applies to OBJECT trees only, so a type serializing to an
        // array of objects would bypass field filtering entirely — expose such types through this
        // adapter only if every element field may be read by every principal.
        ArrayEntityAdapter adapter = new ArrayEntityAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();
        StringListEntity entity = new StringListEntity();
        entity.add("a");
        entity.add("b");

        JsonNode result = (JsonNode) adapter.filterOutput(DalOperationType.READ, entity, mockAuthentication);

        assertTrue(result.isArray());
        assertEquals(2, result.size());
    }

    @Test
    void output_serializationFailure_isWrappedInIllegalStateException() {
        ExplodingPropertyAdapter adapter = new ExplodingPropertyAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();
        ExplodingEntity entity = new ExplodingEntity();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> adapter.filterOutput(DalOperationType.READ, entity, mockAuthentication));
        assertTrue(exception.getMessage().startsWith("Failed to filter output for type"));
    }

    // ------------------------------------------------------------------------
    // Filter-field check (reads)
    // ------------------------------------------------------------------------

    @Test
    void filter_readableFields_areAllowed() {
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        assertTrue(defaultAdapter.canFilterOn("name", mockAuthentication));
        assertTrue(defaultAdapter.canFilterOn("address.city", mockAuthentication));
    }

    @Test
    void filter_hiddenFields_areDenied() {
        // USER cannot read 'role' (ADMIN-only) nor 'address.zipCode' (MANAGER/ADMIN): neither may be used
        // as a filter criterion, or its value could be bisected from the narrowed page.
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        assertFalse(defaultAdapter.canFilterOn("role", mockAuthentication));
        assertFalse(defaultAdapter.canFilterOn("address.zipCode", mockAuthentication));
    }

    @Test
    void filter_usesTheUnionOfTheRoles() {
        doReturn(List.of(UserAuthority.USER, UserAuthority.ADMIN)).when(mockAuthentication).getAuthorities();

        assertTrue(defaultAdapter.canFilterOn("role", mockAuthentication));
    }

    @Test
    void filter_renamedField_isCheckedUnderBothSpellings() {
        // The grant is authored with the Java name; the filter may use the wire name or the Java name —
        // both resolve to the same property, and the hidden one is denied under both spellings too.
        RenameAdapter adapter = new RenameAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        assertTrue(adapter.canFilterOn("full_name", mockAuthentication));
        assertTrue(adapter.canFilterOn("name", mockAuthentication));
        assertFalse(adapter.canFilterOn("secret", mockAuthentication));
    }

    @Test
    void filter_parentWithGrantedDescendant_isAllowedButUngrantedSiblingIsNot() {
        // Mirrors output pruning: 'address' appears in the response (pruned to 'street'), so it may be
        // referenced (e.g. `address is null`); 'address.city' is pruned away, so it may not.
        DescendantReadableAdapter adapter = new DescendantReadableAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        assertTrue(adapter.canFilterOn("address", mockAuthentication));
        assertTrue(adapter.canFilterOn("address.street", mockAuthentication));
        assertFalse(adapter.canFilterOn("address.city", mockAuthentication));
    }

    @Test
    void filter_childOfBareParentGrant_isDeniedLikeOnOutput() {
        // A bare 'address' grant prunes every child from the response
        // (output_parentGrantWithoutChildren_removesEmptiedNestedObject) — so the children are not
        // filterable either; the parent itself still is.
        ParentOnlyReadableAdapter adapter = new ParentOnlyReadableAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        assertTrue(adapter.canFilterOn("address", mockAuthentication));
        assertFalse(adapter.canFilterOn("address.zipCode", mockAuthentication));
    }

    @Test
    void filter_grantOnAPropertyTheResponseNeverShows_isDenied() {
        // 'password' is write-only: listed as readable by mistake, it is still never serialized, so the
        // filter must not open a channel the response does not have.
        CredentialsAdapter adapter = new CredentialsAdapter();
        adapter.setObjectMapper(objectMapper);
        doReturn(List.of(UserAuthority.USER)).when(mockAuthentication).getAuthorities();

        JsonNode output = (JsonNode) adapter.filterOutput(
            DalOperationType.READ, new Credentials("bob", "s3cret"), mockAuthentication);

        assertFalse(output.has("password"));
        assertFalse(adapter.canFilterOn("password", mockAuthentication));
        assertTrue(adapter.canFilterOn("name", mockAuthentication));
    }

    @Test
    void filter_unknownField_isDenied() {
        // Denied before the roles are even consulted: an unresolvable path is never readable.
        assertFalse(defaultAdapter.canFilterOn("nope", mockAuthentication));
        assertFalse(defaultAdapter.canFilterOn("address.nope", mockAuthentication));
        assertFalse(defaultAdapter.canFilterOn("name.length", mockAuthentication), "segment on a scalar");
    }

    @Test
    void filter_withoutAuthentication_isDenied() {
        assertFalse(defaultAdapter.canFilterOn("name", null));
    }

    // ------------------------------------------------------------------------
    // Exposed-type resolution
    // ------------------------------------------------------------------------

    @Test
    void shouldThrowWhenExposedTypeCannotBeResolved() {
        // A raw subclass carries no resolvable type argument; the type is resolved in the constructor.
        assertThrows(IllegalStateException.class, RawAdapter::new);
    }

    // ------------------------------------------------------------------------
    // Concrete adapters & support classes
    // ------------------------------------------------------------------------

    private static class CountingAdapter extends TestPropertyBasedDalRbacAdapter {

        int readableCalls;
        int createWritableCalls;
        int updateWritableCalls;

        @Override
        protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
            readableCalls++;
            return super.readableFieldsByRole();
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> createWritableFieldsByRole() {
            createWritableCalls++;
            return super.createWritableFieldsByRole();
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> updateWritableFieldsByRole() {
            updateWritableCalls++;
            return super.updateWritableFieldsByRole();
        }
    }

    private static class OperationSpecificAdapter extends TestPropertyBasedDalRbacAdapter {

        @Override
        protected Map<GrantedAuthority, Set<String>> createWritableFieldsByRole() {
            return Map.of(UserAuthority.ADMIN, Set.of(
                propertyName(User::getName),
                propertyName(User::getEmail)
            ));
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> updateWritableFieldsByRole() {
            return Map.of(UserAuthority.ADMIN, Set.of(
                propertyName(User::getRole)
            ));
        }
    }

    private static class UserAuthority {
        static final GrantedAuthority USER = new SimpleGrantedAuthority("user");
        static final GrantedAuthority MANAGER = new SimpleGrantedAuthority("manager");
        static final GrantedAuthority ADMIN = new SimpleGrantedAuthority("admin");
    }

    private static class TestPropertyBasedDalRbacAdapter extends PropertyBasedDalRbacAdapter<User> {

        @Override
        protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
            return Map.of(
                UserAuthority.USER, Set.of(
                    propertyName(User::getId),
                    propertyName(User::getName),
                    propertyName(User::getEmail),
                    propertyName(User::getAddress),
                    propertyPath(propertyName(User::getAddress), propertyName(Address::getStreet)),
                    propertyPath(propertyName(User::getAddress), propertyName(Address::getCity))
                ),
                UserAuthority.MANAGER, Set.of(
                    propertyName(User::getId),
                    propertyName(User::getName),
                    propertyName(User::getEmail),
                    propertyName(User::getAddress),
                    propertyPath(propertyName(User::getAddress), propertyName(Address::getStreet)),
                    propertyPath(propertyName(User::getAddress), propertyName(Address::getCity)),
                    propertyPath(propertyName(User::getAddress), propertyName(Address::getZipCode))
                ),
                UserAuthority.ADMIN, Set.of(
                    propertyName(User::getId),
                    propertyName(User::getName),
                    propertyName(User::getEmail),
                    propertyName(User::getRole),
                    propertyName(User::getAddress),
                    propertyPath(propertyName(User::getAddress), propertyName(Address::getStreet)),
                    propertyPath(propertyName(User::getAddress), propertyName(Address::getCity)),
                    propertyPath(propertyName(User::getAddress), propertyName(Address::getZipCode))
                )
            );
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
            return Map.of(
                UserAuthority.USER, Set.of(
                    propertyName(User::getName),
                    propertyName(User::getEmail),
                    propertyName(User::getAddress),
                    propertyPath(propertyName(User::getAddress), propertyName(Address::getStreet)),
                    propertyPath(propertyName(User::getAddress), propertyName(Address::getCity))
                ),
                UserAuthority.MANAGER, Set.of(
                    propertyName(User::getName),
                    propertyName(User::getEmail),
                    propertyName(User::getAddress),
                    propertyPath(propertyName(User::getAddress), propertyName(Address::getStreet)),
                    propertyPath(propertyName(User::getAddress), propertyName(Address::getCity)),
                    propertyPath(propertyName(User::getAddress), propertyName(Address::getZipCode))
                ),
                UserAuthority.ADMIN, Set.of(
                    propertyName(User::getId),
                    propertyName(User::getName),
                    propertyName(User::getEmail),
                    propertyName(User::getRole),
                    propertyName(User::getAddress),
                    propertyPath(propertyName(User::getAddress), propertyName(Address::getStreet)),
                    propertyPath(propertyName(User::getAddress), propertyName(Address::getCity)),
                    propertyPath(propertyName(User::getAddress), propertyName(Address::getZipCode))
                )
            );
        }
    }

    private static class RecordAdapter extends PropertyBasedDalRbacAdapter<PersonRecord> {
        @Override
        protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
            return Map.of(UserAuthority.USER, Set.of("id", "name"));
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
            return Map.of();
        }
    }

    private static class InheritanceAdapter extends PropertyBasedDalRbacAdapter<Dog> {
        @Override
        protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
            return Map.of(UserAuthority.USER, Set.of("species"));
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
            return Map.of();
        }
    }

    private static class CollectionAdapter extends PropertyBasedDalRbacAdapter<Team> {
        @Override
        protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
            return Map.of(UserAuthority.USER, Set.of("name", "members", "members.name"));
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
            return Map.of();
        }
    }

    private static class RenameAdapter extends PropertyBasedDalRbacAdapter<RenamedDto> {
        @Override
        protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
            return Map.of(UserAuthority.USER, Set.of("name"));
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
            return Map.of(UserAuthority.USER, Set.of("name"));
        }
    }

    private static class CredentialsAdapter extends PropertyBasedDalRbacAdapter<Credentials> {
        @Override
        protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
            return Map.of(UserAuthority.USER, Set.of("name", "password"));
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
            return Map.of(UserAuthority.USER, Set.of("name", "password"));
        }
    }

    private static class ReadOnlyAdapter extends PropertyBasedDalRbacAdapter<ReadOnlyEntity> {
        @Override
        protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
            return Map.of(UserAuthority.USER, Set.of("id", "name"));
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
            return Map.of(UserAuthority.USER, Set.of("name"));
        }
    }

    private static class CollectionWriteAdapter extends PropertyBasedDalRbacAdapter<Team> {
        @Override
        protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
            return Map.of();
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
            // 'members' itself is not writable, only its nested 'name' — forcing element-wise recursion.
            return Map.of(UserAuthority.USER, Set.of("name", "members.name"));
        }
    }

    private static class PerOperationReadableAdapter extends PropertyBasedDalRbacAdapter<User> {
        @Override
        protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
            return Map.of(UserAuthority.USER, Set.of(propertyName(User::getId)));
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
            return Map.of();
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> createReadableFieldsByRole() {
            return Map.of(UserAuthority.USER, Set.of(propertyName(User::getId), propertyName(User::getName)));
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> updateReadableFieldsByRole() {
            return Map.of(UserAuthority.USER, Set.of(propertyName(User::getId), propertyName(User::getEmail)));
        }
    }

    private static class NestedWriteAdapter extends PropertyBasedDalRbacAdapter<User> {
        @Override
        protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
            return Map.of();
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
            // 'address' itself is not writable, only its nested 'street' — forcing map recursion.
            return Map.of(UserAuthority.USER, Set.of("name", "address.street"));
        }
    }

    private static class DescendantReadableAdapter extends PropertyBasedDalRbacAdapter<User> {
        @Override
        protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
            return Map.of(UserAuthority.USER, Set.of("id", "address.street"));
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
            return Map.of();
        }
    }

    private static class ParentOnlyReadableAdapter extends PropertyBasedDalRbacAdapter<User> {
        @Override
        protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
            return Map.of(UserAuthority.USER, Set.of("id", "address"));
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
            return Map.of();
        }
    }

    private static class CollectionDescendantReadableAdapter extends PropertyBasedDalRbacAdapter<Team> {
        @Override
        protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
            return Map.of(UserAuthority.USER, Set.of("name", "members.name"));
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
            return Map.of();
        }
    }

    private static class TeamNameOnlyReadableAdapter extends PropertyBasedDalRbacAdapter<Team> {
        @Override
        protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
            return Map.of(UserAuthority.USER, Set.of("name"));
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
            return Map.of();
        }
    }

    private static class TaggedAdapter extends PropertyBasedDalRbacAdapter<TaggedDoc> {
        @Override
        protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
            return Map.of(UserAuthority.USER, Set.of("name", "tags"));
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
            return Map.of();
        }
    }

    private static class ArrayEntityAdapter extends PropertyBasedDalRbacAdapter<StringListEntity> {
        @Override
        protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
            return Map.of();
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
            return Map.of();
        }
    }

    private static class ExplodingPropertyAdapter extends PropertyBasedDalRbacAdapter<ExplodingEntity> {
        @Override
        protected Map<GrantedAuthority, Set<String>> readableFieldsByRole() {
            return Map.of();
        }

        @Override
        protected Map<GrantedAuthority, Set<String>> writableFieldsByRole() {
            return Map.of();
        }
    }

    @SuppressWarnings({"rawtypes"})
    private static class RawAdapter extends PropertyBasedDalRbacAdapter {
        @Override
        protected Map readableFieldsByRole() {
            return Map.of();
        }

        @Override
        protected Map writableFieldsByRole() {
            return Map.of();
        }
    }

    // ------------------------------------------------------------------------
    // DTOs
    // ------------------------------------------------------------------------

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class Address {
        private String street;
        private String city;
        private String zipCode;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class User {
        private Long id;
        private String name;
        private String email;
        private String role;
        private Address address;
    }

    record PersonRecord(Long id, String name, String secret) {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class Animal {
        private String species;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    static class Dog extends Animal {
        private String breed;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class Member {
        private String name;
        private String salary;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class Team {
        private String name;
        private List<Member> members;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class RenamedDto {
        @JsonProperty("full_name")
        private String name;
        private String secret;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class Credentials {
        private String name;
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private String password;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class ReadOnlyEntity {
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private Long id;
        private String name;
        private String secret;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class TaggedDoc {
        private String name;
        private List<String> tags;
    }

    /**
     * Serializes to a JSON array, not an object — no fields to prune.
     */
    static class StringListEntity extends ArrayList<String> {
    }

    static class ExplodingEntity {
        // Deliberately NOT an IllegalStateException: the wrap assertion must be able to tell the
        // adapter's wrapper apart from the raw getter failure leaking through unwrapped.
        @SuppressWarnings("unused")
        public String getBoom() {
            throw new UnsupportedOperationException("boom");
        }
    }
}
