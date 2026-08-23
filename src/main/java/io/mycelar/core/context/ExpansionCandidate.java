package io.mycelar.core.context;

import io.mycelar.core.graph.EdgeId;
import io.mycelar.core.graph.NodeId;

public record ExpansionCandidate(EdgeId edgeId, NodeId source, NodeId target, double weight) {}
