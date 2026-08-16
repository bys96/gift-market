package com.giftmarket.payment.infrastructure.toss;

import com.giftmarket.payment.gateway.GatewayConfirmCommand;
import com.giftmarket.payment.gateway.PaymentGatewayDeclinedException;
import com.giftmarket.payment.gateway.PaymentGatewayUncertainException;
import com.giftmarket.payment.infrastructure.toss.dto.TossConfirmRequest;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TossPaymentClient {

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
            if (exception.getStatusCode().value() == 400) {
                throw new PaymentGatewayDeclinedException(
                        "TOSS_" + exception.getStatusCode().value(),
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
