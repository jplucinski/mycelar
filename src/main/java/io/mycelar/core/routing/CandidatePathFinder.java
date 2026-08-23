package io.mycelar.core.routing;

import io.mycelar.core.graph.EdgeId;
import io.mycelar.core.graph.EdgeType;
import io.mycelar.core.graph.GraphEdge;
import io.mycelar.core.graph.MycelarGraph;
import io.mycelar.core.graph.NodeId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CandidatePathFinder {

    public List<RoutePath> find(MycelarGraph graph, RoutingLimits limits) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(limits, "limits");

        Map<NodeId, List<GraphEdge>> collapsed = collapseHops(graph);
        List<Candidate> candidates = new ArrayList<>();
        Set<NodeId> walkRootSet = new HashSet<>(graph.walkRoots());

        for (NodeId root : graph.walkRoots()) {
            List<NodeId> nodes = new ArrayList<>();
            nodes.add(root);
            dfs(root, nodes, new ArrayList<>(), new ArrayList<>(), collapsed, limits.maxDepth(), candidates);
        }

        List<Candidate> capped = applyCap(candidates, walkRootSet, limits.maxCandidates());
        capped.sort(Comparator.comparing(c -> c.path.nodeIds(), NODE_IDS_LEX));
        return capped.stream().map(c -> c.path).toList();
    }

    private static void dfs(
            NodeId current,
            List<NodeId> nodes,
            List<EdgeId> edges,
            List<Double> weights,
            Map<NodeId, List<GraphEdge>> collapsed,
            int maxDepth,
            List<Candidate> out) {
        out.add(new Candidate(new RoutePath(List.copyOf(nodes), List.copyOf(edges)), List.copyOf(weights)));

        if (edges.size() >= maxDepth) {
            return;
        }

        List<GraphEdge> hops = collapsed.getOrDefault(current, List.of());
        for (GraphEdge hop : hops) {
            NodeId next = hop.target();
            if (nodes.contains(next)) {
                continue;
            }
            nodes.add(next);
            edges.add(hop.id());
            weights.add(hop.weight());
            dfs(next, nodes, edges, weights, collapsed, maxDepth, out);
            weights.remove(weights.size() - 1);
            edges.remove(edges.size() - 1);
            nodes.remove(nodes.size() - 1);
        }
    }

    private static Map<NodeId, List<GraphEdge>> collapseHops(MycelarGraph graph) {
        Map<NodeId, List<GraphEdge>> result = new HashMap<>();
        for (var node : graph.nodes()) {
            Map<NodeId, GraphEdge> bestByTarget = new HashMap<>();
            for (GraphEdge edge : graph.outgoing(node.id())) {
                if (!isWalkable(edge.type())) {
                    continue;
                }
                bestByTarget.merge(edge.target(), edge, CandidatePathFinder::preferHop);
            }
            List<GraphEdge> hops = new ArrayList<>(bestByTarget.values());
            hops.sort(Comparator.comparing(e -> e.target().value()));
            result.put(node.id(), List.copyOf(hops));
        }
        return result;
    }

    private static GraphEdge preferHop(GraphEdge a, GraphEdge b) {
        int byWeight = Double.compare(b.weight(), a.weight());
        if (byWeight != 0) {
            return byWeight < 0 ? a : b;
        }
        int byType = Integer.compare(typeRank(a.type()), typeRank(b.type()));
        if (byType != 0) {
            return byType < 0 ? a : b;
        }
        int byId = a.id().value().compareTo(b.id().value());
        return byId <= 0 ? a : b;
    }

    private static int typeRank(EdgeType type) {
        return type == EdgeType.CONTAINS ? 0 : 1;
    }

    private static boolean isWalkable(EdgeType type) {
        return type == EdgeType.CONTAINS || type == EdgeType.RELATES_TO;
    }

    private static List<Candidate> applyCap(
            List<Candidate> candidates, Set<NodeId> walkRoots, int maxCandidates) {
        if (candidates.size() <= maxCandidates) {
            return new ArrayList<>(candidates);
        }

        List<Candidate> singles = new ArrayList<>();
        List<Candidate> deeper = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c.path.edgeIds().isEmpty() && walkRoots.contains(c.path.nodeIds().getFirst())) {
                singles.add(c);
            } else if (!c.path.edgeIds().isEmpty()) {
                deeper.add(c);
            }
        }

        singles.sort(Comparator.comparing(c -> c.path.nodeIds().getFirst().value()));

        List<Candidate> kept = new ArrayList<>();
        if (singles.size() >= maxCandidates) {
            kept.addAll(singles.subList(0, maxCandidates));
            return kept;
        }

        kept.addAll(singles);
        int remaining = maxCandidates - kept.size();
        deeper.sort(
                Comparator.comparingDouble((Candidate c) -> geoMean(c.weights))
                        .reversed()
                        .thenComparing(c -> c.path.nodeIds(), NODE_IDS_LEX));
        kept.addAll(deeper.subList(0, Math.min(remaining, deeper.size())));
        return kept;
    }

    static double geoMean(List<Double> weights) {
        if (weights.isEmpty()) {
            return 0.0;
        }
        double logSum = 0.0;
        for (double w : weights) {
            if (w == 0.0) {
                return 0.0;
            }
            logSum += Math.log(w);
        }
        return Math.exp(logSum / weights.size());
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

    private record Candidate(RoutePath path, List<Double> weights) {}
}
