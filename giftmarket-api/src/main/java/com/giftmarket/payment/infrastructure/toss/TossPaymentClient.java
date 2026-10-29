package com.giftmarket.payment.infrastructure.toss;

import com.giftmarket.payment.gateway.GatewayConfirmCommand;
import com.giftmarket.payment.gateway.PaymentGatewayDeclinedException;
import com.giftmarket.payment.gateway.PaymentGatewayUncertainException;
import com.giftmarket.payment.infrastructure.toss.dto.TossConfirmRequest;
import com.giftmarket.payment.infrastructure.toss.dto.TossCancelRequest;
import com.giftmarket.payment.gateway.GatewayCancelCommand;
import com.giftmarket.payment.infrastructure.toss.dto.TossPaymentResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TossPaymentClient {

    private static final Set<String> CONFIRM_DECLINES = Set.of(
            "INVALID_STOPPED_CARD", "INVALID_REJECT_CARD", "INVALID_CARD_LOST_OR_STOLEN",
            "EXCEED_MAX_DAILY_PAYMENT_COUNT", "EXCEED_MAX_PAYMENT_AMOUNT",
            "NOT_SUPPORTED_INSTALLMENT_PLAN_CARD_OR_MERCHANT", "INVALID_CARD_INSTALLMENT_PLAN",
            "NOT_SUPPORTED_MONTHLY_INSTALLMENT_PLAN", "INVALID_REQUEST", "INVALID_REQUIRED_PARAM",
            "EXCEED_MAX_CARD_INSTALLMENT_PLAN", "NOT_ALLOWED_POINT_USE", "BELOW_MINIMUM_AMOUNT",
            "INVALID_CARD_EXPIRATION", "INVALID_CARD_NUMBER", "RESTRICTED_TRANSFER_ACCOUNT",
            "EXCEED_MAX_ONE_DAY_WITHDRAW_AMOUNT", "EXCEED_MAX_ONE_TIME_WITHDRAW_AMOUNT",
            "EXCEED_MAX_AMOUNT", "INVALID_ACCOUNT_INFO_RE_REGISTER", "NOT_AVAILABLE_PAYMENT",
            "EXCEED_MAX_MONTHLY_PAYMENT_AMOUNT"
    );
    private static final Set<String> CONFIRM_403_DECLINES = Set.of(
            "REJECT_CARD_COMPANY", "REJECT_ACCOUNT_PAYMENT", "REJECT_CARD_PAYMENT",
            "REJECT_TOSSPAY_INVALID_ACCOUNT", "EXCEED_MAX_AUTH_COUNT", "EXCEED_MAX_ONE_DAY_AMOUNT",
            "NOT_AVAILABLE_BANK", "INVALID_PASSWORD", "FDS_ERROR"
    );
    private static final Set<String> CANCEL_400_DECLINES = Set.of(
            "INVALID_REFUND_ACCOUNT_INFO", "INVALID_REFUND_ACCOUNT_NUMBER", "INVALID_BANK",
            "EXCEED_CANCEL_AMOUNT_DISCOUNT_AMOUNT", "REFUND_REJECTED",
            "FORBIDDEN_BANK_REFUND_REQUEST", "FAILED_CLOSED_ACCOUNT_REFUND", "INVALID_REQUEST"
    );
    private static final Set<String> CANCEL_403_DECLINES = Set.of(
            "NOT_CANCELABLE_AMOUNT", "NOT_PARTIAL_CANCELABLE_PAYMENT", "EXCEED_MAX_REFUND_DUE",
            "NOT_ALLOWED_PARTIAL_REFUND_WAITING_DEPOSIT", "NOT_ALLOWED_PARTIAL_REFUND",
            "NOT_CANCELABLE_PAYMENT_FOR_DORMANT_USER", "EXCEED_CANCEL_LIMIT"
    );

    static boolean isConfirmDecline(int status, String code) {
        return (status == 400 && CONFIRM_DECLINES.contains(code))
                || (status == 403 && CONFIRM_403_DECLINES.contains(code));
    }

    static boolean isCancelDecline(int status, String code) {
        return (status == 400 && CANCEL_400_DECLINES.contains(code))
                || (status == 403 && CANCEL_403_DECLINES.contains(code));
    }

    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile(
            "\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""
    );
    private static final Pattern ERROR_MESSAGE_PATTERN = Pattern.compile(
            "\\\"message\\\"\\s*:\\s*\\\"([^\\\"]*)\\\""
    );
    private static final Pattern SENSITIVE_TOKEN_PATTERN = Pattern.compile(
            "(?i)(test|live)_[a-z0-9_\\-]+|[A-Za-z0-9_\\-=]{20,}"
    );

    private final TossPaymentProperties properties;
    private final RestClient restClient;

    public TossPaymentClient(
            TossPaymentProperties properties
    ) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(
                properties.getConnectTimeoutMillis()
        );
        requestFactory.setReadTimeout(
                properties.getReadTimeoutMillis()
        );

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();

        String secretKey = properties.getSecretKey();
        log.info(
                "Toss HTTP client configured. baseUrl={}, secretConfigured={}, secretType={}, secretLength={}, hasSurroundingWhitespace={}, connectTimeoutMillis={}, readTimeoutMillis={}",
                properties.getBaseUrl(),
                StringUtils.hasText(secretKey),
                StringUtils.hasText(secretKey)
                        ? classifySecretKey()
                        : "NOT_CONFIGURED",
                secretKey == null ? 0 : secretKey.length(),
                secretKey != null && !secretKey.equals(secretKey.trim()),
                properties.getConnectTimeoutMillis(),
                properties.getReadTimeoutMillis()
        );
    }

    public TossPaymentResponse confirm(
            GatewayConfirmCommand command
    ) {
        log.info(
                "Toss request started. method=POST, target={}/v1/payments/confirm, paymentKeyLength={}",
                properties.getBaseUrl(),
                command.providerPaymentKey().length()
        );
        try {
            ResponseEntity<TossPaymentResponse> responseEntity = restClient.post()
                    .uri("/v1/payments/confirm")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            createAuthorizationHeader()
                    )
                    .header(
                            "Idempotency-Key",
                            command.idempotencyKey()
                    )
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                            new TossConfirmRequest(
                                    command.providerPaymentKey(),
                                    command.merchantPaymentId(),
                                    command.amount()
                            )
                    )
                    .retrieve()
                    .toEntity(TossPaymentResponse.class);
            TossPaymentResponse response = responseEntity.getBody();

            log.info(
                    "Toss confirm response received. httpStatus={}, providerStatus={}",
                    responseEntity.getStatusCode().value(),
                    response == null ? null : response.status()
            );

            if (response == null) {
                throw new PaymentGatewayUncertainException(
                        "결제 승인 결과를 확인할 수 없습니다.",
                        null
                );
            }

            return response;
        } catch (RestClientResponseException exception) {
            TossErrorDetails error = extractErrorDetails(exception);
            log.warn(
                    "Toss confirm response failed. responseReceived=true, exceptionType={}, httpStatus={}, contentType={}, bodyState={}, bodyLength={}, errorCode={}, message={}",
                    exception.getClass().getSimpleName(),
                    exception.getStatusCode().value(),
                    error.contentType(),
                    error.bodyState(),
                    error.bodyLength(),
                    error.code(),
                    error.message()
            );
            if (isConfirmDecline(exception.getStatusCode().value(), error.code())) {
                throw new PaymentGatewayDeclinedException(
                        error.code(),
                        "결제 승인이 거절되었습니다. 결제 정보를 다시 확인해주세요."
                );
            }

            throw new PaymentGatewayUncertainException(
                    "결제 승인 결과를 확인 중입니다.",
                    exception
            );
        } catch (ResourceAccessException exception) {
            log.warn(
                    "Toss confirm result is uncertain due to network error. exceptionType={}",
                    exception.getClass().getSimpleName()
            );
            throw new PaymentGatewayUncertainException(
                    "결제 승인 결과를 확인 중입니다.",
                    exception
            );
        } catch (PaymentGatewayDeclinedException
                 | PaymentGatewayUncertainException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn(
                    "Toss confirm result is uncertain. exceptionType={}",
                    exception.getClass().getSimpleName()
            );
            throw new PaymentGatewayUncertainException(
                    "결제 승인 결과를 확인 중입니다.",
                    exception
            );
        }
    }

    public Optional<TossPaymentResponse> getPayment(
            String providerPaymentKey
    ) {
        log.info(
                "Toss request started. method=GET, target={}/v1/payments/{{paymentKey}}, paymentKeyLength={}",
                properties.getBaseUrl(),
                providerPaymentKey.length()
        );
        try {
            ResponseEntity<TossPaymentResponse> responseEntity = restClient.get()
                    .uri(
                            "/v1/payments/{paymentKey}",
                            providerPaymentKey
                    )
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            createAuthorizationHeader()
                    )
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toEntity(TossPaymentResponse.class);
            TossPaymentResponse response = responseEntity.getBody();

            log.info(
                    "Toss payment query response received. httpStatus={}, providerStatus={}",
                    responseEntity.getStatusCode().value(),
                    response == null ? null : response.status()
            );

            return Optional.ofNullable(response);
        } catch (RestClientResponseException exception) {
            TossErrorDetails error = extractErrorDetails(exception);
            log.warn(
                    "Toss payment query failed. responseReceived=true, exceptionType={}, httpStatus={}, contentType={}, bodyState={}, bodyLength={}, errorCode={}, message={}",
                    exception.getClass().getSimpleName(),
                    exception.getStatusCode().value(),
                    error.contentType(),
                    error.bodyState(),
                    error.bodyLength(),
                    error.code(),
                    error.message()
            );
            if (exception.getStatusCode().value() == 404) {
                return Optional.empty();
            }

            throw new PaymentGatewayUncertainException(
                    "결제 결과를 확인 중입니다.",
                    exception
            );
        } catch (ResourceAccessException exception) {
            log.warn(
                    "Toss payment query is uncertain due to network error. exceptionType={}",
                    exception.getClass().getSimpleName()
            );
            throw new PaymentGatewayUncertainException(
                    "결제 결과를 확인 중입니다.",
                    exception
            );
        } catch (PaymentGatewayDeclinedException
                 | PaymentGatewayUncertainException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn(
                    "Toss payment query is uncertain. exceptionType={}",
                    exception.getClass().getSimpleName()
            );
            throw new PaymentGatewayUncertainException(
                    "결제 결과를 확인 중입니다.",
                    exception
            );
        }
    }

    public Optional<TossPaymentResponse> getPaymentByOrderId(String merchantPaymentId) {
        return getPayment("/v1/payments/orders/{id}", merchantPaymentId);
    }

    private Optional<TossPaymentResponse> getPayment(String path, String id) {
        if (!StringUtils.hasText(id)) {
            throw new PaymentGatewayUncertainException("결제 조회 식별정보를 확인할 수 없습니다.", null);
        }
        try {
            ResponseEntity<TossPaymentResponse> responseEntity = restClient.get()
                    .uri(path, id)
                    .header(HttpHeaders.AUTHORIZATION, createAuthorizationHeader())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve().toEntity(TossPaymentResponse.class);
            return Optional.ofNullable(responseEntity.getBody());
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) return Optional.empty();
            throw new PaymentGatewayUncertainException("결제 결과를 확인 중입니다.", exception);
        } catch (ResourceAccessException exception) {
            throw new PaymentGatewayUncertainException("결제 결과를 확인 중입니다.", exception);
        } catch (PaymentGatewayUncertainException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new PaymentGatewayUncertainException("결제 결과를 확인 중입니다.", exception);
        }
    }

    public TossPaymentResponse cancel(GatewayCancelCommand command) {
        log.info("Toss request started. method=POST, target={}/v1/payments/{{paymentKey}}/cancel, paymentKeyLength={}",
                properties.getBaseUrl(), command.providerPaymentKey().length());
        try {
            ResponseEntity<TossPaymentResponse> entity = restClient.post()
                    .uri("/v1/payments/{paymentKey}/cancel", command.providerPaymentKey())
                    .header(HttpHeaders.AUTHORIZATION, createAuthorizationHeader())
                    .header("Idempotency-Key", command.idempotencyKey())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TossCancelRequest(command.reason(), command.cancelAmount()))
                    .retrieve().toEntity(TossPaymentResponse.class);
            if (entity.getBody() == null) {
                throw new PaymentGatewayUncertainException("결제 취소 결과를 확인 중입니다.", null);
            }
            log.info("Toss cancel response received. httpStatus={}, providerStatus={}",
                    entity.getStatusCode().value(), entity.getBody().status());
            return entity.getBody();
        } catch (RestClientResponseException exception) {
            TossErrorDetails error = extractErrorDetails(exception);
            log.warn("Toss cancel response failed. responseReceived=true, exceptionType={}, httpStatus={}, errorCode={}, message={}",
                    exception.getClass().getSimpleName(), exception.getStatusCode().value(), error.code(), error.message());
            if (isCancelDecline(exception.getStatusCode().value(), error.code())) {
                throw new PaymentGatewayDeclinedException(error.code(), "결제 취소를 완료하지 못했습니다.");
            }
            throw new PaymentGatewayUncertainException("결제 취소 결과를 확인 중입니다.", exception);
        } catch (ResourceAccessException exception) {
            throw new PaymentGatewayUncertainException("결제 취소 결과를 확인 중입니다.", exception);
        } catch (PaymentGatewayDeclinedException | PaymentGatewayUncertainException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new PaymentGatewayUncertainException("결제 취소 결과를 확인 중입니다.", exception);
        }
    }

    private String createAuthorizationHeader() {
        if (!StringUtils.hasText(properties.getSecretKey())) {
            log.error("Toss secret key is not configured.");
            throw new PaymentGatewayUncertainException(
                    "결제 서비스 설정을 확인할 수 없습니다.",
                    null
            );
        }
        if (!properties.getSecretKey().equals(
                properties.getSecretKey().trim()
        )) {
            log.error("Toss secret key contains leading or trailing whitespace.");
            throw new PaymentGatewayUncertainException(
                    "결제 서비스 설정을 확인할 수 없습니다.",
                    null
            );
        }

        log.debug(
                "Toss authorization prepared. keyType={}",
                classifySecretKey()
        );

        String credentials = properties.getSecretKey() + ":";
        String encoded = Base64.getEncoder()
                .encodeToString(
                        credentials.getBytes(StandardCharsets.UTF_8)
                );

        return "Basic " + encoded;
    }

    private TossErrorDetails extractErrorDetails(
            RestClientResponseException exception
    ) {
        try {
            String body = exception.getResponseBodyAsString();
            String contentType = exception.getResponseHeaders() == null
                    || exception.getResponseHeaders().getContentType() == null
                    ? "NONE"
                    : exception.getResponseHeaders()
                    .getContentType()
                    .toString();
            if (body == null || body.isBlank()) {
                return new TossErrorDetails(
                        "NO_CODE",
                        "NO_MESSAGE",
                        contentType,
                        "EMPTY",
                        0
                );
            }

            Matcher codeMatcher = ERROR_CODE_PATTERN.matcher(body);
            Matcher messageMatcher = ERROR_MESSAGE_PATTERN.matcher(body);
            boolean hasCode = codeMatcher.find();
            boolean hasMessage = messageMatcher.find();

            return new TossErrorDetails(
                    hasCode
                            ? sanitizeCode(codeMatcher.group(1))
                            : "UNPARSEABLE",
                    hasMessage
                            ? sanitize(messageMatcher.group(1))
                            : "NO_MESSAGE",
                    contentType,
                    hasCode ? "JSON_ERROR" : "NON_STANDARD",
                    body.length()
            );
        } catch (Exception ignored) {
            return new TossErrorDetails(
                    "UNPARSEABLE",
                    "NO_MESSAGE",
                    "UNKNOWN",
                    "PARSE_FAILED",
                    -1
            );
        }
    }

    private String sanitizeCode(String value) {
        String code = value.trim();
        return code.matches("[A-Za-z0-9_\\-]{1,100}")
                ? code
                : "UNPARSEABLE";
    }

    private String sanitize(String value) {
        String singleLine = value
                .replaceAll("[\\r\\n\\t]", " ")
                .trim();
        String redacted = SENSITIVE_TOKEN_PATTERN
                .matcher(singleLine)
                .replaceAll("[redacted]");
        return redacted.length() <= 200
                ? redacted
                : redacted.substring(0, 200);
    }

    private String classifySecretKey() {
        String secretKey = properties.getSecretKey();
        if (secretKey.startsWith("test_gsk_")) {
            return "TEST_WIDGET_SECRET";
        }
        if (secretKey.startsWith("test_sk_")) {
            return "TEST_API_SECRET";
        }
        if (secretKey.startsWith("live_gsk_")) {
            return "LIVE_WIDGET_SECRET";
        }
        if (secretKey.startsWith("live_sk_")) {
            return "LIVE_API_SECRET";
        }
        return "UNKNOWN_SECRET_TYPE";
    }

    private record TossErrorDetails(
            String code,
            String message,
            String contentType,
            String bodyState,
            int bodyLength
    ) {
    }
}
