package dev.demo.jobboard.orchestrator.activity.impl;

import dev.demo.jobboard.orchestrator.activity.PaymentActivities;
import org.springframework.stereotype.Component;

@Component
public class PaymentActivitiesImpl implements PaymentActivities {
    @Override
    public String processPayment(String paymentDetails) {
        // Your payment processing logic
        return "Processed: " + paymentDetails;
    }
}