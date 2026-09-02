package io.mycelar.springai.tool;

import io.mycelar.core.capability.CapabilityId;
import io.mycelar.springai.exception.MycelarCapabilityDeniedException;
import java.util.Objects;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

final class MycelarGuardedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final CapabilityId capabilityId;
    private final MycelarCapabilityAuthorizer authorizer;

    MycelarGuardedToolCallback(
            ToolCallback delegate, CapabilityId capabilityId, MycelarCapabilityAuthorizer authorizer) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.capabilityId = Objects.requireNonNull(capabilityId, "capabilityId");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        throw new MycelarCapabilityDeniedException("ToolContext is required");
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        MycelarToolContext context = MycelarToolContext.from(toolContext);
        authorizer.authorize(context, capabilityId);
        return delegate.call(toolInput, toolContext);
    }
}
