package dev.demo.jobboard.orchestrator.workflow.impl;

import dev.demo.jobboard.orchestrator.activity.PaymentActivities;
import dev.demo.jobboard.orchestrator.workflow.PaymentWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class PaymentWorkflowImpl implements PaymentWorkflow {
    private final PaymentActivities activities =
            Workflow.newActivityStub(
                    PaymentActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofSeconds(30))
                            .build()
            );

    @Override
    public String executePayment(String paymentId) {
        return activities.processPayment("PaymentID: " + paymentId);
    }
}