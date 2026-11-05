package io.mycelar.springai.context;

import io.mycelar.core.capability.BlockedCapability;
import io.mycelar.core.capability.CapabilityId;
import io.mycelar.core.constraint.ConstraintId;
import io.mycelar.core.context.ResolvedCapsule;
import io.mycelar.core.graph.NodeId;
import io.mycelar.core.routing.RouteCandidate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class MycelarContextRenderer {

    private final boolean includeCapabilitiesInContext;
    private final boolean includeBlockedInContext;

    public MycelarContextRenderer(
            boolean includeCapabilitiesInContext, boolean includeBlockedInContext) {
        this.includeCapabilitiesInContext = includeCapabilitiesInContext;
        this.includeBlockedInContext = includeBlockedInContext;
    }

    public String render(RouteCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        StringBuilder sb = new StringBuilder();
        sb.append("<mycelar-context>\n");
        sb.append("Path: ");
        sb.append(candidate.path().nodeIds().stream().map(NodeId::value).collect(Collectors.joining(" / ")));
        sb.append('\n');

        List<ResolvedCapsule> capsules = candidate.contextStack().capsules();
        if (!capsules.isEmpty()) {
            sb.append('\n');
            sb.append("Context:\n");
            for (ResolvedCapsule resolved : capsules) {
                sb.append("- ").append(resolved.capsule().text()).append('\n');
            }
        }

        List<String> allowed =
                candidate.capabilityScope().allowed().stream()
                        .map(CapabilityId::value)
                        .sorted()
                        .toList();
        if (includeCapabilitiesInContext && !allowed.isEmpty()) {
            sb.append('\n');
            sb.append("Available capabilities:\n");
            for (String id : allowed) {
                sb.append("- ").append(id).append('\n');
            }
        }

        List<BlockedCapability> blocked =
                candidate.capabilityScope().blocked().stream()
                        .sorted(Comparator.comparing(b -> b.capabilityId().value()))
                        .toList();
        if (includeBlockedInContext && !blocked.isEmpty()) {
            sb.append('\n');
            sb.append("Blocked:\n");
            for (BlockedCapability b : blocked) {
                String constraints =
                        b.constraintIds().stream()
                                .map(ConstraintId::value)
                                .sorted()
                                .collect(Collectors.joining(", "));
                sb.append("- ")
                        .append(b.capabilityId().value())
                        .append(": ")
                        .append(b.decision().name())
                        .append(" (")
                        .append(constraints)
                        .append(")\n");
            }
        }

        sb.append("</mycelar-context>");
        return sb.toString();
    }
}
