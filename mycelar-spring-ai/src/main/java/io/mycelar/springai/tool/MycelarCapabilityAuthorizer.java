package io.mycelar.springai.tool;

import io.mycelar.core.capability.CapabilityId;
import io.mycelar.springai.exception.MycelarCapabilityDeniedException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.tool.ToolCallback;

public final class MycelarCapabilityAuthorizer {

    public Map<String, Object> snapshot(String path, String sessionId, Set<CapabilityId> allowed) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(allowed, "allowed");
        Map<String, Object> map = new HashMap<>();
        map.put(MycelarToolContextKeys.PATH, path);
        map.put(MycelarToolContextKeys.SESSION_ID, sessionId);
        map.put(MycelarToolContextKeys.CAPABILITIES, Set.copyOf(allowed));
        return map;
    }

    public void authorize(MycelarToolContext context, CapabilityId capabilityId) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(capabilityId, "capabilityId");
        if (!context.allowed().contains(capabilityId)) {
            throw new MycelarCapabilityDeniedException(
                    "Capability not in SNAPSHOT allowed set: " + capabilityId.value());
        }
    }

    public ToolCallback guard(ToolCallback delegate, CapabilityId capabilityId) {
        return new MycelarGuardedToolCallback(delegate, capabilityId, this);
    }
}
