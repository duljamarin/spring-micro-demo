package com.demo.payment.controller;

import com.demo.payment.dto.ChargeRequest;
import com.demo.payment.dto.PaymentResponse;
import com.demo.payment.model.Payment;
import com.demo.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/charge")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse charge(@Valid @RequestBody ChargeRequest request) {
        Payment payment = paymentService.charge(request);
        return PaymentResponse.from(payment);
    }

    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@PathVariable Long id) {
        return PaymentResponse.from(paymentService.refund(id));
    }
}
