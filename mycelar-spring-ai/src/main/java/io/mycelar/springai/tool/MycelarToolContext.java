package io.mycelar.springai.tool;

import io.mycelar.core.capability.CapabilityId;
import io.mycelar.springai.exception.MycelarCapabilityDeniedException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.chat.model.ToolContext;

public record MycelarToolContext(String path, String sessionId, Set<CapabilityId> allowed) {

    public MycelarToolContext {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(allowed, "allowed");
        allowed = Set.copyOf(allowed);
    }

    public static MycelarToolContext from(ToolContext toolContext) {
        if (toolContext == null) {
            throw new MycelarCapabilityDeniedException("ToolContext is required");
        }
        Map<String, Object> map = toolContext.getContext();
        if (map == null) {
            throw new MycelarCapabilityDeniedException("ToolContext is required");
        }
        Object path = map.get(MycelarToolContextKeys.PATH);
        Object sessionId = map.get(MycelarToolContextKeys.SESSION_ID);
        Object capabilities = map.get(MycelarToolContextKeys.CAPABILITIES);
        if (!(path instanceof String pathValue) || pathValue.isBlank()) {
            throw new MycelarCapabilityDeniedException("Malformed mycelar ToolContext");
        }
        if (!(sessionId instanceof String sessionValue) || sessionValue.isBlank()) {
            throw new MycelarCapabilityDeniedException("Malformed mycelar ToolContext");
        }
        if (!(capabilities instanceof Collection<?> raw)) {
            throw new MycelarCapabilityDeniedException("Malformed mycelar ToolContext");
        }
        Set<CapabilityId> allowed = new HashSet<>();
        for (Object element : raw) {
            if (!(element instanceof CapabilityId id)) {
                throw new MycelarCapabilityDeniedException("Malformed mycelar ToolContext");
            }
            allowed.add(id);
        }
        return new MycelarToolContext(pathValue, sessionValue, allowed);
    }
}
