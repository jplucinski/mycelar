package io.mycelar.routing.springai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mycelar.routing.springai.fixture.Nodes;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultNodeRoutingTextEncoderTest {

    private final NodeRoutingTextEncoder encoder = new DefaultNodeRoutingTextEncoder();

    @Test
    void encodesLabelDescriptionAliasesKeywordsInOrder() {
        var node =
                Nodes.node(
                        "draft",
                        "Draft email",
                        "Prepare an email without sending it.",
                        List.of("compose", "write"),
                        List.of("write them an email", "napisz mu maila"));
        assertThat(encoder.encode(node))
                .isEqualTo(
                        """
                        Label:
                        Draft email

                        Description:
                        Prepare an email without sending it.

                        Aliases:
                        - compose
                        - write

                        Keywords:
                        - write them an email
                        - napisz mu maila""");
    }

    @Test
    void omitsBlankDescriptionAndEmptyLists() {
        var node = Nodes.node("n", "OnlyLabel", "  ", List.of(), List.of());
        assertThat(encoder.encode(node)).isEqualTo("Label:\nOnlyLabel");
    }

    @Test
    void trimsDedupesFirstWinsAndDropsBlanks() {
        var node =
                Nodes.node(
                        "n",
                        " L ",
                        " D ",
                        List.of(" a ", "a", "b", " ", "a"),
                        List.of("k", " k ", ""));
        assertThat(encoder.encode(node))
                .isEqualTo(
                        """
                        Label:
                        L

                        Description:
                        D

                        Aliases:
                        - a
                        - b

                        Keywords:
                        - k""");
    }

    @Test
    void nullNodeThrowsNpe() {
        assertThatThrownBy(() -> encoder.encode(null)).isInstanceOf(NullPointerException.class);
    }
}
