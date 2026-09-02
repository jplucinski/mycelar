package io.mycelar.core.routing;

import io.mycelar.core.capability.CapabilityResolver;
import io.mycelar.core.capability.CapabilityScope;
import io.mycelar.core.constraint.ConstraintEvaluator;
import io.mycelar.core.context.ContextAssembler;
import io.mycelar.core.context.ContextStack;
import io.mycelar.core.context.ExpansionCandidate;
import io.mycelar.core.graph.EdgeType;
import io.mycelar.core.graph.GraphEdge;
import io.mycelar.core.graph.MycelarGraph;
import io.mycelar.core.graph.NodeId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class MycelarRuntime {

    private final MycelarGraph graph;
    private final RouteScorer routeScorer;
    private final RoutingLimits routingLimits;
    private final List<ConstraintEvaluator> constraintEvaluators;
    private final CandidatePathFinder pathFinder;
    private final ContextAssembler contextAssembler;
    private final CapabilityResolver capabilityResolver;

    private MycelarRuntime(
            MycelarGraph graph,
            RouteScorer routeScorer,
            RoutingLimits routingLimits,
            List<ConstraintEvaluator> constraintEvaluators) {
        this.graph = graph;
        this.routeScorer = routeScorer;
        this.routingLimits = routingLimits;
        this.constraintEvaluators = constraintEvaluators;
        this.pathFinder = new CandidatePathFinder();
        this.contextAssembler = new ContextAssembler();
        this.capabilityResolver = new CapabilityResolver();
    }

    public static Builder builder() {
        return new Builder();
    }

    public MycelarGraph graph() {
        return graph;
    }

    public RouteResult route(RouteRequest request) {
        Objects.requireNonNull(request, "request");

        List<RoutePath> paths = pathFinder.find(graph, routingLimits);
        if (paths.isEmpty()) {
            return new RouteResult(List.of());
        }

        List<ScoredPath> scored = new ArrayList<>(paths.size());
        for (RoutePath path : paths) {
            RouteScore score = routeScorer.score(request, path, graph);
            if (!Double.isFinite(score.total())) {
                throw new IllegalArgumentException("route score total must be finite");
            }
            scored.add(new ScoredPath(path, score));
        }

        scored.sort(
                Comparator.comparingDouble((ScoredPath s) -> s.score.total())
                        .reversed()
                        .thenComparing(s -> s.path.nodeIds(), NODE_IDS_LEX));

        int limit = Math.min(request.topK(), scored.size());
        List<RouteCandidate> candidates = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            ScoredPath sp = scored.get(i);
            ContextStack stack = contextAssembler.assemble(graph, sp.path);
            CapabilityScope scope =
                    capabilityResolver.resolve(
                            graph, sp.path, request.context(), constraintEvaluators);
            List<ExpansionCandidate> expansions = collectExpansions(sp.path);
            candidates.add(new RouteCandidate(sp.path, sp.score, stack, scope, expansions));
        }
        return new RouteResult(List.copyOf(candidates));
    }

    public RouteResult route(String intent, RoutingContext context) {
        return route(RouteRequest.builder().intent(intent).context(context).topK(3).build());
    }

    private List<ExpansionCandidate> collectExpansions(RoutePath path) {
        List<ExpansionCandidate> out = new ArrayList<>();
        for (NodeId id : path.nodeIds()) {
            for (GraphEdge e : graph.outgoing(id, EdgeType.EXPANDS_TO)) {
                out.add(new ExpansionCandidate(e.id(), e.source(), e.target(), e.weight()));
            }
        }
        out.sort(Comparator.comparing(e -> e.edgeId().value()));
        return List.copyOf(out);
    }

    private static final Comparator<List<NodeId>> NODE_IDS_LEX =
            (a, b) -> {
                int n = Math.min(a.size(), b.size());
                for (int i = 0; i < n; i++) {
                    int cmp = a.get(i).value().compareTo(b.get(i).value());
                    if (cmp != 0) {
                        return cmp;
                    }
                }
                return Integer.compare(a.size(), b.size());
            };

    private record ScoredPath(RoutePath path, RouteScore score) {}

    public static final class Builder {
        private MycelarGraph graph;
        private SemanticMatcher semanticMatcher;
        private RouteScorer routeScorer;
        private RoutingLimits routingLimits;
        private List<ConstraintEvaluator> constraintEvaluators;

        private Builder() {}

        public Builder graph(MycelarGraph graph) {
            this.graph = graph;
            return this;
        }

        public Builder semanticMatcher(SemanticMatcher semanticMatcher) {
            this.semanticMatcher = semanticMatcher;
            return this;
        }

        public Builder routeScorer(RouteScorer routeScorer) {
            this.routeScorer = routeScorer;
            return this;
        }

        public Builder routingLimits(RoutingLimits routingLimits) {
            this.routingLimits = routingLimits;
            return this;
        }

        public Builder constraintEvaluators(List<ConstraintEvaluator> constraintEvaluators) {
            this.constraintEvaluators = constraintEvaluators;
            return this;
        }

        public MycelarRuntime build() {
            if (graph == null) {
                throw new IllegalArgumentException("graph is required");
            }

            SemanticMatcher matcher =
                    semanticMatcher == null ? new LexicalSemanticMatcher() : semanticMatcher;
            RoutingLimits limits =
                    routingLimits == null ? RoutingLimits.defaults() : routingLimits;
            RouteScorer scorer =
                    routeScorer == null
                            ? new DefaultRouteScorer(matcher, ScoringWeights.defaults(), limits)
                            : routeScorer;
            List<ConstraintEvaluator> evaluators =
                    constraintEvaluators == null
                            ? List.of()
                            : List.copyOf(constraintEvaluators);

            return new MycelarRuntime(graph, scorer, limits, evaluators);
        }
    }
}
