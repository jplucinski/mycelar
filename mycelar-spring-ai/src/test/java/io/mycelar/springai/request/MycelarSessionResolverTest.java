package io.mycelar.springai.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

class MycelarSessionResolverTest {

    private final MycelarSessionResolver resolver = new MycelarSessionResolver();

    private static ChatClientRequest request(Map<String, Object> context) {
        return ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage("hi")))
                .context(context)
                .build();
    }

    @Test
    void prefersMycelarSessionId() {
        assertThat(
                        resolver.resolve(
                                request(
                                        Map.of(
                                                "mycelar.session.id",
                                                "mycelar-1",
                                                ChatMemory.CONVERSATION_ID,
                                                "memory-1"))))
                .isEqualTo("mycelar-1");
    }

    @Test
    void fallsBackToChatMemoryConversationId() {
        assertThat(resolver.resolve(request(Map.of(ChatMemory.CONVERSATION_ID, "memory-1"))))
                .isEqualTo("memory-1");
    }

    @Test
    void generatesWhenMissing() {
        String first = resolver.resolve(request(Map.of()));
        String second = resolver.resolve(request(Map.of()));
        assertThat(first).isNotBlank();
        assertThat(second).isNotBlank();
        assertThat(first).isNotEqualTo(second);
    }
}
