package dev.demo.jobboard.orchestrator.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface PaymentWorkflow {
    @WorkflowMethod
    String executePayment(String paymentId);
}