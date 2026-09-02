package io.mycelar.routing.springai.embedding;

import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.NodeMetadata;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

final class DefaultNodeRoutingTextEncoder implements NodeRoutingTextEncoder {

    @Override
    public String encode(GraphNode node) {
        Objects.requireNonNull(node, "node");
        NodeMetadata meta = node.metadata();
        List<String> sections = new ArrayList<>();
        sections.add("Label:\n" + meta.label().trim());
        String description = meta.description() == null ? "" : meta.description().trim();
        if (!description.isEmpty()) {
            sections.add("Description:\n" + description);
        }
        List<String> aliases = uniqueTrimmed(meta.aliases());
        if (!aliases.isEmpty()) {
            sections.add("Aliases:\n" + bullets(aliases));
        }
        List<String> keywords = uniqueTrimmed(meta.keywords());
        if (!keywords.isEmpty()) {
            sections.add("Keywords:\n" + bullets(keywords));
        }
        return String.join("\n\n", sections);
    }

    private static List<String> uniqueTrimmed(List<String> values) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return List.copyOf(out);
    }

    private static String bullets(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append("- ").append(values.get(i));
        }
        return sb.toString();
    }
}
