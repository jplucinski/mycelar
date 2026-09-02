package io.mycelar.core.routing;

import io.mycelar.core.graph.EdgeId;
import io.mycelar.core.graph.GraphEdge;
import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.MycelarGraph;
import io.mycelar.core.graph.NodeId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DefaultRouteScorer implements RouteScorer {

    private final SemanticMatcher matcher;
    private final ScoringWeights weights;
    private final RoutingLimits limits;

    public DefaultRouteScorer(SemanticMatcher matcher, ScoringWeights weights, RoutingLimits limits) {
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.weights = Objects.requireNonNull(weights, "weights");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    @Override
    public RouteScore score(RouteRequest request, RoutePath path, MycelarGraph graph) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(graph, "graph");

        List<GraphNode> nodes = resolveNodes(path, graph);
        List<Double> hopWeights = resolveHopWeights(path, graph);

        double semantic = clamp01(semanticScore(request.intent(), nodes));
        double topology = clamp01(topologyScore(hopWeights));
        double runtimeContext = clamp01(runtimeContextScore(request.context(), nodes));
        double pathPenalty = Math.max(0.0, pathPenalty(path));

        double total =
                weights.semantic() * semantic
                        + weights.topology() * topology
                        + weights.context() * runtimeContext
                        - pathPenalty;

        Map<String, Double> components = new LinkedHashMap<>();
        components.put("semantic", semantic);
        components.put("topology", topology);
        components.put("runtimeContext", runtimeContext);
        components.put("pathPenalty", pathPenalty);
        return new RouteScore(total, components);
    }

    private double semanticScore(String intent, List<GraphNode> nodes) {
        double weightedSum = 0.0;
        double weightSum = 0.0;
        for (int i = 0; i < nodes.size(); i++) {
            double sim = matcher.similarity(intent, nodes.get(i));
            if (!Double.isFinite(sim)) {
                throw new IllegalArgumentException("matcher similarity must be finite");
            }
            double w = i + 1.0;
            weightedSum += sim * w;
            weightSum += w;
        }
        return weightedSum / weightSum;
    }

    private static double topologyScore(List<Double> hopWeights) {
        if (hopWeights.isEmpty()) {
            return 0.5;
        }
        return CandidatePathFinder.geoMean(hopWeights);
    }

    private static double runtimeContextScore(RoutingContext context, List<GraphNode> nodes) {
        Map<String, Object> values = context.asMap();
        if (values.isEmpty()) {
            return 0.5;
        }
        int hits = 0;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (contextHits(entry.getKey(), entry.getValue(), nodes)) {
                hits++;
            }
        }
        return (double) hits / values.size();
    }

    private static boolean contextHits(String key, Object value, List<GraphNode> nodes) {
        String valueToken = String.valueOf(value).toLowerCase(Locale.ROOT);
        for (GraphNode node : nodes) {
            if (Objects.equals(node.metadata().attributes().get(key), value)) {
                return true;
            }
            Set<String> tokens = Tokens.nodeTokens(node);
            if (tokens.contains(valueToken)) {
                return true;
            }
        }
        return false;
    }

    private double pathPenalty(RoutePath path) {
        int maxDepth = limits.maxDepth();
        if (maxDepth == 0) {
            return 0.0;
        }
        int depth = path.edgeIds().size();
        return weights.pathPenaltyAlpha() * depth / maxDepth;
    }

    private static List<GraphNode> resolveNodes(RoutePath path, MycelarGraph graph) {
        List<GraphNode> nodes = new ArrayList<>(path.nodeIds().size());
        for (NodeId id : path.nodeIds()) {
            GraphNode node =
                    graph.node(id)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("unknown node: " + id.value()));
            nodes.add(node);
        }
        return nodes;
    }

    private static List<Double> resolveHopWeights(RoutePath path, MycelarGraph graph) {
        List<Double> weights = new ArrayList<>(path.edgeIds().size());
        for (EdgeId edgeId : path.edgeIds()) {
            GraphEdge edge = findEdge(graph, edgeId);
            weights.add(edge.weight());
        }
        return weights;
    }

    private static GraphEdge findEdge(MycelarGraph graph, EdgeId edgeId) {
        for (GraphEdge edge : graph.edges()) {
            if (edge.id().equals(edgeId)) {
                return edge;
            }
        }
        throw new IllegalArgumentException("unknown edge: " + edgeId.value());
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
