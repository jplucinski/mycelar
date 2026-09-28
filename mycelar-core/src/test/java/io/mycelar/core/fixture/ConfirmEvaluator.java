package io.mycelar.core.fixture;

import io.mycelar.core.constraint.Constraint;
import io.mycelar.core.constraint.ConstraintDecision;
import io.mycelar.core.constraint.ConstraintEvaluator;
import io.mycelar.core.routing.RoutingContext;

final class ConfirmEvaluator implements ConstraintEvaluator {

    @Override
    public boolean supports(Constraint constraint) {
        return "confirm-before-send".equals(constraint.type());
    }

    @Override
    public ConstraintDecision evaluate(Constraint constraint, RoutingContext context) {
        return context.get("confirmed").filter(Boolean.TRUE::equals).isPresent()
                ? ConstraintDecision.ALLOW
                : ConstraintDecision.REQUIRE_CONFIRMATION;
    }
}
