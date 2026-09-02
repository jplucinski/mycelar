package io.mycelar.springai.autoconfigure;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

@ConfigurationProperties(prefix = "mycelar.spring-ai")
public class MycelarSpringAiProperties {

    private boolean enabled = true;
    private int advisorOrder = Ordered.HIGHEST_PRECEDENCE + 250;
    private int topK = 1;
    private boolean strictToolBindings = false;
    private boolean includeCapabilitiesInContext = true;
    private boolean includeBlockedInContext = true;
    private final Tools tools = new Tools();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getAdvisorOrder() {
        return advisorOrder;
    }

    public void setAdvisorOrder(int advisorOrder) {
        this.advisorOrder = advisorOrder;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public boolean isStrictToolBindings() {
        return strictToolBindings;
    }

    public void setStrictToolBindings(boolean strictToolBindings) {
        this.strictToolBindings = strictToolBindings;
    }

    public boolean isIncludeCapabilitiesInContext() {
        return includeCapabilitiesInContext;
    }

    public void setIncludeCapabilitiesInContext(boolean includeCapabilitiesInContext) {
        this.includeCapabilitiesInContext = includeCapabilitiesInContext;
    }

    public boolean isIncludeBlockedInContext() {
        return includeBlockedInContext;
    }

    public void setIncludeBlockedInContext(boolean includeBlockedInContext) {
        this.includeBlockedInContext = includeBlockedInContext;
    }

    public Tools getTools() {
        return tools;
    }

    public static class Tools {
        private Map<String, String> mappings = new LinkedHashMap<>();

        public Map<String, String> getMappings() {
            return mappings;
        }

        public void setMappings(Map<String, String> mappings) {
            this.mappings = mappings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(mappings);
        }
    }
}
