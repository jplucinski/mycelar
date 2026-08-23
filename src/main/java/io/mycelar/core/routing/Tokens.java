package io.mycelar.core.routing;

import io.mycelar.core.graph.GraphNode;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class Tokens {

    private Tokens() {}

    public static Set<String> tokenize(String text) {
        if (text == null) {
            return Set.of();
        }
        Set<String> tokens = new HashSet<>();
        for (String part : text.toLowerCase(Locale.ROOT).split("\\W+")) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return Set.copyOf(tokens);
    }

    public static Set<String> nodeTokens(GraphNode node) {
        Set<String> tokens = new HashSet<>();
        for (String part : node.id().value().toLowerCase(Locale.ROOT).split("[.\\-]+")) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        tokens.addAll(tokenize(node.metadata().label()));
        tokens.addAll(tokenize(node.metadata().description()));
        for (String alias : node.metadata().aliases()) {
            tokens.addAll(tokenize(alias));
        }
        for (String keyword : node.metadata().keywords()) {
            tokens.addAll(tokenize(keyword));
        }
        return Set.copyOf(tokens);
    }

    public static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        for (String token : a) {
            if (b.contains(token)) {
                intersection++;
            }
        }
        int union = a.size() + b.size() - intersection;
        return (double) intersection / union;
    }
}
