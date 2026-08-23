package io.mycelar.core.routing;

import io.mycelar.core.graph.GraphNode;

public final class LexicalSemanticMatcher implements SemanticMatcher {

    @Override
    public double similarity(String intent, GraphNode node) {
        return Tokens.jaccard(Tokens.tokenize(intent), Tokens.nodeTokens(node));
    }
}
