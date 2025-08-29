package dev.demo.jobboard.orchestrator.service;

import dev.demo.jobboard.orchestrator.workflow.PaymentWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    @Autowired
    private WorkflowClient workflowClient;

    public String startPaymentFlow(String paymentId) {
        PaymentWorkflow workflow = workflowClient.newWorkflowStub(
                PaymentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue("PAYMENT_TASK_QUEUE")
                        .build()
        );
        return workflow.executePayment(paymentId);
    }
}