package io.mycelar.core.context;

import java.util.List;

public interface ContextExpander {
    List<ContextCapsule> expand(ExpansionRequest request);
}
