package io.mycelar.springai.context;

import static org.assertj.core.api.Assertions.assertThat;

import io.mycelar.core.capability.BlockedCapability;
import io.mycelar.core.capability.CapabilityId;
import io.mycelar.core.capability.CapabilityScope;
import io.mycelar.core.constraint.ConstraintDecision;
import io.mycelar.core.constraint.ConstraintId;
import io.mycelar.core.context.CapsuleType;
import io.mycelar.core.context.ContextCapsule;
import io.mycelar.core.context.ContextStack;
import io.mycelar.core.context.ResolvedCapsule;
import io.mycelar.core.graph.CapsuleId;
import io.mycelar.core.graph.EdgeId;
import io.mycelar.core.graph.NodeId;
import io.mycelar.core.routing.RouteCandidate;
import io.mycelar.core.routing.RoutePath;
import io.mycelar.core.routing.RouteScore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MycelarContextRendererTest {

    @Test
    void rendersPathContextCapabilitiesAndBlockedInAssemblerOrder() {
        ContextCapsule first =
                new ContextCapsule(
                        new CapsuleId("c1"),
                        CapsuleType.INSTRUCTION,
                        "first capsule",
                        1,
                        1.0,
                        Optional.empty());
        ContextCapsule second =
                new ContextCapsule(
                        new CapsuleId("c2"),
                        CapsuleType.DOMAIN_CONTEXT,
                        "second capsule",
                        2,
                        1.0,
                        Optional.empty());
        RouteCandidate candidate =
                new RouteCandidate(
                        new RoutePath(
                                List.of(new NodeId("leads"), new NodeId("update")),
                                List.of(new EdgeId("leads-update"))),
                        new RouteScore(1.0, Map.of()),
                        new ContextStack(
                                List.of(
                                        new ResolvedCapsule(first, new NodeId("leads"), 0),
                                        new ResolvedCapsule(second, new NodeId("update"), 1))),
                        new CapabilityScope(
                                Set.of(new CapabilityId("update"), new CapabilityId("read")),
                                Set.of(
                                        new BlockedCapability(
                                                new CapabilityId("send"),
                                                ConstraintDecision.REQUIRE_CONFIRMATION,
                                                List.of(
                                                        new ConstraintId("z-confirm"),
                                                        new ConstraintId("a-confirm"))))),
                        List.of());

        String rendered = new MycelarContextRenderer(true, true).render(candidate);

        assertThat(rendered)
                .startsWith("<mycelar-context>")
                .endsWith("</mycelar-context>")
                .contains("Path: leads / update")
                .contains(
                        """
                        Context:
                        - first capsule
                        - second capsule
                        """)
                .contains(
                        """
                        Available capabilities:
                        - read
                        - update
                        """)
                .contains("Blocked:")
                .contains("- send: REQUIRE_CONFIRMATION (a-confirm, z-confirm)");
        assertThat(rendered.indexOf("first capsule")).isLessThan(rendered.indexOf("second capsule"));
    }

    @Test
    void omitsOptionalSections() {
        RouteCandidate candidate =
                new RouteCandidate(
                        new RoutePath(List.of(new NodeId("leads")), List.of()),
                        new RouteScore(1.0, Map.of()),
                        new ContextStack(List.of()),
                        new CapabilityScope(Set.of(), Set.of()),
                        List.of());
        String rendered = new MycelarContextRenderer(true, true).render(candidate);
        assertThat(rendered).contains("Path: leads");
        assertThat(rendered).doesNotContain("Context:");
        assertThat(rendered).doesNotContain("Available capabilities:");
        assertThat(rendered).doesNotContain("Blocked:");
    }

    @Test
    void flagsSuppressCapabilitySectionsEvenWhenNonEmpty() {
        RouteCandidate candidate =
                new RouteCandidate(
                        new RoutePath(List.of(new NodeId("leads")), List.of()),
                        new RouteScore(1.0, Map.of()),
                        new ContextStack(List.of()),
                        new CapabilityScope(
                                Set.of(new CapabilityId("read")),
                                Set.of(
                                        new BlockedCapability(
                                                new CapabilityId("send"),
                                                ConstraintDecision.BLOCK,
                                                List.of(new ConstraintId("policy"))))),
                        List.of());
        String rendered = new MycelarContextRenderer(false, false).render(candidate);
        assertThat(rendered).doesNotContain("Available capabilities:");
        assertThat(rendered).doesNotContain("Blocked:");
    }
}
