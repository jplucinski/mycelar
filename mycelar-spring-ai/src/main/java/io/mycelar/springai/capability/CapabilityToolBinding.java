package io.mycelar.springai.capability;

import io.mycelar.core.capability.CapabilityId;
import java.util.Objects;
import org.springframework.ai.tool.ToolCallback;

public record CapabilityToolBinding(CapabilityId capabilityId, ToolCallback toolCallback) {
    public CapabilityToolBinding {
        Objects.requireNonNull(capabilityId, "capabilityId");
        Objects.requireNonNull(toolCallback, "toolCallback");
    }
}
