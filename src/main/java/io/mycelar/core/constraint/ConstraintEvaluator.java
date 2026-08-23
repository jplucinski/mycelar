package io.mycelar.core.constraint;

import io.mycelar.core.routing.RoutingContext;

public interface ConstraintEvaluator {

    boolean supports(Constraint constraint);

    ConstraintDecision evaluate(Constraint constraint, RoutingContext context);
}
