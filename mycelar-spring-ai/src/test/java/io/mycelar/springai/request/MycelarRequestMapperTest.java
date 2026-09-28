package io.mycelar.springai.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

class MycelarRequestMapperTest {

    @Test
    void mapsLatestUserTextAndStripsRoutingPrefix() {
        ChatClientRequest request =
                ChatClientRequest.builder()
                        .prompt(new Prompt(new UserMessage("Update this lead")))
                        .context(
                                Map.of(
                                        "mycelar.routing.confirmed",
                                        true,
                                        "chat_memory_conversation_id",
                                        "ignored-by-mapper",
                                        "other",
                                        "nope"))
                        .build();

        var mapped = new MycelarRequestMapper().map(request, 1);

        assertThat(mapped.intent()).isEqualTo("Update this lead");
        assertThat(mapped.topK()).isEqualTo(1);
        assertThat(mapped.context().get("confirmed")).contains(true);
        assertThat(mapped.context().get("chat_memory_conversation_id")).isEmpty();
        assertThat(mapped.context().get("other")).isEmpty();
    }
}
