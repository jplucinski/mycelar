package io.mycelar.core.routing;

import io.mycelar.core.capability.CapabilityScope;
import io.mycelar.core.context.ContextStack;
import io.mycelar.core.context.ExpansionCandidate;
import java.util.List;

public record RouteCandidate(
        RoutePath path,
        RouteScore score,
        ContextStack contextStack,
        CapabilityScope capabilityScope,
        List<ExpansionCandidate> expansions) {}
