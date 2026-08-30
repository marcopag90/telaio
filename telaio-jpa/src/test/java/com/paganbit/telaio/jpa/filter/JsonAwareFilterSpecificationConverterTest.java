package com.paganbit.telaio.jpa.filter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paganbit.telaio.core.exception.DalInvalidFilterException;
import com.turkraft.springfilter.converter.FilterSpecification;
import com.turkraft.springfilter.converter.FilterSpecificationConverter;
import com.turkraft.springfilter.parser.node.FieldNode;
import com.turkraft.springfilter.parser.node.FilterNode;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JsonAwareFilterSpecificationConverter}'s decorator wiring: that every
 * {@link FilterSpecificationConverter} method either delegates verbatim or wraps the delegate's result,
 * and that the lazy {@code toPredicate} rewrite translates a JSON wire name to its Java property name
 * before delegating. The end-to-end resolution against a real JPA metamodel lives in
 * {@link JsonAwareFilterSpecificationConverterIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class JsonAwareFilterSpecificationConverterTest {

    private static final String QUERY = "cost_price > 100";

    @Mock
    private FilterSpecificationConverter delegate;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private JsonAwareFilterSpecificationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new JsonAwareFilterSpecificationConverter(delegate, objectMapper);
    }

    @Test
    void convertStringDelegatesParsingThenWrapsTheResultingNode() {
        FilterNode node = new FieldNode("cost_price");
        FilterSpecification<Object> delegateSpec = mock();
        when(delegateSpec.getFilter()).thenReturn(node);
        when(delegate.<Object>convert(QUERY)).thenReturn(delegateSpec);

        FilterSpecification<Object> result = converter.convert(QUERY);

        // The wrapper exposes the delegate's parsed node; conversion (the metamodel-bound work) is deferred
        // to toPredicate, so the delegate is asked only to parse here.
        assertThat(result.getFilter()).isSameAs(node);
        verify(delegate).convert(QUERY);
    }

    @Test
    void convertFilterNodeWrapsWithoutDelegatingUntilToPredicate() {
        FilterNode node = new FieldNode("cost_price");

        FilterSpecification<Object> result = converter.convert(node);

        assertThat(result.getFilter()).isSameAs(node);
        // No conversion happens up front for a pre-parsed node — the delegate is untouched until toPredicate.
        verifyNoInteractions(delegate);
    }

    @Test
    void convertSpecificationDelegatesToStringSerialization() {
        FilterSpecification<Object> spec = mock();
        when(delegate.convert(spec)).thenReturn(QUERY);

        assertThat(converter.convert(spec)).isEqualTo(QUERY);
    }

    @Test
    void getConvertibleTypesDelegates() {
        Set<ConvertiblePair> types = Set.of(new ConvertiblePair(String.class, FilterSpecification.class));
        when(delegate.getConvertibleTypes()).thenReturn(types);

        assertThat(converter.getConvertibleTypes()).isSameAs(types);
    }

    @Test
    void genericConvertDispatchesStringSourceToQueryConversion() {
        FilterNode node = new FieldNode("cost_price");
        FilterSpecification<Object> delegateSpec = mock();
        when(delegateSpec.getFilter()).thenReturn(node);
        when(delegate.<Object>convert(QUERY)).thenReturn(delegateSpec);

        Object result = converter.convert(QUERY, descriptor(String.class), descriptor(FilterSpecification.class));

        assertThat(result).isInstanceOfSatisfying(FilterSpecification.class,
            spec -> assertThat(spec.getFilter()).isSameAs(node));
    }

    @Test
    void genericConvertDispatchesFilterNodeSourceToNodeConversion() {
        FilterNode node = new FieldNode("cost_price");

        Object result = converter.convert(node, descriptor(FilterNode.class), descriptor(FilterSpecification.class));

        assertThat(result).isInstanceOfSatisfying(FilterSpecification.class,
            spec -> assertThat(spec.getFilter()).isSameAs(node));
    }

    @Test
    void genericConvertDispatchesSpecificationSourceToStringSerialization() {
        FilterSpecification<Object> spec = mock();
        when(delegate.convert(spec)).thenReturn(QUERY);

        Object result = converter.convert(spec, descriptor(FilterSpecification.class), descriptor(String.class));

        assertThat(result).isEqualTo(QUERY);
    }

    @Test
    void genericConvertFallsBackToDelegateForUnrelatedSource() {
        TypeDescriptor sourceType = descriptor(Integer.class);
        TypeDescriptor targetType = descriptor(String.class);
        Integer source = 42;
        when(delegate.convert(source, sourceType, targetType)).thenReturn("fallback");

        Object result = converter.convert(source, sourceType, targetType);

        assertThat(result).isEqualTo("fallback");
    }

    /**
     * A metamodel type mapping exactly one persistent attribute, {@code costPrice} (basic). The stubs are
     * lenient because a filter rejected by the name rewrite never reaches the metamodel.
     */
    private static EntityType<Widget> widgetModel() {
        EntityType<Widget> model = mock();
        SingularAttribute<?, ?> costPrice = mock();
        Type<?> basic = mock();
        lenient().doThrow(new IllegalArgumentException("Unable to locate Attribute"))
            .when(model).getAttribute(argThat(name -> !"costPrice".equals(name)));
        lenient().doReturn(costPrice).when(model).getAttribute("costPrice");
        lenient().doReturn(basic).when(costPrice).getType();
        return model;
    }

    private static Root<Widget> widgetRoot(EntityType<Widget> model) {
        Root<Widget> root = mock();
        doReturn(Widget.class).when(root).getJavaType();
        lenient().doReturn(model).when(root).getModel();
        return root;
    }

    @Test
    void toPredicateRewritesJsonFieldNameToJavaNameThenDelegates() {
        FieldNode jsonNamedNode = new FieldNode("cost_price");

        FilterSpecification<Widget> rewrittenSpec = mock();
        Predicate predicate = mock(Predicate.class);
        Root<Widget> root = widgetRoot(widgetModel());
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);

        when(delegate.<Widget>convert(any(FilterNode.class))).thenReturn(rewrittenSpec);
        when(rewrittenSpec.toPredicate(root, query, criteriaBuilder)).thenReturn(predicate);

        Predicate result = converter.<Widget>convert(jsonNamedNode).toPredicate(root, query, criteriaBuilder);

        assertThat(result).isSameAs(predicate);

        // The delegate must receive the node with the JSON name "cost_price" rewritten to "costPrice".
        ArgumentCaptor<FilterNode> captor = ArgumentCaptor.forClass(FilterNode.class);
        verify(delegate).convert(captor.capture());
        assertThat(captor.getValue()).isInstanceOfSatisfying(FieldNode.class,
            field -> assertThat(field.getName()).isEqualTo("costPrice"));
    }

    @Test
    void toPredicateRejectsUnknownFieldName() {
        FieldNode unknownNode = new FieldNode("not_a_field");

        Root<Widget> root = widgetRoot(widgetModel());
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);

        FilterSpecification<Widget> spec = converter.convert(unknownNode);

        // A field the entity does not expose is a client fault, rejected before the delegate is asked.
        assertThatThrownBy(() -> spec.toPredicate(root, query, criteriaBuilder))
            .isInstanceOf(DalInvalidFilterException.class)
            .hasMessageContaining("not_a_field");
        verify(delegate, never()).convert(any(FilterNode.class));
    }

    @Test
    void toPredicateRejectsPropertyTheMetamodelDoesNotMap() {
        // Jackson exposes "name", so the name rewrite passes; the metamodel has no such attribute
        // (@Transient-like), so the filter is a client fault — not a Hibernate failure at query time.
        FieldNode transientNode = new FieldNode("name");

        Root<Widget> root = widgetRoot(widgetModel());
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);

        FilterSpecification<Widget> spec = converter.convert(transientNode);

        assertThatThrownBy(() -> spec.toPredicate(root, query, criteriaBuilder))
            .isInstanceOf(DalInvalidFilterException.class)
            .hasMessageContaining("name");
        verify(delegate, never()).convert(any(FilterNode.class));
    }

    @Test
    void toPredicateWrapsOnlyUnsupportedFunctionsAsInvalidFilter() {
        FilterSpecification<Widget> rewrittenSpec = mock();
        Root<Widget> root = widgetRoot(widgetModel());
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        when(delegate.<Widget>convert(any(FilterNode.class))).thenReturn(rewrittenSpec);

        FilterSpecification<Widget> spec = converter.convert(new FieldNode("cost_price"));

        // A function the backend has no processor for is a client fault...
        UnsupportedOperationException unsupported = new UnsupportedOperationException("No transformer");
        doThrow(unsupported).when(rewrittenSpec).toPredicate(root, query, criteriaBuilder);
        assertThatThrownBy(() -> spec.toPredicate(root, query, criteriaBuilder))
            .isInstanceOf(DalInvalidFilterException.class)
            .hasCause(unsupported);

        // ...whereas a literal the ConversionService cannot convert, or a criteria-building argument
        // failure, propagate unchanged (server faults, consistently with the other backends).
        ConversionFailedException conversion = new ConversionFailedException(
            TypeDescriptor.valueOf(String.class), TypeDescriptor.valueOf(BigDecimal.class), "abc",
            new NumberFormatException("abc"));
        doThrow(conversion).when(rewrittenSpec).toPredicate(root, query, criteriaBuilder);
        assertThatThrownBy(() -> spec.toPredicate(root, query, criteriaBuilder)).isSameAs(conversion);

        IllegalArgumentException argument = new IllegalArgumentException("Unable to locate Attribute");
        doThrow(argument).when(rewrittenSpec).toPredicate(root, query, criteriaBuilder);
        assertThatThrownBy(() -> spec.toPredicate(root, query, criteriaBuilder)).isSameAs(argument);
    }

    @Test
    void unsupportedFilterNodeOverloadIsNeverInvokedForStringQuery() {
        FilterSpecification<Object> delegateSpec = mock();
        when(delegateSpec.getFilter()).thenReturn(new FieldNode("cost_price"));
        when(delegate.<Object>convert(QUERY)).thenReturn(delegateSpec);

        converter.convert(QUERY);

        // Parsing a string must not eagerly route through the node overload.
        verify(delegate, never()).convert(any(FilterNode.class));
    }

    private static TypeDescriptor descriptor(Class<?> type) {
        return TypeDescriptor.valueOf(type);
    }

    @Getter
    @Setter
    static class Widget {

        private Long id;

        @JsonProperty("cost_price")
        private BigDecimal costPrice;

        /**
         * Exposed on the wire but not mapped by the (stubbed) metamodel — the {@code @Transient} case.
         */
        private String name;
    }
}
