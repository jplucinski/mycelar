package io.mycelar.routing.springai.fixture;

import io.mycelar.core.capability.CapabilityDescriptor;
import io.mycelar.core.capability.CapabilityId;
import io.mycelar.core.capability.GatewayId;
import io.mycelar.core.graph.EdgeId;
import io.mycelar.core.graph.EdgeMetadata;
import io.mycelar.core.graph.EdgeType;
import io.mycelar.core.graph.GraphEdge;
import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.MycelarGraph;
import io.mycelar.core.graph.NodeId;
import io.mycelar.core.graph.NodeMetadata;
import io.mycelar.core.graph.NodeType;
import io.mycelar.routing.springai.embedding.NodeRoutingTextEncoder;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DetailGraphFixture {

    private DetailGraphFixture() {}

    public static MycelarGraph graph() {
        return MycelarGraph.builder()
                .node(resource("detail", "CRM lead detail"))
                .node(
                        capability(
                                "draft-email",
                                "Prepare an email or follow-up message for the lead without sending it.",
                                List.of("write them an email", "napisz mu maila")))
                .node(
                        capability(
                                "send-email",
                                "Send an existing prepared email to the CRM lead.",
                                List.of("send the message")))
                .node(
                        capability(
                                "add-note",
                                "Add an internal CRM note about the lead.",
                                List.of("add a note")))
                .node(
                        capability(
                                "update",
                                "Modify CRM lead fields or properties.",
                                List.of("change their status")))
                .edge(contains("detail-draft", "detail", "draft-email"))
                .edge(contains("detail-send", "detail", "send-email"))
                .edge(contains("detail-note", "detail", "add-note"))
                .edge(contains("detail-update", "detail", "update"))
                .build();
    }

    public static MapEmbeddingModel model(NodeRoutingTextEncoder encoder) {
        MycelarGraph graph = graph();
        MapEmbeddingModel model =
                new MapEmbeddingModel()
                        .put("write a follow-up email", 1f, 0f)
                        .put("prepare a follow-up email", 1f, 0f);
        for (GraphNode node : graph.nodes()) {
            String encoded = encoder.encode(node);
            model.put(encoded, vectorFor(node.id().value()));
        }
        return model;
    }

    private static float[] vectorFor(String nodeId) {
        return switch (nodeId) {
            case "draft-email" -> new float[] {0.99f, 0.01f};
            case "send-email" -> new float[] {0.6f, 0.4f};
            case "add-note" -> new float[] {0.2f, 0.8f};
            case "update" -> new float[] {0f, 1f};
            default -> new float[] {0.5f, 0.5f};
        };
    }

    private static GraphNode resource(String id, String description) {
        return new GraphNode(
                new NodeId(id),
                NodeType.RESOURCE,
                new NodeMetadata(id, description, List.of(), List.of(), Map.of()),
                List.of(),
                null,
                null);
    }

    private static GraphNode capability(String id, String description, List<String> keywords) {
        return new GraphNode(
                new NodeId(id),
                NodeType.CAPABILITY,
                new NodeMetadata(id, description, List.of(), keywords, Map.of()),
                List.of(),
                new CapabilityDescriptor(
                        new CapabilityId(id),
                        id,
                        description,
                        new GatewayId("crm"),
                        id,
                        Map.of()),
                null);
    }

    private static GraphEdge contains(String edgeId, String source, String target) {
        return new GraphEdge(
                new EdgeId(edgeId),
                new NodeId(source),
                new NodeId(target),
                EdgeType.CONTAINS,
                1.0,
                new EdgeMetadata(Map.of()));
    }
}
