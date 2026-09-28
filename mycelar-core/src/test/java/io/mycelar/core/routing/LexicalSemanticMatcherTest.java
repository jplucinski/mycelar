package io.mycelar.core.routing;

import static org.assertj.core.api.Assertions.assertThat;

import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.NodeId;
import io.mycelar.core.graph.NodeMetadata;
import io.mycelar.core.graph.NodeType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LexicalSemanticMatcherTest {

    @Test
    void jaccardOnSharedTokens() {
        GraphNode node =
                new GraphNode(
                        new NodeId("leads.search"),
                        NodeType.CAPABILITY,
                        new NodeMetadata("Search leads", "find inactive leads", List.of("lookup"), List.of("crm"), Map.of()),
                        List.of(),
                        null,
                        null);
        double sim = new LexicalSemanticMatcher().similarity("search inactive leads", node);
        assertThat(sim).isGreaterThan(0.0);
        assertThat(sim).isLessThanOrEqualTo(1.0);
    }

    @Test
    void emptyIntentIsZero() {
        GraphNode node =
                new GraphNode(
                        new NodeId("crm"),
                        NodeType.DOMAIN,
                        new NodeMetadata("CRM", "", List.of(), List.of(), Map.of()),
                        List.of(),
                        null,
                        null);
        assertThat(new LexicalSemanticMatcher().similarity("", node)).isZero();
    }
}
