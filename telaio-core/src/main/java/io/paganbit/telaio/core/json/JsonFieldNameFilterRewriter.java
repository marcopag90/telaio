package io.paganbit.telaio.core.json;

import com.turkraft.springfilter.parser.node.*;

import java.util.List;

/**
 * Rewrites a Turkraft {@link FilterNode} tree so that every field reference expressed with its
 * <em>JSON</em> wire name is replaced by the underlying <em>Java</em> property name.
 *
 * <p>Turkraft resolves the name carried by each {@link FieldNode} directly against the JPA metamodel
 * (it never consults Jackson), so a client filtering on a {@code @JsonProperty}-renamed field — e.g.
 * {@code cost_price > 100} for a property {@code costPrice} — would otherwise fail to resolve. This
 * rewriter walks the parsed tree and translates only {@code FieldNode} names via
 * {@link JsonPropertyPathResolver#toJavaPath}; literals ({@code InputNode}), placeholders, operators, and
 * functions are preserved verbatim, and the tree shape is unchanged.</p>
 *
 * <p>This operates purely on the Turkraft <em>core</em> parse tree and Jackson introspection, so it lives
 * in telaio-core; the JPA-specific wiring that applies it (a {@code FilterSpecificationConverter}
 * decorator) lives in telaio-jpa.</p>
 *
 * <p>Translation is lenient: names that are already Java names, or that do not correspond to any known
 * JSON name, pass through untouched — so the rewrite is purely additive and never breaks a filter that
 * already worked.</p>
 *
 * @author Marco Pagan
 * @since 1.0.0
 */
public class JsonFieldNameFilterRewriter {

    private final JsonPropertyPathResolver pathResolver;

    public JsonFieldNameFilterRewriter(JsonPropertyPathResolver pathResolver) {
        this.pathResolver = pathResolver;
    }

    /**
     * Returns a copy of {@code node} with every {@link FieldNode} name translated from its JSON wire
     * name to the corresponding Java property name on {@code entityType}.
     *
     * @param node       the parsed filter tree
     * @param entityType the root entity type the field names are resolved against
     * @return a structurally identical tree with field names rewritten to Java property names
     */
    public FilterNode rewrite(FilterNode node, Class<?> entityType) {
        return switch (node) {
            case FieldNode field -> rewriteField(field, entityType);
            case InfixOperationNode infix -> new InfixOperationNode(
                rewrite(infix.getLeft(), entityType), infix.getOperator(), rewrite(infix.getRight(), entityType));
            case PrefixOperationNode prefix -> new PrefixOperationNode(
                prefix.getOperator(), rewrite(prefix.getRight(), entityType));
            case PostfixOperationNode postfix -> new PostfixOperationNode(
                rewrite(postfix.getLeft(), entityType), postfix.getOperator());
            case FunctionNode function -> new FunctionNode(
                function.getFunction(), rewriteAll(function.getArguments(), entityType));
            case CollectionNode collection -> new CollectionNode(rewriteAll(collection.getItems(), entityType));
            case PriorityNode priority -> new PriorityNode(rewrite(priority.getNode(), entityType));
            // InputNode, PlaceholderNode and any other leaf carry no field reference.
            default -> node;
        };
    }

    private FieldNode rewriteField(FieldNode field, Class<?> entityType) {
        String javaName = pathResolver.toJavaPath(entityType, field.getName());
        return javaName.equals(field.getName()) ? field : new FieldNode(javaName);
    }

    private List<FilterNode> rewriteAll(List<FilterNode> nodes, Class<?> entityType) {
        return nodes.stream().map(child -> rewrite(child, entityType)).toList();
    }
}
