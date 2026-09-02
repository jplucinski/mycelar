package io.mycelar.springai.fixture;

import io.mycelar.core.capability.CapabilityDescriptor;
import io.mycelar.core.capability.CapabilityId;
import io.mycelar.core.capability.GatewayId;
import io.mycelar.core.context.CapsuleType;
import io.mycelar.core.context.ContextCapsule;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class LeadGraphFixture {

    private LeadGraphFixture() {}

    public static MycelarGraph graph() {
        return MycelarGraph.builder()
                .node(resource("leads", "leads resource", List.of("leads", "update", "lead"), leadCapsule()))
                .node(resource("comms", "comms resource", List.of("email", "send"), List.of()))
                .node(capability("read", "readLead", List.of("read", "lead")))
                .node(capability("update", "updateLead", List.of("update", "lead")))
                .node(capability("send", "sendEmail", List.of("email", "send")))
                .edge(contains("leads-read", "leads", "read"))
                .edge(contains("leads-update", "leads", "update"))
                .edge(contains("comms-send", "comms", "send"))
                .build();
    }

    private static List<ContextCapsule> leadCapsule() {
        return List.of(
                new ContextCapsule(
                        new CapsuleId("cap-leads"),
                        CapsuleType.DOMAIN_CONTEXT,
                        "Current entities are leads.",
                        20,
                        1.0,
                        Optional.empty()));
    }

    private static GraphNode resource(
            String id, String description, List<String> keywords, List<ContextCapsule> capsules) {
        return new GraphNode(
                new NodeId(id),
                NodeType.RESOURCE,
                new NodeMetadata(id, description, List.of(), keywords, Map.of()),
                capsules,
                Optional.empty(),
                Optional.empty());
    }

    private static GraphNode capability(String id, String operationId, List<String> keywords) {
        return new GraphNode(
                new NodeId(id),
                NodeType.CAPABILITY,
                new NodeMetadata(id, id, List.of(), keywords, Map.of()),
                List.of(),
                Optional.of(
                        new CapabilityDescriptor(
                                new CapabilityId(id),
                                id,
                                id,
                                new GatewayId("crm"),
                                operationId,
                                Map.of())),
                Optional.empty());
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
