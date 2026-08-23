package io.mycelar.core.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NodeIdTest {

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new NodeId("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityByValue() {
        assertThat(new NodeId("crm")).isEqualTo(new NodeId("crm"));
    }
}
