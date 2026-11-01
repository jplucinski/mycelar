package io.mycelar.core.routing;

import static org.assertj.core.api.Assertions.assertThat;

import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.NodeId;
import io.mycelar.core.graph.NodeMetadata;
import io.mycelar.core.graph.NodeType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TokensTest {

    @Test
    void nullAndPunctuationOnlyAreEmpty() {
        assertThat(Tokens.tokenize(null)).isEmpty();
        assertThat(Tokens.tokenize("!!!")).isEmpty();
    }

    @Test
    void dottedIdDropsEmptySegments() {
        GraphNode node =
                new GraphNode(
                        new NodeId("a..b"),
                        NodeType.DOMAIN,
                        new NodeMetadata("label", "", List.of(), List.of(), Map.of()),
                        List.of(),
                        null,
                        null);
        assertThat(Tokens.nodeTokens(node)).contains("a", "b").doesNotContain("");
    }
}
