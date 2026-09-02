package io.mycelar.springai.capability;

import io.mycelar.core.capability.CapabilityDescriptor;
import io.mycelar.core.capability.CapabilityId;
import io.mycelar.core.graph.GraphNode;
import io.mycelar.core.routing.MycelarRuntime;
import io.mycelar.springai.exception.DuplicateMycelarToolNameException;
import io.mycelar.springai.exception.MycelarConfigurationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

public final class CapabilityToolRegistry {

    private final MycelarRuntime runtime;
    private final Map<String, ToolCallback> inventoryByName;
    private final Map<String, CapabilityId> toolNameToCapability;
    private final Map<CapabilityId, List<CapabilityToolBinding>> bindingsByCapability;

    public CapabilityToolRegistry(
            MycelarRuntime runtime,
            List<CapabilityToolBindings> programmatic,
            List<ToolCallback> callbacks,
            List<ToolCallbackProvider> providers,
            Map<String, String> yamlMappings) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.inventoryByName = flattenInventory(callbacks, providers);
        this.toolNameToCapability = new LinkedHashMap<>();
        this.bindingsByCapability = new LinkedHashMap<>();
        applyProgrammatic(programmatic == null ? List.of() : programmatic);
        applyYaml(yamlMappings == null ? Map.of() : yamlMappings);
        applyConvention();
    }

    public Collection<CapabilityToolBinding> resolve(Set<CapabilityId> allowed) {
        return resolve(allowed, false);
    }

    public Collection<CapabilityToolBinding> resolve(
            Set<CapabilityId> allowed, boolean strictToolBindings) {
        Objects.requireNonNull(allowed, "allowed");
        List<CapabilityToolBinding> out = new ArrayList<>();
        for (CapabilityId id : allowed) {
            List<CapabilityToolBinding> found = bindingsByCapability.getOrDefault(id, List.of());
            if (found.isEmpty() && strictToolBindings) {
                throw new MycelarConfigurationException(
                        "strict-tool-bindings: no tools for capability " + id.value());
            }
            out.addAll(found);
        }
        return List.copyOf(out);
    }

    private static Map<String, ToolCallback> flattenInventory(
            List<ToolCallback> callbacks, List<ToolCallbackProvider> providers) {
        Map<String, ToolCallback> byName = new LinkedHashMap<>();
        List<ToolCallback> all = new ArrayList<>();
        if (callbacks != null) {
            all.addAll(callbacks);
        }
        if (providers != null) {
            for (ToolCallbackProvider provider : providers) {
                ToolCallback[] fromProvider = provider.getToolCallbacks();
                if (fromProvider != null) {
                    all.addAll(List.of(fromProvider));
                }
            }
        }
        for (ToolCallback callback : all) {
            String name = callback.getToolDefinition().name();
            if (byName.containsKey(name)) {
                throw new DuplicateMycelarToolNameException("Duplicate tool name in inventory: " + name);
            }
            byName.put(name, callback);
        }
        return Map.copyOf(byName);
    }

    private void applyProgrammatic(List<CapabilityToolBindings> programmatic) {
        for (CapabilityToolBindings group : programmatic) {
            for (CapabilityToolBinding binding : group.bindings()) {
                String name = binding.toolCallback().getToolDefinition().name();
                CapabilityId existing = toolNameToCapability.get(name);
                if (existing != null && !existing.equals(binding.capabilityId())) {
                    throw new DuplicateMycelarToolNameException(
                            "Tool "
                                    + name
                                    + " bound to both "
                                    + existing.value()
                                    + " and "
                                    + binding.capabilityId().value());
                }
                if (existing != null) {
                    continue;
                }
                putBinding(name, binding);
            }
        }
    }

    private void applyYaml(Map<String, String> yamlMappings) {
        for (Map.Entry<String, String> entry : yamlMappings.entrySet()) {
            String toolName = entry.getKey();
            String capabilityValue = entry.getValue();
            if (capabilityValue == null || capabilityValue.isBlank()) {
                throw new MycelarConfigurationException(
                        "YAML mapping for " + toolName + " has blank capability id");
            }
            if (!inventoryByName.containsKey(toolName)) {
                throw new MycelarConfigurationException("YAML mapping unknown tool name: " + toolName);
            }
            if (toolNameToCapability.containsKey(toolName)) {
                continue;
            }
            CapabilityId capabilityId;
            try {
                capabilityId = new CapabilityId(capabilityValue.trim());
            } catch (IllegalArgumentException ex) {
                throw new MycelarConfigurationException(
                        "YAML mapping for " + toolName + " has invalid capability id", ex);
            }
            putBinding(toolName, new CapabilityToolBinding(capabilityId, inventoryByName.get(toolName)));
        }
    }

    private void applyConvention() {
        Map<String, CapabilityId> operationIdOwners = new LinkedHashMap<>();
        for (GraphNode node : runtime.graph().nodes()) {
            if (node.capability().isEmpty()) {
                continue;
            }
            CapabilityDescriptor descriptor = node.capability().get();
            CapabilityId previous = operationIdOwners.put(descriptor.operationId(), descriptor.id());
            if (previous != null && !previous.equals(descriptor.id())) {
                throw new MycelarConfigurationException(
                        "Duplicate operationId "
                                + descriptor.operationId()
                                + " on "
                                + previous.value()
                                + " and "
                                + descriptor.id().value());
            }
        }
        for (GraphNode node : runtime.graph().nodes()) {
            if (node.capability().isEmpty()) {
                continue;
            }
            CapabilityDescriptor lookedUp =
                    runtime.graph().capability(node.capability().get().id()).orElse(null);
            if (lookedUp == null) {
                continue;
            }
            String operationId = lookedUp.operationId();
            ToolCallback callback = inventoryByName.get(operationId);
            if (callback == null) {
                continue;
            }
            if (toolNameToCapability.containsKey(operationId)) {
                continue;
            }
            putBinding(operationId, new CapabilityToolBinding(lookedUp.id(), callback));
        }
    }

    private void putBinding(String toolName, CapabilityToolBinding binding) {
        toolNameToCapability.put(toolName, binding.capabilityId());
        bindingsByCapability
                .computeIfAbsent(binding.capabilityId(), k -> new ArrayList<>())
                .add(binding);
    }
}
