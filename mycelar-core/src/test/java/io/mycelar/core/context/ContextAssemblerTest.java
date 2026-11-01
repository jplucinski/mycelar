package io.mycelar.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import io.mycelar.core.graph.CapsuleId;
import io.mycelar.core.graph.EdgeId;
import io.mycelar.core.graph.EdgeMetadata;
import io.mycelar.core.graph.EdgeType;
import io.mycelar.core.graph.GraphEdge;
import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.MycelarGraph;
import io.mycelar.core.graph.NodeId;
import io.mycelar.core.graph.NodeMetadata;
import io.mycelar.core.graph.NodeType;
import io.mycelar.core.routing.RoutePath;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContextAssemblerTest {

    @Test
    void sortsPriorityThenLaterPathNode() {
        ContextCapsule low =
                new ContextCapsule(new CapsuleId("a"), CapsuleType.DOMAIN_CONTEXT, "crm", 1, 1.0, null);
        ContextCapsule highEarly =
                new ContextCapsule(new CapsuleId("b"), CapsuleType.DOMAIN_CONTEXT, "leads", 5, 1.0, null);
        ContextCapsule highLate =
                new ContextCapsule(new CapsuleId("c"), CapsuleType.DOMAIN_CONTEXT, "msg", 5, 1.0, null);
        GraphNode crm =
                new GraphNode(
                        new NodeId("crm"),
                        NodeType.DOMAIN,
                        new NodeMetadata("crm", "", List.of(), List.of(), Map.of()),
                        List.of(low, highEarly),
                        null,
                        null);
        GraphNode leads =
                new GraphNode(
                        new NodeId("leads"),
                        NodeType.RESOURCE,
                        new NodeMetadata("leads", "", List.of(), List.of(), Map.of()),
                        List.of(),
                        null,
                        null);
        GraphNode message =
                new GraphNode(
                        new NodeId("message"),
                        NodeType.CONTEXT,
                        new NodeMetadata("message", "", List.of(), List.of(), Map.of()),
                        List.of(highLate),
                        null,
                        null);
        MycelarGraph graph =
                MycelarGraph.builder()
                        .node(crm)
                        .node(leads)
                        .node(message)
                        .edge(
                                new GraphEdge(
                                        new EdgeId("e1"),
                                        new NodeId("crm"),
                                        new NodeId("leads"),
                                        EdgeType.CONTAINS,
                                        1.0,
                                        new EdgeMetadata(Map.of())))
                        .edge(
                                new GraphEdge(
                                        new EdgeId("e2"),
                                        new NodeId("leads"),
                                        new NodeId("message"),
                                        EdgeType.CONTAINS,
                                        1.0,
                                        new EdgeMetadata(Map.of())))
                        .build();
        ContextStack stack =
                new ContextAssembler()
                        .assemble(
                                graph,
                                new RoutePath(
                                        List.of(new NodeId("crm"), new NodeId("leads"), new NodeId("message")),
                                        List.of(new EdgeId("e1"), new EdgeId("e2"))));
        assertThat(stack.capsules())
                .extracting(r -> r.capsule().id().value())
                .containsExactly("c", "b", "a");
    }

    @Test
    void unknownPathNodeIsSkipped() {
        ContextCapsule only =
                new ContextCapsule(new CapsuleId("keep"), CapsuleType.INSTRUCTION, "keep", 1, 1.0, null);
        GraphNode known =
                new GraphNode(
                        new NodeId("known"),
                        NodeType.DOMAIN,
                        new NodeMetadata("known", "", List.of(), List.of(), Map.of()),
                        List.of(only),
                        null,
                        null);
        MycelarGraph graph = MycelarGraph.builder().node(known).build();
        ContextStack stack =
                new ContextAssembler()
                        .assemble(
                                graph,
                                new RoutePath(
                                        List.of(new NodeId("missing"), new NodeId("known")),
                                        List.of(new EdgeId("missing-edge"))));
        assertThat(stack.capsules()).extracting(r -> r.capsule().id().value()).containsExactly("keep");
    }
}
