package com.railway.payment.kafka;

import com.railway.common.event.EventEnvelope;
import com.railway.common.event.PaymentEvent;
import com.railway.payment.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.payment-events}")
    private String paymentEventsTopic;

    public void publishPaymentSuccess(Payment payment) {
        publish(payment, "PAYMENT_SUCCESS");
    }

    public void publishPaymentFailed(Payment payment) {
        publish(payment, "PAYMENT_FAILED");
    }

    public void publishPaymentRefunded(Payment payment) {
        publish(payment, "PAYMENT_REFUNDED");
    }

    private void publish(Payment payment, String eventType) {
        PaymentEvent payload = new PaymentEvent(
                payment.getId(),
                payment.getBookingId(),
                payment.getPnr(),
                payment.getAmount(),
                payment.getPaymentStatus().name(),
                payment.getGatewayTransactionId(),
                payment.getFailureReason()
        );

        EventEnvelope<PaymentEvent> envelope = EventEnvelope.<PaymentEvent>builder()
                .eventType(eventType)
                .aggregateId(String.valueOf(payment.getBookingId()))
                .aggregateType("Payment")
                .source("railway-payment")
                .payload(payload)
                .build();

        kafkaTemplate.send(paymentEventsTopic, String.valueOf(payment.getBookingId()), envelope);
        log.info("Published {} for booking {}, payment {}", eventType, payment.getBookingId(), payment.getId());
    }
}
