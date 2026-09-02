package io.mycelar.core.context;

import io.mycelar.core.graph.NodeId;
import io.mycelar.core.routing.RoutePath;
import io.mycelar.core.routing.RoutingContext;

public record ExpansionRequest(
        RoutePath path, NodeId source, NodeId target, RoutingContext routingContext) {}
