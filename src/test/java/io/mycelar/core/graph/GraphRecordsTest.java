package io.mycelar.core.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mycelar.core.context.CapsuleType;
import io.mycelar.core.context.ContextCapsule;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphRecordsTest {

    @Test
    void edgeRejectsWeightAboveOne() {
        assertThatThrownBy(
                        () ->
                                new GraphEdge(
                                        new EdgeId("e1"),
                                        new NodeId("a"),
                                        new NodeId("b"),
                                        EdgeType.CONTAINS,
                                        1.1,
                                        new EdgeMetadata(Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void edgeRejectsNaNWeight() {
        assertThatThrownBy(
                        () ->
                                new GraphEdge(
                                        new EdgeId("e1"),
                                        new NodeId("a"),
                                        new NodeId("b"),
                                        EdgeType.CONTAINS,
                                        Double.NaN,
                                        new EdgeMetadata(Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nodeCopiesCapsuleList() {
        ContextCapsule capsule =
                new ContextCapsule(
                        new CapsuleId("c1"),
                        CapsuleType.INSTRUCTION,
                        "stay local",
                        10,
                        1.0,
                        Optional.empty());
        GraphNode node =
                new GraphNode(
                        new NodeId("crm"),
                        NodeType.DOMAIN,
                        new NodeMetadata("CRM", "", List.of(), List.of(), Map.of()),
                        List.of(capsule),
                        Optional.empty(),
                        Optional.empty());
        assertThat(node.capsules()).containsExactly(capsule);
    }
}
