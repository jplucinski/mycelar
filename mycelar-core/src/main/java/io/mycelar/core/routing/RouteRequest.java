package io.mycelar.core.routing;

import java.util.Objects;

public record RouteRequest(String intent, RoutingContext context, int topK) {

    public RouteRequest {
        Objects.requireNonNull(intent, "intent");
        if (intent.isBlank()) {
            throw new IllegalArgumentException("intent must not be blank");
        }
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be >= 1");
        }
        context = context == null ? RoutingContext.empty() : context;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String intent;
        private RoutingContext context = RoutingContext.empty();
        private int topK;

        public Builder intent(String intent) {
            this.intent = intent;
            return this;
        }

        public Builder context(RoutingContext context) {
            this.context = context;
            return this;
        }

        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public RouteRequest build() {
            return new RouteRequest(intent, context, topK);
        }
    }
}
