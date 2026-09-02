package io.mycelar.springai.request;

import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClientRequest;

public final class MycelarSessionResolver {

    public static final String SESSION_ID_KEY = "mycelar.session.id";

    private static final String CHAT_MEMORY_CONVERSATION_ID = conversationIdKey();

    public String resolve(ChatClientRequest request) {
        Objects.requireNonNull(request, "request");
        Object mycelar = request.context().get(SESSION_ID_KEY);
        if (mycelar instanceof String value && !value.isBlank()) {
            return value;
        }
        if (CHAT_MEMORY_CONVERSATION_ID != null) {
            Object memory = request.context().get(CHAT_MEMORY_CONVERSATION_ID);
            if (memory instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return UUID.randomUUID().toString();
    }

    private static String conversationIdKey() {
        try {
            Class<?> chatMemory = Class.forName("org.springframework.ai.chat.memory.ChatMemory");
            Object value = chatMemory.getField("CONVERSATION_ID").get(null);
            return value instanceof String key ? key : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
