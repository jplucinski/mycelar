package io.mycelar.springai.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mycelar.core.capability.CapabilityDescriptor;
import io.mycelar.core.capability.CapabilityId;
import io.mycelar.core.capability.GatewayId;
import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.graph.MycelarGraph;
import io.mycelar.core.graph.NodeId;
import io.mycelar.core.graph.NodeMetadata;
import io.mycelar.core.graph.NodeType;
import io.mycelar.core.routing.MycelarRuntime;
import io.mycelar.springai.exception.DuplicateMycelarToolNameException;
import io.mycelar.springai.exception.MycelarConfigurationException;
import io.mycelar.springai.fixture.LeadGraphFixture;
import io.mycelar.springai.fixture.StubTools;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

class CapabilityToolRegistryTest {

    private final MycelarRuntime runtime =
            MycelarRuntime.builder().graph(LeadGraphFixture.graph()).build();

    @Test
    void conventionBindsOperationIdToToolName() {
        CapabilityToolRegistry registry =
                new CapabilityToolRegistry(
                        runtime,
                        List.of(),
                        List.of(
                                StubTools.callback("readLead"),
                                StubTools.callback("updateLead"),
                                StubTools.callback("sendEmail")),
                        List.of(),
                        Map.of());

        var resolved = registry.resolve(Set.of(new CapabilityId("read"), new CapabilityId("update")));
        assertThat(resolved)
                .extracting(b -> b.toolCallback().getToolDefinition().name())
                .containsExactlyInAnyOrder("readLead", "updateLead");
    }

    @Test
    void yamlBeatsConventionForTheSameToolName() {
        CapabilityToolRegistry registry =
                new CapabilityToolRegistry(
                        runtime,
                        List.of(),
                        List.of(StubTools.callback("readLead"), StubTools.callback("updateLead")),
                        List.of(),
                        Map.of("readLead", "update"));

        var forUpdate = registry.resolve(Set.of(new CapabilityId("update")));
        var forRead = registry.resolve(Set.of(new CapabilityId("read")));
        assertThat(forUpdate)
                .extracting(b -> b.toolCallback().getToolDefinition().name())
                .containsExactlyInAnyOrder("readLead", "updateLead");
        assertThat(forRead).isEmpty();
    }

    @Test
    void programmaticBeatsYamlAndIsIdempotent() {
        ToolCallback programmatic = StubTools.callback("readLead");
        CapabilityToolBindings bindings =
                new CapabilityToolBindings(
                        List.of(
                                new CapabilityToolBinding(new CapabilityId("read"), programmatic),
                                new CapabilityToolBinding(new CapabilityId("read"), programmatic)));
        CapabilityToolRegistry registry =
                new CapabilityToolRegistry(
                        runtime,
                        List.of(bindings),
                        List.of(programmatic, StubTools.callback("otherTool")),
                        List.of(),
                        Map.of("readLead", "update"));

        var resolved = registry.resolve(Set.of(new CapabilityId("read")));
        assertThat(resolved).hasSize(1);
        assertThat(resolved.iterator().next().toolCallback()).isSameAs(programmatic);
    }

    @Test
    void unknownAllowedCapabilityContributesNoTools() {
        CapabilityToolRegistry registry =
                new CapabilityToolRegistry(
                        runtime, List.of(), List.of(StubTools.callback("readLead")), List.of(), Map.of());
        assertThat(registry.resolve(Set.of(new CapabilityId("no-such")))).isEmpty();
    }

    @Test
    void duplicateInventoryNameFailsConstruction() {
        assertThatThrownBy(
                        () ->
                                new CapabilityToolRegistry(
                                        runtime,
                                        List.of(),
                                        List.of(StubTools.callback("readLead"), StubTools.callback("readLead")),
                                        List.of(),
                                        Map.of()))
                .isInstanceOf(DuplicateMycelarToolNameException.class);
    }

    @Test
    void providerInventoryIsFlattened() {
        ToolCallbackProvider provider = () -> new ToolCallback[] {StubTools.callback("updateLead")};
        CapabilityToolRegistry registry =
                new CapabilityToolRegistry(
                        runtime,
                        List.of(),
                        List.of(StubTools.callback("readLead")),
                        List.of(provider),
                        Map.of());
        var resolved = registry.resolve(Set.of(new CapabilityId("update")));
        assertThat(resolved)
                .extracting(b -> b.toolCallback().getToolDefinition().name())
                .containsExactly("updateLead");
    }

    @Test
    void sameToolNameTwoCapabilitiesFails() {
        ToolCallback shared = StubTools.callback("shared");
        CapabilityToolBindings bindings =
                new CapabilityToolBindings(
                        List.of(
                                new CapabilityToolBinding(new CapabilityId("read"), shared),
                                new CapabilityToolBinding(new CapabilityId("update"), shared)));
        assertThatThrownBy(
                        () ->
                                new CapabilityToolRegistry(
                                        runtime, List.of(bindings), List.of(shared), List.of(), Map.of()))
                .isInstanceOf(DuplicateMycelarToolNameException.class);
    }

    @Test
    void yamlUnknownToolNameFailsConstruction() {
        assertThatThrownBy(
                        () ->
                                new CapabilityToolRegistry(
                                        runtime,
                                        List.of(),
                                        List.of(StubTools.callback("readLead")),
                                        List.of(),
                                        Map.of("missingTool", "read")))
                .isInstanceOf(MycelarConfigurationException.class);
    }

    @Test
    void yamlBlankCapabilityIdFailsConstruction() {
        assertThatThrownBy(
                        () ->
                                new CapabilityToolRegistry(
                                        runtime,
                                        List.of(),
                                        List.of(StubTools.callback("readLead")),
                                        List.of(),
                                        Map.of("readLead", "  ")))
                .isInstanceOf(MycelarConfigurationException.class);
    }

    @Test
    void strictBindingsFailWhenAllowedHasNoTools() {
        CapabilityToolRegistry registry =
                new CapabilityToolRegistry(
                        runtime, List.of(), List.of(StubTools.callback("readLead")), List.of(), Map.of());
        assertThatThrownBy(
                        () ->
                                registry.resolve(
                                        Set.of(new CapabilityId("read"), new CapabilityId("update")), true))
                .isInstanceOf(MycelarConfigurationException.class);
    }

    @Test
    void duplicateOperationIdFailsConstruction() {
        GraphNode a =
                new GraphNode(
                        new NodeId("read"),
                        NodeType.CAPABILITY,
                        new NodeMetadata("read", "read", List.of(), List.of(), Map.of()),
                        List.of(),
                        new CapabilityDescriptor(
                                new CapabilityId("read"),
                                "read",
                                "read",
                                new GatewayId("crm"),
                                "sharedOp",
                                Map.of()),
                        null);
        GraphNode b =
                new GraphNode(
                        new NodeId("update"),
                        NodeType.CAPABILITY,
                        new NodeMetadata("update", "update", List.of(), List.of(), Map.of()),
                        List.of(),
                        new CapabilityDescriptor(
                                new CapabilityId("update"),
                                "update",
                                "update",
                                new GatewayId("crm"),
                                "sharedOp",
                                Map.of()),
                        null);
        MycelarRuntime dupRuntime =
                MycelarRuntime.builder().graph(MycelarGraph.builder().node(a).node(b).build()).build();
        assertThatThrownBy(
                        () ->
                                new CapabilityToolRegistry(
                                        dupRuntime, List.of(), List.of(), List.of(), Map.of()))
                .isInstanceOf(MycelarConfigurationException.class);
    }
}
