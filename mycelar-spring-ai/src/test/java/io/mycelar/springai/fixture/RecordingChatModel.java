package io.mycelar.springai.fixture;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

public final class RecordingChatModel implements ChatModel {

    public final List<Prompt> prompts = new ArrayList<>();
    private final List<ChatResponse> responses;

    public RecordingChatModel(List<ChatResponse> responses) {
        this.responses = new ArrayList<>(responses);
    }

    public static RecordingChatModel text(String text) {
        return new RecordingChatModel(
                List.of(new ChatResponse(List.of(new Generation(new AssistantMessage(text))))));
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        if (responses.isEmpty()) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(""))));
        }
        return responses.remove(0);
    }
}
