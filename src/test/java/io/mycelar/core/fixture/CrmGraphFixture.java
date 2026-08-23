package io.mycelar.core.fixture;

import io.mycelar.core.capability.CapabilityDescriptor;
import io.mycelar.core.capability.CapabilityId;
import io.mycelar.core.capability.GatewayId;
import io.mycelar.core.constraint.Constraint;
import io.mycelar.core.constraint.ConstraintId;
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

final class CrmGraphFixture {

    private CrmGraphFixture() {}

    static MycelarGraph graph() {
        return MycelarGraph.builder()
                .node(
                        domain(
                                "crm",
                                List.of(
                                        capsule(
                                                "cap-crm",
                                                CapsuleType.INSTRUCTION,
                                                "You are operating on CRM data.",
                                                10)),
                                List.of("crm"),
                                "crm domain"))
                .node(
                        resource(
                                "leads",
                                List.of(
                                        capsule(
                                                "cap-leads",
                                                CapsuleType.DOMAIN_CONTEXT,
                                                "Current entities are leads.",
                                                20)),
                                List.of("leads", "crm"),
                                "leads resource"))
                .node(
                        context(
                                "message",
                                List.of(
                                        capsule(
                                                "cap-msg",
                                                CapsuleType.CAPABILITY_CONTEXT,
                                                "You are preparing external communication.",
                                                30)),
                                List.of("message", "leads", "follow-up", "inactive"),
                                "message context"))
                .node(resource("accounts", List.of(), List.of("accounts", "crm"), "accounts resource"))
                .node(resource("admin", List.of(), List.of("admin", "crm"), "admin resource"))
                .node(capability("search", "search_leads", List.of("search", "leads", "crm")))
                .node(capability("update", "update_leads", List.of("update", "leads", "crm")))
                .node(
                        capability(
                                "draft",
                                "draft_message",
                                List.of("draft", "message", "follow-up", "inactive", "leads")))
                .node(
                        capability(
                                "send",
                                "send_message",
                                List.of("send", "message", "follow-up", "inactive", "leads")))
                .node(capability("delete-all", "delete_all", List.of("delete", "admin")))
                .node(
                        capability(
                                "accounts-search",
                                "search_accounts",
                                List.of("search", "accounts")))
                .node(
                        capability(
                                "accounts-update",
                                "update_accounts",
                                List.of("update", "accounts")))
                .node(
                        constraint(
                                "confirm-before-send",
                                "confirm-before-send",
                                "Require confirmation before send"))
                .node(context("lead-profile", List.of(), List.of("lead", "profile"), "lead profile"))
                .node(
                        context(
                                "interaction-history",
                                List.of(),
                                List.of("interaction", "history"),
                                "interaction history"))
                .node(
                        context(
                                "communication-policy",
                                List.of(),
                                List.of("communication", "policy"),
                                "communication policy"))
                .edge(contains("crm-leads", "crm", "leads"))
                .edge(contains("crm-accounts", "crm", "accounts"))
                .edge(contains("crm-admin", "crm", "admin"))
                .edge(contains("leads-search", "leads", "search"))
                .edge(contains("leads-update", "leads", "update"))
                .edge(contains("leads-message", "leads", "message"))
                .edge(contains("message-draft", "message", "draft"))
                .edge(contains("message-send", "message", "send"))
                .edge(contains("accounts-search", "accounts", "accounts-search"))
                .edge(contains("accounts-update", "accounts", "accounts-update"))
                .edge(contains("admin-delete", "admin", "delete-all"))
                .edge(requires("send-confirm", "send", "confirm-before-send"))
                .edge(expands("message-lead-profile", "message", "lead-profile"))
                .edge(expands("message-interaction-history", "message", "interaction-history"))
                .edge(expands("message-communication-policy", "message", "communication-policy"))
                .build();
    }

    static MycelarGraph weightedForkGraph() {
        return MycelarGraph.builder()
                .node(domain("root", List.of(), List.of("follow-up"), "follow-up"))
                .node(context("heavy", List.of(), List.of("follow-up"), "follow-up"))
                .node(context("light", List.of(), List.of("follow-up"), "follow-up"))
                .edge(relates("root-heavy", "root", "heavy", 0.9))
                .edge(relates("root-light", "root", "light", 0.4))
                .build();
    }

    static MycelarGraph cycleGraph() {
        return MycelarGraph.builder()
                .node(context("a", List.of(), List.of("alpha"), "alpha"))
                .node(context("b", List.of(), List.of("alpha"), "alpha"))
                .node(context("c", List.of(), List.of("alpha"), "alpha"))
                .edge(relates("a-b", "a", "b", 1.0))
                .edge(relates("b-c", "b", "c", 1.0))
                .edge(relates("c-a", "c", "a", 1.0))
                .build();
    }

    private static GraphNode domain(
            String id, List<ContextCapsule> capsules, List<String> keywords, String description) {
        return node(id, NodeType.DOMAIN, capsules, keywords, description, Optional.empty(), Optional.empty());
    }

    private static GraphNode resource(
            String id, List<ContextCapsule> capsules, List<String> keywords, String description) {
        return node(id, NodeType.RESOURCE, capsules, keywords, description, Optional.empty(), Optional.empty());
    }

    private static GraphNode context(
            String id, List<ContextCapsule> capsules, List<String> keywords, String description) {
        return node(id, NodeType.CONTEXT, capsules, keywords, description, Optional.empty(), Optional.empty());
    }

    private static GraphNode capability(String id, String operationId, List<String> keywords) {
        return node(
                id,
                NodeType.CAPABILITY,
                List.of(),
                keywords,
                id + " capability",
                Optional.of(
                        new CapabilityDescriptor(
                                new CapabilityId(id),
                                id,
                                id + " capability",
                                new GatewayId("crm-mcp"),
                                operationId,
                                Map.of())),
                Optional.empty());
    }

    private static GraphNode constraint(String id, String type, String description) {
        return node(
                id,
                NodeType.CONSTRAINT,
                List.of(),
                List.of(),
                description,
                Optional.empty(),
                Optional.of(new Constraint(new ConstraintId(id), type, description, Map.of())));
    }

    private static GraphNode node(
            String id,
            NodeType type,
            List<ContextCapsule> capsules,
            List<String> keywords,
            String description,
            Optional<CapabilityDescriptor> capability,
            Optional<Constraint> constraint) {
        return new GraphNode(
                new NodeId(id),
                type,
                new NodeMetadata(id, description, List.of(), keywords, Map.of()),
                capsules,
                capability,
                constraint);
    }

    private static ContextCapsule capsule(String id, CapsuleType type, String text, int priority) {
        return new ContextCapsule(new CapsuleId(id), type, text, priority, 1.0, Optional.empty());
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

    private static GraphEdge requires(String edgeId, String source, String target) {
        return new GraphEdge(
                new EdgeId(edgeId),
                new NodeId(source),
                new NodeId(target),
                EdgeType.REQUIRES,
                1.0,
                new EdgeMetadata(Map.of()));
    }

    private static GraphEdge expands(String edgeId, String source, String target) {
        return new GraphEdge(
                new EdgeId(edgeId),
                new NodeId(source),
                new NodeId(target),
                EdgeType.EXPANDS_TO,
                1.0,
                new EdgeMetadata(Map.of()));
    }

    private static GraphEdge relates(String edgeId, String source, String target, double weight) {
        return new GraphEdge(
                new EdgeId(edgeId),
                new NodeId(source),
                new NodeId(target),
                EdgeType.RELATES_TO,
                weight,
                new EdgeMetadata(Map.of()));
    }
}
