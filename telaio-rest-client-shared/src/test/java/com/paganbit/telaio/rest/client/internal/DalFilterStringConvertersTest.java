package com.paganbit.telaio.rest.client.internal;

import com.turkraft.springfilter.converter.FilterStringConverter;
import com.turkraft.springfilter.language.*;
import com.turkraft.springfilter.parser.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DalFilterStringConvertersTest {

    private final FilterStringConverter converter = DalFilterStringConverters.pinned();

    @Test
    void rendersComparisonWithNumericInput() {
        FilterNode filter = new FieldNode("price")
            .infix(new GreaterThanOperator(), new InputNode(5));

        // Every input is rendered quoted; the server converts it to the field's type on parse.
        assertThat(converter.convert(filter)).isEqualTo("price > '5'");
    }

    @Test
    void rendersStringInputQuoted() {
        FilterNode filter = new FieldNode("name")
            .infix(new EqualOperator(), new InputNode("alpha"));

        assertThat(converter.convert(filter)).isEqualTo("name : 'alpha'");
    }

    @Test
    void rendersLogicalComposition() {
        FilterNode filter = new FieldNode("price")
            .infix(new GreaterThanOperator(), new InputNode(5))
            .infix(new AndOperator(), new FieldNode("name").postfix(new IsNotNullOperator()));

        assertThat(converter.convert(filter)).isEqualTo("price > '5' and name is not null");
    }

    @Test
    void rendersNegationCollectionAndPriority() {
        FilterNode filter = new PriorityNode(
            new FieldNode("status").infix(new InOperator(),
                new CollectionNode(List.of(new InputNode("OPEN"), new InputNode("CLOSED")))))
            .prefix(new NotOperator());

        // Collections render bracketed; the 4.x grammar tokenizes both '[' and '('.
        assertThat(converter.convert(filter))
            .isEqualTo("not (status in ['OPEN', 'CLOSED'])");
    }

    @Test
    void rejectsTheStringToNodeDirection() {
        assertThatThrownBy(() -> converter.convert("price > 5"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
