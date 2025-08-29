package dev.demo.jobboard.orchestrator.controller;

import dev.demo.jobboard.orchestrator.service.PaymentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // Option A: path variable
    @PostMapping("/{paymentId}")
    public String startByPath(@PathVariable String paymentId) {
        return paymentService.startPaymentFlow(paymentId);
    }

    // Option B: query param
    @PostMapping
    public String startByQuery(@RequestParam String paymentId) {
        return paymentService.startPaymentFlow(paymentId);
    }
}