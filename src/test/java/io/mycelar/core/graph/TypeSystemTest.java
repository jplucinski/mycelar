package io.mycelar.core.graph;

import static org.assertj.core.api.Assertions.assertThat;

import io.mycelar.core.constraint.ConstraintDecision;
import org.junit.jupiter.api.Test;

class TypeSystemTest {

    @Test
    void nodeTypesMatchSpec() {
        assertThat(NodeType.values())
                .containsExactly(
                        NodeType.DOMAIN,
                        NodeType.RESOURCE,
                        NodeType.CAPABILITY,
                        NodeType.CONTEXT,
                        NodeType.CONSTRAINT);
    }

    @Test
    void edgeTypesMatchSpec() {
        assertThat(EdgeType.values())
                .containsExactly(
                        EdgeType.CONTAINS,
                        EdgeType.RELATES_TO,
                        EdgeType.REQUIRES,
                        EdgeType.EXPANDS_TO);
    }

    @Test
    void constraintDecisionsMatchSpec() {
        assertThat(ConstraintDecision.values())
                .containsExactly(
                        ConstraintDecision.ALLOW,
                        ConstraintDecision.BLOCK,
                        ConstraintDecision.REQUIRE_CONFIRMATION,
                        ConstraintDecision.UNRESOLVED);
    }
}
