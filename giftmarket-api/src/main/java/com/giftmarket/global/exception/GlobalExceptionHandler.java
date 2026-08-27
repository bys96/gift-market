package com.giftmarket.global.exception;

import com.giftmarket.address.exception.AddressException;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.cart.exception.CartException;
import com.giftmarket.global.response.ApiResponse;
import com.giftmarket.inquiry.exception.ProductInquiryException;
import com.giftmarket.order.exception.OrderException;
import com.giftmarket.payment.exception.PaymentException;
import com.giftmarket.payment.exception.PaymentWebhookRetryableException;
import com.giftmarket.product.exception.ProductException;
import com.giftmarket.seller.exception.SellerException;
import com.giftmarket.wishlist.exception.WishlistException;
import com.giftmarket.review.exception.ReviewException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<?>> handleAuthenticationException(
            AuthenticationException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(
                        ApiResponse.fail(
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(SellerException.class)
    public ResponseEntity<ApiResponse<?>> handleSellerException(
            SellerException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiResponse.fail(
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(ProductException.class)
    public ResponseEntity<ApiResponse<?>> handleProductException(
            ProductException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiResponse.fail(
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(CartException.class)
    public ResponseEntity<ApiResponse<?>> handleCartException(
            CartException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiResponse.fail(
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(OrderException.class)
    public ResponseEntity<ApiResponse<?>> handleOrderException(
            OrderException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiResponse.fail(
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(AddressException.class)
    public ResponseEntity<ApiResponse<?>> handleAddressException(
            AddressException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiResponse.fail(
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(ProductInquiryException.class)
    public ResponseEntity<ApiResponse<?>> handleProductInquiryException(ProductInquiryException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(exception.getMessage()));
    }

    @ExceptionHandler(WishlistException.class)
    public ResponseEntity<ApiResponse<?>> handleWishlistException(
            WishlistException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(exception.getMessage()));
    }

    @ExceptionHandler(ReviewException.class)
    public ResponseEntity<ApiResponse<?>> handleReviewException(ReviewException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(exception.getMessage()));
    }

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ApiResponse<?>> handlePaymentException(
            PaymentException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(exception.getMessage()));
    }

    @ExceptionHandler(PaymentWebhookRetryableException.class)
    public ResponseEntity<ApiResponse<?>> handlePaymentWebhookRetryableException(
            PaymentWebhookRetryableException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.fail("결제 웹훅 처리를 재시도해주세요."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        String message =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .findFirst()
                        .map(
                                fieldError ->
                                        fieldError.getDefaultMessage()
                        )
                        .orElse(
                                "요청 값을 확인해 주세요."
                        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ApiResponse.fail(message)
                );
    }

    @ExceptionHandler({
            ServletRequestBindingException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ApiResponse<?>> handleInvalidRequest(Exception exception) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("요청 값을 확인해 주세요."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handle(
            Exception exception
    ) {
        log.error(
                "처리되지 않은 서버 예외가 발생했습니다.",
                exception
        );

        return ResponseEntity
                .status(
                        HttpStatus.INTERNAL_SERVER_ERROR
                )
                .body(
                        ApiResponse.fail(
                                "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
                        )
                );
    }
}
