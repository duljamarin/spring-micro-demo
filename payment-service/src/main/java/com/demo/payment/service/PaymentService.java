package com.demo.payment.service;

import com.demo.payment.PaymentProperties;
import com.demo.payment.dto.ChargeRequest;
import com.demo.payment.exception.PaymentConflictException;
import com.demo.payment.exception.PaymentDeclinedException;
import com.demo.payment.exception.PaymentNotFoundException;
import com.demo.payment.model.Payment;
import com.demo.payment.model.PaymentStatus;
import com.demo.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentProperties properties;

    public PaymentService(PaymentRepository paymentRepository, PaymentProperties properties) {
        this.paymentRepository = paymentRepository;
        this.properties = properties;
    }

    /**
     * Idempotent in orderId: charging the same order twice returns the first
     * payment instead of creating a second one. This matters because
     * order-orchestrator retries this call, and a read timeout looks
     * identical to a request that never arrived - without this, a charge that
     * actually succeeded but answered too slowly would be charged again.
     *
     * <p>Deliberately not @Transactional: the DECLINED row below is written
     * and then an exception is thrown, and a surrounding transaction would
     * roll that row back - losing the very record that makes the decline
     * idempotent on retry.
     *
     * <p>No real payment processor is wired up here - amounts above the
     * configured threshold are simulated as declined so the saga's
     * compensation path (release stock, mark order FAILED) is exercisable
     * without needing a real card network to say no.
     */
    public Payment charge(ChargeRequest request) {
        Payment existing = paymentRepository.findByOrderId(request.orderId()).orElse(null);
        if (existing != null) {
            return replay(existing, request);
        }

        if (request.amount().compareTo(properties.getDeclineAboveAmount()) > 0) {
            save(new Payment(request.orderId(), request.userId(), request.amount(), PaymentStatus.DECLINED), request);
            throw new PaymentDeclinedException(request.amount());
        }
        return save(new Payment(request.orderId(), request.userId(), request.amount(), PaymentStatus.SUCCEEDED), request);
    }

    /**
     * Two concurrent retries can both find nothing and both try to insert;
     * the unique constraint on orderId lets exactly one win, and the loser
     * resolves to the winner's row rather than failing the caller.
     */
    private Payment save(Payment payment, ChargeRequest request) {
        try {
            return paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException ex) {
            Payment winner = paymentRepository.findByOrderId(request.orderId())
                    .orElseThrow(() -> ex);
            log.info("Concurrent charge for order {} lost the race; replaying payment {}",
                    request.orderId(), winner.getId());
            return replay(winner, request);
        }
    }

    /**
     * Resolves a repeat charge against the payment the order already has.
     * The amount is re-checked because an order's total changing underneath a
     * charge means the two calls aren't the same charge at all, and silently
     * returning the old one would confirm an order that was never paid for.
     */
    private Payment replay(Payment existing, ChargeRequest request) {
        if (existing.getAmount().compareTo(request.amount()) != 0) {
            throw new PaymentConflictException("Order " + request.orderId() + " already has a payment for "
                    + existing.getAmount() + ", cannot charge " + request.amount());
        }
        return switch (existing.getStatus()) {
            case SUCCEEDED -> existing;
            case DECLINED -> throw new PaymentDeclinedException(existing.getAmount());
            case REFUNDED -> throw new PaymentConflictException(
                    "Payment for order " + request.orderId() + " was already refunded and will not be re-charged");
        };
    }

    public Payment refund(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        payment.setStatus(PaymentStatus.REFUNDED);
        return paymentRepository.save(payment);
    }
}
