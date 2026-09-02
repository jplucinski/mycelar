package io.mycelar.springai.fixture;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class StubTools {

    private StubTools() {}

    public static ToolCallback callback(String name) {
        return callback(name, new AtomicBoolean());
    }

    public static ToolCallback callback(String name, AtomicBoolean called) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description(name)
                        .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                called.set(true);
                return name + "-ok";
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return call(toolInput);
            }
        };
    }
}
