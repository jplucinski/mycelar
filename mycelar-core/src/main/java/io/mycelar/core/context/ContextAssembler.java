package io.mycelar.core.context;

import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.MycelarGraph;
import io.mycelar.core.graph.NodeId;
import io.mycelar.core.routing.RoutePath;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ContextAssembler {

    public ContextStack assemble(MycelarGraph graph, RoutePath path) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(path, "path");

        List<Entry> entries = new ArrayList<>();
        List<NodeId> nodeIds = path.nodeIds();
        for (int i = 0; i < nodeIds.size(); i++) {
            NodeId nodeId = nodeIds.get(i);
            Optional<GraphNode> maybeNode = graph.node(nodeId);
            if (maybeNode.isEmpty()) {
                continue;
            }
            GraphNode node = maybeNode.get();
            List<ContextCapsule> capsules = node.capsules();
            for (int j = 0; j < capsules.size(); j++) {
                entries.add(new Entry(new ResolvedCapsule(capsules.get(j), nodeId, i), j));
            }
        }

        entries.sort(
                Comparator.comparingInt((Entry e) -> e.resolved().capsule().priority())
                        .reversed()
                        .thenComparing(Comparator.comparingInt((Entry e) -> e.resolved().pathIndex()).reversed())
                        .thenComparingInt(Entry::listIndex)
                        .thenComparing(e -> e.resolved().capsule().id().value()));

        return new ContextStack(entries.stream().map(Entry::resolved).toList());
    }

    private record Entry(ResolvedCapsule resolved, int listIndex) {}
}
