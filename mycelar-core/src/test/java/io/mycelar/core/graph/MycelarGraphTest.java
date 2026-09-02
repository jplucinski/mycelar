package io.mycelar.core.graph;

import static org.assertj.core.api.Assertions.assertThat;

import io.mycelar.core.capability.CapabilityDescriptor;
import io.mycelar.core.capability.CapabilityId;
import io.mycelar.core.capability.GatewayId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MycelarGraphTest {

    private GraphNode node(String id, NodeType type) {
        return new GraphNode(
                new NodeId(id),
                type,
                new NodeMetadata(id, "", List.of(), List.of(), Map.of()),
                List.of(),
                Optional.empty(),
                Optional.empty());
    }

    @Test
    void walkRootsAreInDegreeZero() {
        GraphNode crm = node("crm", NodeType.DOMAIN);
        GraphNode leads = node("leads", NodeType.RESOURCE);
        GraphEdge e =
                new GraphEdge(
                        new EdgeId("e1"),
                        new NodeId("crm"),
                        new NodeId("leads"),
                        EdgeType.CONTAINS,
                        1.0,
                        new EdgeMetadata(Map.of()));
        MycelarGraph graph = MycelarGraph.builder().node(crm).node(leads).edge(e).build();
        assertThat(graph.walkRoots()).containsExactly(new NodeId("crm"));
    }

    @Test
    void cycleOnlyComponentAllNodesAreRoots() {
        GraphNode a = node("a", NodeType.CONTEXT);
        GraphNode b = node("b", NodeType.CONTEXT);
        GraphNode c = node("c", NodeType.CONTEXT);
        MycelarGraph graph =
                MycelarGraph.builder()
                        .node(a)
                        .node(b)
                        .node(c)
                        .edge(rel("ab", "a", "b"))
                        .edge(rel("bc", "b", "c"))
                        .edge(rel("ca", "c", "a"))
                        .build();
        assertThat(graph.walkRoots())
                .containsExactly(new NodeId("a"), new NodeId("b"), new NodeId("c"));
    }

    private GraphEdge rel(String id, String s, String t) {
        return new GraphEdge(
                new EdgeId(id),
                new NodeId(s),
                new NodeId(t),
                EdgeType.RELATES_TO,
                1.0,
                new EdgeMetadata(Map.of()));
    }

    @Test
    void withWeightsClonesTopology() {
        GraphNode crm = node("crm", NodeType.DOMAIN);
        GraphNode leads = node("leads", NodeType.RESOURCE);
        GraphEdge e =
                new GraphEdge(
                        new EdgeId("e1"),
                        new NodeId("crm"),
                        new NodeId("leads"),
                        EdgeType.CONTAINS,
                        0.5,
                        new EdgeMetadata(Map.of()));
        MycelarGraph graph =
                MycelarGraph.builder().node(crm).node(leads).edge(e).build()
                        .withWeights(Map.of(new EdgeId("e1"), 0.9));
        assertThat(graph.outgoing(new NodeId("crm")).get(0).weight()).isEqualTo(0.9);
    }

    @Test
    void capabilityReturnsDescriptorById() {
        CapabilityDescriptor descriptor =
                new CapabilityDescriptor(
                        new CapabilityId("read"),
                        "read",
                        "read a lead",
                        new GatewayId("crm"),
                        "readLead",
                        Map.of());
        GraphNode node =
                new GraphNode(
                        new NodeId("read"),
                        NodeType.CAPABILITY,
                        new NodeMetadata("read", "read", List.of(), List.of(), Map.of()),
                        List.of(),
                        Optional.of(descriptor),
                        Optional.empty());
        MycelarGraph graph = MycelarGraph.builder().node(node).build();

        assertThat(graph.capability(new CapabilityId("read"))).contains(descriptor);
        assertThat(graph.capability(new CapabilityId("missing"))).isEmpty();
    }

    @Test
    void capabilityIndexSurvivesWithWeights() {
        CapabilityDescriptor descriptor =
                new CapabilityDescriptor(
                        new CapabilityId("read"),
                        "read",
                        "read a lead",
                        new GatewayId("crm"),
                        "readLead",
                        Map.of());
        GraphNode node =
                new GraphNode(
                        new NodeId("read"),
                        NodeType.CAPABILITY,
                        new NodeMetadata("read", "read", List.of(), List.of(), Map.of()),
                        List.of(),
                        Optional.of(descriptor),
                        Optional.empty());
        MycelarGraph graph = MycelarGraph.builder().node(node).build();

        assertThat(graph.withWeights(Map.of()).capability(new CapabilityId("read")))
                .contains(descriptor);
    }
}
