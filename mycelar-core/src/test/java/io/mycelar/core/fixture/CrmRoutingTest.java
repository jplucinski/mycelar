package io.mycelar.core.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import io.mycelar.core.capability.BlockedCapability;
import io.mycelar.core.capability.CapabilityId;
import io.mycelar.core.constraint.ConstraintDecision;
import io.mycelar.core.graph.NodeId;
import io.mycelar.core.routing.MycelarRuntime;
import io.mycelar.core.routing.RouteCandidate;
import io.mycelar.core.routing.RouteResult;
import io.mycelar.core.routing.RoutingContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrmRoutingTest {

    private MycelarRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime =
                MycelarRuntime.builder()
                        .graph(CrmGraphFixture.graph())
                        .constraintEvaluators(List.of(new ConfirmEvaluator()))
                        .build();
    }

    private RouteCandidate firstMatching(String intent, RoutingContext ctx, java.util.function.Predicate<RouteCandidate> pred) {
        return runtime.route(intent, ctx).candidates().stream().filter(pred).findFirst().orElseThrow();
    }

    @Test
    void coneHidesAdminDeleteAll() {
        RouteCandidate c =
                firstMatching(
                        "draft a follow-up to inactive leads",
                        RoutingContext.empty(),
                        cand -> cand.path().nodeIds().contains(new NodeId("draft")));
        assertThat(c.capabilityScope().allowed()).contains(new CapabilityId("draft"));
        assertThat(c.capabilityScope().allowed()).doesNotContain(new CapabilityId("delete-all"));
        assertThat(c.capabilityScope().blocked())
                .extracting(BlockedCapability::capabilityId)
                .doesNotContain(new CapabilityId("delete-all"));
    }

    @Test
    void sendRequiresConfirmation() {
        RouteCandidate blocked =
                firstMatching(
                        "send a follow-up to inactive leads",
                        RoutingContext.empty(),
                        cand -> cand.path().nodeIds().contains(new NodeId("send"))
                                || cand.capabilityScope().blocked().stream()
                                        .anyMatch(b -> b.capabilityId().equals(new CapabilityId("send"))));
        assertThat(blocked.capabilityScope().blocked())
                .anyMatch(
                        b ->
                                b.capabilityId().equals(new CapabilityId("send"))
                                        && b.decision() == ConstraintDecision.REQUIRE_CONFIRMATION);
        RouteCandidate allowed =
                firstMatching(
                        "send a follow-up to inactive leads",
                        RoutingContext.of(Map.of("confirmed", true)),
                        cand -> cand.capabilityScope().allowed().contains(new CapabilityId("send")));
        assertThat(allowed.capabilityScope().allowed()).contains(new CapabilityId("send"));
    }

    @Test
    void heavierEqualLexiconPathWins() {
        MycelarRuntime r =
                MycelarRuntime.builder().graph(CrmGraphFixture.weightedForkGraph()).build();
        RouteResult result = r.route("follow-up", RoutingContext.empty());
        assertThat(result.candidates().get(0).path().nodeIds())
                .contains(new NodeId("heavy"));
    }

    @Test
    void stackOnlyPathNodesOrdered() {
        RouteCandidate c =
                runtime
                        .route(
                                io.mycelar.core.routing.RouteRequest.builder()
                                        .intent("message leads crm")
                                        .topK(20)
                                        .build())
                        .candidates()
                        .stream()
                        .filter(
                                cand ->
                                        cand.path()
                                                .nodeIds()
                                                .equals(
                                                        List.of(
                                                                new NodeId("crm"),
                                                                new NodeId("leads"),
                                                                new NodeId("message"))))
                        .findFirst()
                        .orElseThrow();
        assertThat(c.contextStack().capsules())
                .extracting(rc -> rc.capsule().id().value())
                .containsExactly("cap-msg", "cap-leads", "cap-crm");
        assertThat(c.contextStack().capsules())
                .extracting(rc -> rc.sourceNodeId().value())
                .doesNotContain("draft", "send", "search");
    }

    @Test
    void cycleTerminates() {
        MycelarRuntime r =
                MycelarRuntime.builder().graph(CrmGraphFixture.cycleGraph()).build();
        RouteResult result = r.route("alpha", RoutingContext.empty());
        assertThat(result.candidates()).isNotEmpty();
        assertThat(result.candidates())
                .allMatch(c -> c.path().nodeIds().size() == c.path().nodeIds().stream().distinct().count());
    }

    @Test
    void topKReturnsMultiple() {
        RouteResult result =
                runtime.route(
                        io.mycelar.core.routing.RouteRequest.builder()
                                .intent("leads")
                                .topK(3)
                                .build());
        assertThat(result.candidates()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.candidates()).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void expansionsListedNotFetched() {
        RouteCandidate c =
                firstMatching(
                        "message",
                        RoutingContext.empty(),
                        cand -> cand.path().nodeIds().contains(new NodeId("message")));
        assertThat(c.expansions())
                .extracting(e -> e.target().value())
                .contains("interaction-history");
    }

    @Test
    void leadsPathDoesNotIncludeDraft() {
        RouteCandidate c =
                firstMatching(
                        "leads",
                        RoutingContext.empty(),
                        cand -> cand.path().nodeIds().equals(List.of(new NodeId("crm"), new NodeId("leads"))));
        assertThat(c.capabilityScope().allowed())
                .contains(new CapabilityId("search"), new CapabilityId("update"));
        assertThat(c.capabilityScope().allowed())
                .doesNotContain(new CapabilityId("draft"), new CapabilityId("send"));
    }

    @Test
    void missingEvaluatorIsUnresolved() {
        MycelarRuntime bare = MycelarRuntime.builder().graph(CrmGraphFixture.graph()).build();
        RouteCandidate c =
                bare.route("send a follow-up to inactive leads", RoutingContext.empty()).candidates().stream()
                        .filter(cand -> cand.capabilityScope().blocked().stream()
                                .anyMatch(b -> b.capabilityId().equals(new CapabilityId("send"))))
                        .findFirst()
                        .orElseThrow();
        assertThat(c.capabilityScope().blocked())
                .anyMatch(
                        b ->
                                b.capabilityId().equals(new CapabilityId("send"))
                                        && b.decision() == ConstraintDecision.UNRESOLVED);
    }

    @Test
    void definitionOfDoneExplainsSevenQuestions() {
        RouteResult result =
                runtime.route(
                        "send a follow-up to inactive leads",
                        RoutingContext.empty());
        assertThat(result.candidates()).isNotEmpty();
        RouteCandidate top = result.candidates().get(0);
        assertThat(top.path().nodeIds()).isNotEmpty();
        assertThat(top.score().components())
                .containsKeys("semantic", "topology", "runtimeContext", "pathPenalty");
        assertThat(top.contextStack()).isNotNull();
        assertThat(top.expansions()).isNotNull();
        assertThat(top.capabilityScope().allowed()).isNotNull();
        assertThat(top.capabilityScope().blocked()).isNotNull();
        if (!top.capabilityScope().blocked().isEmpty()) {
            assertThat(top.capabilityScope().blocked())
                    .allMatch(b -> !b.constraintIds().isEmpty() || b.decision() == ConstraintDecision.UNRESOLVED);
        }
    }
}
