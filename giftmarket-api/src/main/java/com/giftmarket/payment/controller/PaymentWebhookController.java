package com.giftmarket.payment.controller;

import com.giftmarket.payment.infrastructure.toss.dto.TossPaymentWebhookRequest;
import com.giftmarket.payment.service.TossPaymentWebhookService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments/webhooks")
public class PaymentWebhookController {

    private static final String TRANSMISSION_ID_HEADER =
            "tosspayments-webhook-transmission-id";

    private final TossPaymentWebhookService webhookService;

    @PostMapping("/toss")
    public ResponseEntity<Void> receiveTossWebhook(
            @RequestHeader(TRANSMISSION_ID_HEADER)
            @NotBlank
            @Size(max = 200)
            String transmissionId,
            @Valid @RequestBody TossPaymentWebhookRequest request
    ) {
        webhookService.process(transmissionId, request);
        return ResponseEntity.ok().build();
    }
}
