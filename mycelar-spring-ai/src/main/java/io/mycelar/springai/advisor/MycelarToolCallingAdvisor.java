package io.mycelar.springai.advisor;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityChecker;

public final class MycelarToolCallingAdvisor extends ToolCallingAdvisor {

    private final MycelarAdvisor mycelarAdvisor;

    private MycelarToolCallingAdvisor(
            ToolCallingManager toolCallingManager,
            ToolExecutionEligibilityChecker toolExecutionEligibilityChecker,
            int advisorOrder,
            boolean conversationHistoryEnabled,
            MycelarAdvisor mycelarAdvisor) {
        super(toolCallingManager, toolExecutionEligibilityChecker, advisorOrder, conversationHistoryEnabled);
        this.mycelarAdvisor = Objects.requireNonNull(mycelarAdvisor, "mycelarAdvisor");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected ChatClientRequest doBeforeCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        return reroute(chatClientRequest);
    }

    @Override
    protected ChatClientRequest doBeforeStream(
            ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        return reroute(chatClientRequest);
    }

    private ChatClientRequest reroute(ChatClientRequest request) {
        if (!hasToolResponse(request.prompt())) {
            return request;
        }
        String intent = originalUserText(request.prompt()) + "\n\n" + lastToolResponseText(request.prompt());
        return mycelarAdvisor.route(request, intent);
    }

    private static boolean hasToolResponse(Prompt prompt) {
        for (Message message : prompt.getInstructions()) {
            if (message instanceof ToolResponseMessage) {
                return true;
            }
        }
        return false;
    }

    private static String originalUserText(Prompt prompt) {
        for (Message message : prompt.getInstructions()) {
            if (message instanceof UserMessage user) {
                String text = user.getText();
                return text == null ? "" : text;
            }
        }
        return "";
    }

    private static String lastToolResponseText(Prompt prompt) {
        String text = "";
        for (Message message : prompt.getInstructions()) {
            if (!(message instanceof ToolResponseMessage tool) || tool.getResponses() == null) {
                continue;
            }
            if (tool.getResponses().isEmpty()) {
                continue;
            }
            String data = tool.getResponses().get(tool.getResponses().size() - 1).responseData();
            text = data == null ? "" : data;
        }
        return text;
    }

    public static final class Builder extends ToolCallingAdvisor.Builder<Builder> {

        private MycelarAdvisor mycelarAdvisor;

        public Builder mycelarAdvisor(MycelarAdvisor advisor) {
            this.mycelarAdvisor = advisor;
            return self();
        }

        @Override
        protected Builder newCopy() {
            return new Builder();
        }

        @Override
        public Builder copy() {
            Builder copy = (Builder) super.copy();
            copy.mycelarAdvisor = this.mycelarAdvisor;
            return copy;
        }

        @Override
        public MycelarToolCallingAdvisor build() {
            if (mycelarAdvisor == null) {
                throw new IllegalStateException("mycelarAdvisor is required");
            }
            return new MycelarToolCallingAdvisor(
                    getToolCallingManager(),
                    getToolExecutionEligibilityChecker(),
                    getAdvisorOrder(),
                    isConversationHistoryEnabled(),
                    mycelarAdvisor);
        }
    }
}
