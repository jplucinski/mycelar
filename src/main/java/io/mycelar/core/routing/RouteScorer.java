package io.mycelar.core.routing;

import io.mycelar.core.graph.MycelarGraph;

public interface RouteScorer {
    RouteScore score(RouteRequest request, RoutePath path, MycelarGraph graph);
}
