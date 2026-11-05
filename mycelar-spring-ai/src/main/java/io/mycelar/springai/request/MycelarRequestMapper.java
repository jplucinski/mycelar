package io.mycelar.springai.request;

import io.mycelar.core.routing.RouteRequest;
import io.mycelar.core.routing.RoutingContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClientRequest;

public final class MycelarRequestMapper {

    public static final String ROUTING_PREFIX = "mycelar.routing.";

    public RouteRequest map(ChatClientRequest request, int topK) {
        Objects.requireNonNull(request, "request");
        return map(request, request.prompt().getUserMessage().getText(), topK);
    }

    public RouteRequest map(ChatClientRequest request, String intent, int topK) {
        Objects.requireNonNull(request, "request");
        Map<String, Object> routing = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : request.context().entrySet()) {
            String key = entry.getKey();
            if (key != null && key.startsWith(ROUTING_PREFIX)) {
                routing.put(key.substring(ROUTING_PREFIX.length()), entry.getValue());
            }
        }
        return RouteRequest.builder()
                .intent(intent)
                .context(RoutingContext.of(routing))
                .topK(topK)
                .build();
    }
}
