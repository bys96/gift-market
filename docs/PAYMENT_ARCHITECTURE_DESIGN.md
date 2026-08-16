# Gift Market Payment 도메인 및 결제 아키텍처 설계

코드는 수정하지 않았습니다. `AGENTS.md`, 최신 `docs/DEVELOPMENT_STATUS.md`, 실제 Backend/Frontend 주문·장바구니·상품·재고·취소·Security·예외 처리 코드를 기준으로 설계했습니다.

## 결론

1차 PG는 **Toss Payments 결제위젯 + Backend 승인 방식**을 추천합니다.

다만 Order와 Payment 핵심 로직은 Toss를 몰라야 하며, 다음 경계를 둡니다.

```text
OrderService
  └─ 주문 생성·금액 계산·재고 예약·snapshot

PaymentService
  └─ 결제 상태 전이·멱등성·승인/취소 결과 반영

PaymentGateway
  ├─ TossPaymentGateway       ← 1차 구현
  ├─ InicisPaymentGateway     ← 향후 추가
  └─ PortOnePaymentGateway    ← 향후 추가
```

PG를 교체할 때 바뀌는 부분은 SDK, API 주소, 인증, 결제수단 코드 매핑, 웹훅 검증, 오류코드 매핑으로 제한합니다.

---

# 1. 현재 Order 구조와 결제 도입 시 문제점

현재 주문 구조는 “결제 없는 주문 확정”에는 일관성이 있지만 실제 결제를 붙이기에는 주문 확정 시점이 너무 빠릅니다.

현재 `OrderService`의 장바구니 주문 흐름은 하나의 DB transaction에서 다음을 처리합니다.

```text
CartItem 소유권 검증
→ Product/Variant pessimistic lock
→ 판매 상태·옵션·재고 검증
→ 재고 차감
→ Order(ORDERED) 생성
→ OrderItem snapshot 생성
→ 주문한 CartItem 삭제
→ commit
```

바로구매도 동일하게 재고를 차감하고 즉시 `ORDERED` 주문을 생성하되 Cart에는 접근하지 않습니다. 주문 취소는 Order를 lock하고 재고를 복원한 후 `CANCELLED`로 변경합니다.

결제 도입 시 문제는 다음과 같습니다.

- 결제창이 닫혀도 이미 주문이 확정되고 CartItem이 삭제됩니다.
- 결제 승인에 실패해도 재고가 계속 차감된 상태가 될 수 있습니다.
- 현재 `ORDERED`만으로 결제 대기와 결제 완료를 구분할 수 없습니다.
- PG 승인 성공 후 서버 응답이 유실되는 상태를 표현할 수 없습니다.
- 같은 주문에 결제를 다시 시도할 Payment 이력이 없습니다.
- 현재 취소는 PG 환불보다 재고 복원이 먼저 발생합니다.
- 중복 클릭, 새로고침, 승인 API 재시도를 막는 식별자가 없습니다.
- 현재 `orderedAt`은 Order 생성과 동시에 필수인데, 결제 전 주문에는 “주문 완료 시각”이 아직 없습니다.

보존해야 할 강점도 분명합니다.

- Backend 금액 재계산
- CartItem 소유권 검증
- Product/Variant pessimistic lock
- 상품·옵션 상태와 재고 검증
- OrderItem 가격·상품·옵션·판매자 snapshot
- 장바구니 주문과 바로구매의 입력 경로 분리
- 취소 시 재고 복원 순서와 lock 정렬
- 주문 소유권 검증
- Cart/direct 실패 메시지 분리

이 로직들은 결제 도입 후에도 최대한 그대로 사용하고, “언제 주문을 확정하고 CartItem을 삭제하는가”만 재정의하는 것이 안전합니다.

---

# 2. PG 후보 비교

| 기준 | Toss Payments 직접 | PortOne V2 | KG이니시스 직접 | KakaoPay/NaverPay |
|---|---|---|---|---|
| 국내 쇼핑몰 적합성 | 매우 높음 | 매우 높음 | 매우 높음 | 단독 주 결제망으로는 제한적 |
| 테스트 환경 | 공개 테스트 키와 테스트 결제 지원 | PG별 테스트 채널 제공 | 테스트 MID 및 모듈 제공 | 각 사업자별 별도 테스트·심사 |
| 카드 | 지원 | 연결 PG에 따라 지원 | 지원 | 카드 외 자체 간편결제 중심 |
| 네이버/카카오/토스페이 | 위젯에서 통합 제공 가능 | 연결한 PG·채널에 따라 다름 | 계약·모듈 설정에 따라 다름 | 해당 간편결제만 직접 제공 |
| 계좌이체·휴대폰 등 | 위젯 계약 설정에 따라 지원 | 연결 PG별 지원 | 폭넓게 지원 | 제한적 |
| Backend 승인 | 결제 인증 후 서버 confirm | 서버 조회·검증 또는 PG별 흐름 추상화 | 인증 결과를 서버 승인 API로 처리 | 사업자별 ready/approve 등 |
| 결제 조회 | 지원 | 통합 조회 API | 지원 | 지원 |
| 전체 취소 | 지원 | 통합 취소 API | 지원 | 지원 |
| 부분 취소 | 지원 | PG가 지원하면 통합 API | 계약·수단별 지원 | 수단별 정책 차이 |
| Webhook | 결제·취소 상태 웹훅 | V2 웹훅, 테스트/운영 URL 분리 | 결과 통보/노티 방식 | 사업자마다 차이 큼 |
| API 멱등성 | `Idempotency-Key` 지원 | V2 API 멱등성 지원 | 공개 표준화 정도가 상대적으로 낮음 | 사업자별 상이 |
| 테스트/운영 키 분리 | 명확 | 채널과 키 분리 | MID·키 분리 | 각 사업자별 분리 |
| 개발 복잡도 | 낮음 | 가장 낮거나 중간 | 높음 | 여러 개를 직접 붙이면 매우 높음 |
| 운영 안정성 | 높음 | PortOne 계층과 실제 PG 양쪽 의존 | 오래된 국내 운영 사례 풍부 | 보조 결제수단으로 적합 |
| PG 교체 | 자체 adapter 필요 | PortOne 내부 채널 전환은 쉬움 | 자체 adapter 필요 | 각 결제사별 재개발 필요 |
| Spring + Next 적합성 | 매우 좋음 | 매우 좋음 | 가능하나 연동 부담 큼 | 단독 통합은 비효율적 |

Toss Payments는 테스트 키와 운영 키, 브라우저용 client key와 서버용 secret key가 분리되어 있고, 결제위젯 인증 후 서버가 `paymentKey`, `orderId`, `amount`를 검증하여 승인합니다. 승인·취소 요청에는 멱등성 키를 사용할 수 있습니다. [Toss API 키](https://docs.tosspayments.com/reference/using-api/api-keys), [결제위젯 연동](https://docs.tosspayments.com/guides/v2/payment-widget/integration-window), [API 인증과 멱등성](https://docs.tosspayments.com/reference/using-api/authorization)

결제위젯은 계약 전 테스트 환경에서도 카드, 네이버페이, 카카오페이, 토스페이, 페이코, 퀵계좌이체, 휴대폰을 테스트할 수 있고, 실제 운영 결제수단은 계약 및 위젯 관리자 설정을 따라갑니다. [Toss Payments FAQ](https://docs.tosspayments.com/resources/faq)

PortOne V2는 여러 PG의 브라우저 SDK와 서버 API를 비교적 통일된 형태로 제공하며, 결제 조회·취소 API 및 `Idempotency-Key`를 지원합니다. 공식 문서상 멱등성 보장 기간은 현재 3시간으로 안내됩니다. [PortOne V2 REST API](https://developers.portone.io/api/rest-v2?v=v2), [PortOne V2 인증결제](https://developers.portone.io/opi/ko/integration/start/v2/checkout)

다만 PortOne을 사용해도 실제 PG 계약과 결제수단 활성화가 자동으로 통일되는 것은 아닙니다. PG별 채널 credential과 계약이 필요하고, 일부 간편결제 테스트는 해당 사업자에게 별도 테스트 상점 정보를 발급받아야 합니다. [PortOne 채널 관리](https://developers.portone.io/opi/ko/console/guide/channel-manage)

KG이니시스 직접 연동은 국내 운영 경험과 결제수단 범위가 강점이지만, 현재 프로젝트가 처음 결제를 도입하는 상황에서는 MID·모듈·승인·노티·결제수단별 예외를 직접 다뤄야 하므로 구현과 검증 비용이 큽니다. 공식 테스트 자료에는 `INIpayTest` MID를 사용하는 스테이징 흐름도 안내됩니다. [KG이니시스 스테이징 자료](https://manual.inicis.com/download/TLS12_test_manual.pdf)

KakaoPay 직접 연동은 `ready → 사용자 인증 → approve` 흐름과 조회·취소 API를 제공하지만, 전체 쇼핑몰 결제수단을 대체하기보다 하나의 간편결제수단을 직접 붙이는 형태입니다. 여러 직접 결제를 각각 붙이면 인증·웹훅·취소·대사 경로가 급격히 늘어납니다. [KakaoPay 온라인 결제](https://developers.kakaopay.com/docs/payment/online), [KakaoPay 결제 공통](https://developers.kakaopay.com/docs/payment/online/common)

수수료는 어떤 방식을 선택해도 업종, 매출, 카드사 심사, 간편결제 계약, 정산 조건에 따라 달라집니다. 개발 편의만으로 확정할 수 없으며 실제 계약 견적을 별도로 비교해야 합니다.

---

# 3. 추천 PG와 이유

## 추천: Toss Payments 직접 연동

현재 프로젝트에는 Toss Payments가 가장 적합합니다.

- Next.js 결제위젯과 Spring Backend 승인 구조가 단순합니다.
- 테스트 키로 실제 과금 없이 승인·실패·취소를 충분히 검증할 수 있습니다.
- 카드와 주요 간편결제를 하나의 위젯으로 시작할 수 있습니다.
- 서버 승인, 조회, 취소, webhook, idempotency를 공식적으로 지원합니다.
- PortOne이라는 중간 계층 없이 PG 상태와 장애를 직접 관찰할 수 있습니다.
- 첫 결제 도입 범위를 작게 유지할 수 있습니다.
- 자체 `PaymentGateway` 경계를 두면 향후 PortOne 또는 이니시스 adapter를 추가할 수 있습니다.

PortOne V2는 “여러 PG를 자주 교체하거나 동시에 운영해야 한다”는 요구가 초기부터 강하다면 좋은 선택입니다. 하지만 현재는 Toss 한 곳을 먼저 안정적으로 붙이는 단계이므로, 자체 domain 경계를 유지한 Toss 직접 연동이 복잡도와 통제력의 균형이 좋습니다.

---

# 4. PG 교체 가능한 전체 아키텍처

```text
OrderController
   │
   ├─ OrderService
   │    ├─ 주문 입력 검증
   │    ├─ Backend 금액 계산
   │    ├─ 재고 예약/복원
   │    └─ OrderItem snapshot
   │
   └─ PaymentController
        │
        └─ PaymentService
             ├─ Payment 상태 전이
             ├─ 승인/취소 멱등성
             ├─ Order 결제 상태 반영
             ├─ CartItem 후처리
             └─ PaymentGateway
                    ├─ TossPaymentGateway
                    │    ├─ TossPaymentClient
                    │    ├─ TossPaymentMapper
                    │    └─ TossWebhookVerifier
                    ├─ InicisPaymentGateway
                    └─ PortOnePaymentGateway
```

핵심 원칙은 다음과 같습니다.

- `OrderService`는 Toss의 `paymentKey`나 API URL을 알지 않습니다.
- `PaymentService`는 Toss response DTO를 받지 않습니다.
- PG adapter는 응답을 `GatewayConfirmResult`, `GatewayCancelResult`, `GatewayPaymentStatus` 같은 내부 DTO로 변환합니다.
- DB에는 Toss 전체 response JSON을 저장하지 않습니다.
- PG 원문 오류코드는 adapter에서 내부 실패 의미로 매핑합니다.
- 사용자 메시지에는 PG 내부 키·거래 ID·stack trace를 노출하지 않습니다.
- PG 교체 시 기존 Order, OrderItem, 재고, 멱등성, 취소 정책은 유지합니다.

---

# 5. PaymentGateway / adapter 경계

1차에 필요한 최소 interface는 이 정도면 충분합니다.

```java
public interface PaymentGateway {
    PaymentProvider provider();

    GatewayConfirmResult confirm(GatewayConfirmCommand command);

    GatewayPaymentResult getPayment(String providerPaymentKey);

    GatewayCancelResult cancel(GatewayCancelCommand command);

    GatewayWebhookEvent verifyAndParseWebhook(
        Map<String, String> headers,
        String body
    );
}
```

내부 command/result에는 PG-neutral 값만 둡니다.

```text
GatewayConfirmCommand
- merchantPaymentId
- providerPaymentKey
- amount
- currency
- idempotencyKey

GatewayConfirmResult
- status
- providerPaymentKey
- providerTransactionId
- approvedAmount
- currency
- paymentMethod
- easyPayProvider
- approvedAt
- providerStatus
- failure
```

Toss의 `paymentKey`, 이니시스의 TID, PortOne의 payment ID는 adapter에서 `providerPaymentKey` 또는 `providerTransactionId`로 매핑합니다.

여러 adapter가 실제로 생기기 전까지 복잡한 plugin framework는 만들지 않습니다. 초기에는 `PaymentProvider`를 기준으로 구현체 하나를 선택하는 작은 registry 정도면 충분합니다.

---

# 6. 결제수단의 PG-neutral 설계

```java
public enum PaymentProvider {
    TOSS,
    INICIS,
    PORTONE
}

public enum PaymentMethod {
    CARD,
    TRANSFER,
    VIRTUAL_ACCOUNT,
    EASY_PAY,
    MOBILE
}

public enum EasyPayProvider {
    NAVERPAY,
    KAKAOPAY,
    TOSSPAY,
    PAYCO,
    SAMSUNGPAY,
    APPLEPAY,
    OTHER
}
```

예시는 다음과 같습니다.

```text
provider = TOSS
method = EASY_PAY
easyPayProvider = NAVERPAY
```

향후 이니시스로 변경하면:

```text
provider = INICIS
method = EASY_PAY
easyPayProvider = NAVERPAY
```

domain 의미는 유지되고 PG 코드 매핑만 변경됩니다.

PG가 결제수단을 판별하지 못했거나 아직 결제가 완료되지 않았다면 `method`와 `easyPayProvider`는 nullable이어야 합니다.

---

# 7. 간편결제 처리 방식

1차는 결제위젯 안에서 네이버페이·카카오페이·토스페이를 제공하는 방식이 적절합니다.

Frontend가 각각의 간편결제 버튼과 SDK를 직접 관리하지 않습니다. 실제 노출 수단은 Toss 계약 및 위젯 관리자 설정을 따릅니다.

향후에는 다음 API를 추가할 수 있습니다.

```http
GET /api/payments/methods
```

예시 응답:

```json
{
  "provider": "TOSS",
  "uiMode": "WIDGET",
  "methods": [
    { "method": "CARD", "enabled": true },
    {
      "method": "EASY_PAY",
      "enabled": true,
      "providers": ["NAVERPAY", "KAKAOPAY", "TOSSPAY"]
    }
  ]
}
```

1차에는 API 없이 결제위젯 구성으로 시작해도 됩니다. 다만 Frontend 코드에서는 `TossWidget` 호출을 주문 페이지 전체에 섞지 않고 별도 payment adapter/component에 격리해야 합니다.

---

# 8. 제안 ERD

```text
User
 └─ 1:N Order
       ├─ 1:N OrderItem
       │      └─ source_cart_item_id (nullable snapshot)
       │
       └─ 1:N Payment
              ├─ 1:N PaymentCancellation
              └─ 1:N PaymentWebhookEvent
```

`Order 1:N Payment`가 맞습니다.

한 Order에서 첫 결제가 실패한 뒤 다른 결제수단으로 다시 시도할 수 있기 때문입니다. 다만 동시에 활성화되는 결제 시도는 하나만 허용하고, `PAID` Payment도 하나만 허용합니다.

MySQL에서 조건부 unique index가 간단하지 않으므로 다음을 함께 사용합니다.

- Order row pessimistic lock
- 한 Order당 활성 `READY/CONFIRMING` Payment 하나
- Order가 이미 `PAID`이면 새 승인 차단
- `merchantPaymentId` unique
- `providerPaymentKey` unique
- client idempotency key unique
- 늦게 도착한 두 번째 승인 발견 시 즉시 자동 취소 시도 및 운영 알림

---

# 9. Payment Entity

권장 필드입니다.

```text
Payment
- id
- order_id
- provider
- status
- merchant_payment_id          unique
- client_request_key
- confirm_idempotency_key      unique
- provider_payment_key         unique, nullable
- provider_transaction_id      nullable
- amount
- currency
- method                       nullable
- easy_pay_provider            nullable
- provider_status              nullable
- failure_code                 nullable
- failure_message              nullable, 정제된 메시지
- requested_at
- confirming_at                nullable
- approved_at                  nullable
- failed_at                    nullable
- cancelled_at                 nullable
- expires_at
- version                      @Version
- created_at
- updated_at
```

Constraint 후보:

```text
UNIQUE(user_id, client_request_key)
UNIQUE(merchant_payment_id)
UNIQUE(provider, provider_payment_key)
UNIQUE(confirm_idempotency_key)
```

`user_id`는 Payment에 중복 저장하지 않고 Order를 통해 접근해도 되지만, prepare key unique를 효율적으로 구성하려면 Order에 `clientOrderKey`를 둘 수도 있습니다. 가장 단순한 형태는 Order에 준비 요청 key를 저장하고 Payment에는 승인용 key를 저장하는 방식입니다.

저장하지 않을 정보:

- 카드번호 전체
- CVC
- PG secret
- 전체 인증 payload
- 전체 PG response JSON
- 불필요한 구매자 개인정보
- 사용자 화면에 노출될 수 있는 내부 stack trace

부분환불 확장을 위해 `PaymentCancellation`에는 다음 정도를 둡니다.

```text
- id
- payment_id
- client_request_key
- status
- amount
- reason
- provider_cancellation_id
- failure_code
- failure_message
- requested_at
- completed_at
- version
```

---

# 10. PaymentStatus

```java
public enum PaymentStatus {
    READY,
    CONFIRMING,
    PAID,
    FAILED,
    EXPIRED,
    CANCELING,
    CANCELED
}
```

의미:

- `READY`: 주문과 재고 예약 완료, 결제창을 열 수 있음
- `CONFIRMING`: 서버가 PG 승인을 요청했거나 결과 확인 중
- `PAID`: 금액·주문번호·통화까지 검증된 승인 완료
- `FAILED`: PG가 명시적으로 실패를 확정
- `EXPIRED`: 결제 가능 시간이 만료됨
- `CANCELING`: PG 취소 결과 확인 중
- `CANCELED`: PG 취소 완료

네트워크 timeout은 `FAILED`가 아닙니다. `CONFIRMING`에 남겨 조회·webhook·reconciliation으로 최종 상태를 확인해야 합니다.

별도 `UNKNOWN` 상태는 1차에는 만들지 않아도 됩니다. “모름” 상태는 `CONFIRMING` 또는 `CANCELING`과 마지막 조회 정보로 표현할 수 있습니다.

---

# 11. OrderStatus

권장 상태는 다음과 같습니다.

```java
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PAYMENT_FAILED,
    PAYMENT_EXPIRED,
    CANCELLED
}
```

다만 결제 시도 하나가 실패했다고 항상 Order를 `PAYMENT_FAILED`로 바꾸지는 않습니다.

- 예약 시간이 남아 재시도 가능: Order는 `PENDING_PAYMENT`
- 사용자가 주문 결제를 종료하고 재고를 반환: `PAYMENT_FAILED`
- 제한시간 만료 및 재고 반환: `PAYMENT_EXPIRED`
- 결제 완료: `PAID`
- 결제 취소 및 재고 복원 완료: `CANCELLED`

Order에는 `CANCELING`을 굳이 추가하지 않습니다. 취소 처리 중에는 Order를 `PAID`로 유지하고 Payment만 `CANCELING`으로 둡니다. PG 취소가 성공해야 Order를 `CANCELLED`로 확정합니다.

기존 개발 DB의 `ORDERED` 데이터는 결제 이전에 이미 확정된 주문이므로 migration에서 `PAID`로 변환하는 것이 가장 자연스럽습니다.

```sql
UPDATE orders
SET status = 'PAID'
WHERE status = 'ORDERED';
```

`orderedAt`은 실제 결제 완료 시각 의미로 바꾸려면 nullable 전환이 필요합니다. 더 명확하게는 다음처럼 구분합니다.

- `createdAt`: 결제 대기 주문 생성 시각
- `paidAt` 또는 기존 `orderedAt`: 실제 결제 확정 시각
- `cancelledAt`: 취소 확정 시각

기존 API 호환성을 고려하면 필드명은 당장 `orderedAt`으로 유지하되 `PENDING_PAYMENT`에서는 null을 허용하는 최소 변경이 현실적입니다.

### `ordered_at` nullable migration 필수 조건

Entity의 nullable mapping 변경만으로 기존 MySQL 컬럼의 `NOT NULL` 제약이
항상 제거되는 것은 아니다. 특히 현재 개발 설정인 Hibernate
`ddl-auto: update`는 `NOT NULL → NULL` 같은 기존 제약 변경을 안전한 migration으로
보장하지 않는다.

따라서 Payment 주문 준비 기능 적용 전에 다음 변경을 명시적 DB migration으로
수행해야 한다.

```sql
ALTER TABLE gift_market.orders
MODIFY ordered_at DATETIME(6) NULL;
```

적용 후 `SHOW COLUMNS FROM gift_market.orders LIKE 'ordered_at';` 결과의
`Null`이 `YES`인지 확인한다.

- `PENDING_PAYMENT`: `ordered_at = NULL`
- `PAID`: PG 승인 확정 시각 기록
- 기존 `ORDERED`: 기존 `ordered_at` 값 유지

애플리케이션 코드에서 임의 시각을 채워 DB 제약을 우회하지 않는다. 운영 전에는
Flyway 또는 Liquibase를 도입해 이 DDL을 versioned migration으로 관리하고,
배포 순서와 rollback/검증 절차를 함께 준비한다.

---

# 12. 주문 준비 흐름

장바구니와 바로구매 모두 기존 검증 로직을 재사용합니다.

```text
1. 인증 사용자 확인
2. client idempotency key 확인
3. 동일 key의 기존 Order가 있으면 기존 결과 반환
4. CartItem 또는 direct 입력 검증
5. Product/Variant를 현재 정렬 순서대로 pessimistic lock
6. 판매 상태·옵션 관계·옵션 활성·수량·재고 검증
7. Backend에서 가격·배송비·최종 금액 재계산
8. 재고 예약 차감
9. Order(PENDING_PAYMENT) 생성
10. OrderItem snapshot 생성
11. Payment(READY) 생성
12. CartItem은 삭제하지 않음
13. commit
14. Frontend에 결제 준비 정보 반환
```

장바구니와 바로구매의 차이는 주문 원본을 읽는 부분뿐입니다.

- 장바구니: 현재 사용자 소유 CartItem을 원본으로 사용
- 바로구매: request의 productId/variantId/quantity를 원본으로 사용
- 공통: 상품 lock 이후 Backend가 가격과 재고를 다시 계산

기존 `POST /api/orders`, `POST /api/orders/direct` endpoint를 유지하되, 응답 의미가 “주문 완료”에서 “결제 대기 주문 준비 완료”로 바뀝니다.

---

# 13. 재고 예약과 복원

## 권장: 결제 준비 시 재고 차감

현재 재고 차감 로직을 “판매 확정 차감”이 아니라 “결제 시간 동안의 예약 차감”으로 사용합니다.

장점:

- 동시에 마지막 상품을 결제하려는 사용자 간 overselling을 방지합니다.
- 결제 인증까지 완료했는데 승인 시점에 재고가 없어지는 UX를 피합니다.
- 현재 pessimistic lock과 재고 감소 로직을 거의 그대로 재사용할 수 있습니다.

단점:

- 결제창을 닫은 사용자의 재고가 일정 시간 묶입니다.
- 만료 처리 job이 반드시 필요합니다.
- `CONFIRMING` 상태를 성급하게 만료시키면 실제 결제와 재고가 어긋날 수 있습니다.

권장 예약 시간은 설정값으로 관리합니다. 초기값은 약 30분이 적절하지만 실제 PG 인증 유효시간과 사용자 UX를 확인하여 조정해야 합니다.

재고 복원 조건:

- 결제 준비 후 명시적 포기
- Payment 명시적 실패 후 재시도하지 않기로 한 경우
- `PENDING_PAYMENT` 만료
- 결제 완료 후 전체 취소 성공

복원하면 안 되는 조건:

- 승인 API timeout
- `CONFIRMING`
- webhook을 아직 받지 못한 상태
- PG 취소 실패
- `CANCELING`

중복 복원을 막기 위해 Order에 다음 audit 필드를 두는 것을 권장합니다.

```text
inventory_released_at nullable
```

Order lock 아래에서 `inventoryReleasedAt == null`일 때만 복원합니다.

---

# 14. CartItem 처리

현재처럼 주문 준비 시 CartItem을 삭제하면 안 됩니다.

가장 작은 안전한 변경은 `OrderItem`에 nullable snapshot을 추가하는 것입니다.

```text
source_cart_item_id nullable
```

- 장바구니 주문: 원본 CartItem ID 저장
- 바로구매: null
- 실제 FK는 두지 않음

CartItem은 사용자가 중간에 삭제하거나 수량을 바꿀 수 있으므로 FK보다는 snapshot ID가 적합합니다.

결제 성공 시:

1. 해당 Order의 `sourceCartItemId` 목록 조회
2. 현재 사용자 소유인지 확인
3. 현재 CartItem의 product/variant/quantity가 주문 snapshot과 같을 때만 삭제
4. 결제 대기 중 사용자가 수량이나 옵션을 바꿨다면 그 CartItem은 유지

이렇게 해야 사용자가 결제 대기 중 새로 구성한 장바구니를 결제 완료 후 잘못 삭제하지 않습니다.

바로구매 OrderItem은 `sourceCartItemId=null`이므로 Cart repository를 호출하지 않습니다.

---

# 15. 결제 승인 흐름

Toss 기준 Frontend success URL의 값을 바로 신뢰하지 않습니다.

```text
[Transaction A]
1. Payment와 Order lock
2. 사용자 소유권 확인
3. Payment가 이미 PAID면 기존 성공 결과 반환
4. READY 상태 및 금액·만료 여부 확인
5. providerPaymentKey 저장
6. Payment = CONFIRMING
7. commit

[DB transaction 밖]
8. PaymentGateway.confirm()
9. Toss adapter가 confirm API 호출
10. merchantPaymentId/orderId, amount, currency, 승인 상태 검증

[Transaction B]
11. Payment와 Order 다시 lock
12. 이미 PAID면 기존 결과 반환
13. Payment = PAID
14. Order = PAID
15. orderedAt/approvedAt 기록
16. 장바구니 주문이면 정확한 CartItem만 삭제
17. commit
```

PG HTTP 호출은 DB transaction 밖에서 수행합니다.

PG 승인 성공 직후 Backend가 죽어 11번 이후가 실행되지 않더라도:

- 같은 confirm 요청 재시도
- PG 결제 조회
- webhook
- `CONFIRMING` reconciliation job

중 하나가 DB 상태를 최종 `PAID`로 수렴시킵니다.

---

# 16. Idempotency

멱등성 키는 두 종류로 분리하는 것이 좋습니다.

## 주문 준비 key

```text
clientOrderRequestKey
```

- 사용자가 결제 버튼을 처음 누를 때 Frontend에서 UUID 생성
- 같은 주문 입력을 재시도하는 동안 `sessionStorage` 등에 유지
- Backend DB unique constraint로 중복 Order 생성 방지
- 응답이 유실되면 같은 key로 기존 Order/Payment 반환
- 장바구니 내용이나 배송지가 바뀌면 새 key 발급

## PG 승인 key

```text
confirmIdempotencyKey
```

- Backend가 Payment 생성 시 UUID 발급
- DB에 저장
- 같은 Payment 승인 재시도는 항상 같은 key 사용
- Toss `Idempotency-Key` header로 전달
- Frontend가 임의로 정하지 않음

취소에는 별도 `cancelIdempotencyKey`를 `PaymentCancellation`마다 저장합니다.

중복 방지 장치:

- Order 준비 key unique
- `merchantPaymentId` unique
- `providerPaymentKey` unique
- confirm key unique
- Order pessimistic lock
- Payment `@Version`
- 한 Order당 active Payment 하나
- PAID Order의 추가 승인 차단

두 결제가 동시에 승인되는 극단적인 상황에는 늦게 확인된 중복 승인을 자동 취소하고 운영 알림을 발생시켜야 합니다.

---

# 17. DB transaction 경계

## 주문 준비

하나의 transaction:

```text
검증 → row lock → 금액 계산 → 재고 예약
→ Order/OrderItem/Payment 저장 → commit
```

외부 PG 호출 없음.

## 결제 승인

```text
Tx A: CONFIRMING 전환 → commit
외부: PG confirm/query
Tx B: PAID 반영 + CartItem 삭제 → commit
```

## 결제 만료

```text
Order/Payment lock
→ PG에 승인되지 않았음이 확실한 상태만 처리
→ 재고 복원
→ Payment EXPIRED
→ Order PAYMENT_EXPIRED
→ commit
```

## 결제 취소

```text
Tx A: Payment CANCELING + 취소 요청 기록 → commit
외부: PG cancel
Tx B: CANCELED/CANCELLED + 재고 복원 → commit
```

PG 호출 성공 후 DB 반영에 실패할 수 있으므로 모든 외부 호출 결과는 재조회와 reconciliation이 가능해야 합니다.

---

# 18. Webhook

운영 환경에서는 webhook이 필수 안전장치입니다. Toss는 결제 상태 및 취소 상태 변경 webhook을 제공하고, 공식 문서도 이벤트 중복 및 서명 검증을 고려하도록 안내합니다. [Toss webhook 이벤트](https://docs.tosspayments.com/reference/using-api/webhook-events)

endpoint는 provider별로 분리합니다.

```http
POST /api/payments/webhooks/toss
POST /api/payments/webhooks/inicis
POST /api/payments/webhooks/portone
```

보안 정책:

- 정확한 webhook endpoint만 `permitAll`
- 사용자 JWT 인증과 분리
- provider adapter에서 signature/authenticity 검증
- 검증 실패 시 처리 금지
- payload의 금액·상태만 믿지 않고 PG 조회 API로 재확인
- Order 금액, 통화, merchant ID 일치 확인
- event deduplication
- 허용된 상태 전이만 반영
- 동일 event 재수신 시 같은 결과 반환
- 민감한 원문 payload 저장 금지
- 처리할 수 없는 이벤트는 운영 알림

`PaymentWebhookEvent`에는 다음 정도만 저장합니다.

```text
provider
provider_event_id 또는 dedup_hash
event_type
payment_id
processing_status
received_at
processed_at
failure_reason
```

PortOne도 브라우저 redirect만으로 결제를 확정하지 말고 webhook과 결제 조회를 이용해 금액·통화·상태를 검증하도록 안내합니다. [PortOne Hosted Checkout 결과 처리](https://developers.portone.io/opi/ko/extra/hosted-checkout/readme-v2?v=v2)

---

# 19. 결제 취소와 환불

## 결제 전 주문 취소

`PENDING_PAYMENT`이고 승인 시도가 없거나 실패가 확정된 경우:

```text
Order/Payment lock
→ Payment CANCELED 또는 EXPIRED
→ Order CANCELLED 또는 PAYMENT_EXPIRED
→ 재고 복원
→ CartItem 유지
```

## PAID 주문 전체 취소

```text
1. 사용자 소유권·취소 가능 상태 확인
2. Order/Payment lock
3. Payment = CANCELING
4. PaymentCancellation = REQUESTED
5. commit
6. transaction 밖에서 PG 전체 취소 API
7. PG 성공 결과와 취소 금액 검증
8. 새 transaction
9. Payment = CANCELED
10. PaymentCancellation = SUCCEEDED
11. Order = CANCELLED
12. 재고를 한 번만 복원
13. commit
```

PG 취소 실패 시:

- Order는 `PAID` 유지
- Payment는 실패 확인 후 `PAID`로 복귀하거나 재시도 가능한 취소 상태 유지
- 재고 복원 금지
- 사용자에게 “취소 처리를 완료하지 못했습니다” 안내
- 운영 알림 및 재조회 대상 등록

## 부분환불

1차에서는 지원하지 않는 것이 맞습니다.

현재는 다음 구조가 없습니다.

- OrderItem별 배송·취소 상태
- 반품/교환
- 상품별 환불액 배분
- 배송비 재계산
- 쿠폰·포인트 배분
- 판매자별 정산 취소

대신 `PaymentCancellation.amount` 구조를 두어 향후 부분환불을 추가할 수 있게 합니다.

---

# 20. Frontend 결제 UX

## 주문서

```text
배송지
상품 목록
결제수단 위젯
최종 결제금액
결제하기
```

Backend가 계산한 금액만 위젯에 전달합니다.

## 진행 흐름

```text
1. 결제하기 클릭
2. 버튼 비활성화
3. clientOrderRequestKey 생성/재사용
4. 주문 준비 API 호출
5. 서버 금액으로 PG 결제창 실행
6. success/fail URL 이동
7. success 화면에서 Backend confirm 호출
8. “결제 확인 중” 표시
9. PAID 확인 후 주문 상세로 이동
```

상황별 UX:

- 결제창 닫기: 주문은 `PENDING_PAYMENT`, 재결제 또는 주문 취소 제공
- 사용자가 취소: Cart 유지, 예약 주문 취소 시 재고 복원
- fail URL: PG의 내부 메시지를 그대로 노출하지 않고 사용자용 메시지 표시
- success 페이지 새로고침: 같은 Payment와 confirm key로 멱등 처리
- 뒤로가기: PAID이면 다시 결제창을 열지 않고 주문 상세로 이동
- confirm timeout: 실패라고 단정하지 않고 “결제 결과를 확인 중입니다”
- 이미 PAID: 성공 결과 반환
- PENDING 재진입: 만료 전이면 기존 Payment 재사용 또는 안전한 retry
- 만료 주문: 재고·가격을 다시 검증하여 새 Order 준비
- 모바일 외부 앱 복귀 실패: status API polling과 webhook으로 복구

Frontend에 허용되는 키는 공개 client key뿐입니다. secret, PG 인증 헤더, 취소 권한은 Backend 환경변수 또는 운영 secret manager에서만 관리합니다.

---

# 21. 테스트 전략

## Domain/Service 테스트

- 장바구니 선택 주문 준비 성공
- 바로구매 준비 성공
- 저장 배송지 snapshot 유지
- Backend 금액 계산과 Frontend 조작 금액 무시
- 품절·판매 중지·옵션 비활성·잘못된 variant 차단
- prepare key 재요청 시 Order 중복 생성 안 됨
- 재고 예약이 한 번만 발생
- 만료 시 재고가 한 번만 복원
- 바로구매가 Cart를 변경하지 않음
- 결제 전 장바구니 CartItem 유지
- PAID 후 정확한 CartItem만 삭제
- 결제 대기 중 변경된 CartItem은 삭제하지 않음

## Payment 테스트

- 정상 승인
- PG 명시적 실패
- 승인 timeout 후 조회 결과 PAID
- 승인 성공 응답 유실 후 같은 요청 재시도
- success 페이지 새로고침
- webhook이 confirm보다 먼저 도착
- webhook이 confirm보다 늦게 도착
- 중복 webhook
- 잘못된 금액·통화·merchant ID
- 이미 PAID인 Order 재승인
- 동시에 두 Payment 승인 시도
- 결제 만료와 confirm 경합
- 취소 성공
- 취소 실패 시 재고 미복원
- 취소 성공 응답 유실 후 webhook 복구

## Toss 테스트 환경

- 테스트 client key와 secret key 사용
- 실제 과금 없는 카드·간편결제 테스트
- 테스트 취소
- 공식 테스트 오류 재현 방식 사용
- 성공/실패/사용자 취소/timeout 모의
- 운영 키가 테스트 환경에서 로딩되지 않는지 검증

Toss는 테스트 키로 실제 과금 없이 결제 흐름을 시뮬레이션하고, 요청 헤더를 통한 테스트 오류 재현 방법을 제공합니다. [Toss API 인증·테스트](https://docs.tosspayments.com/reference/using-api/authorization)

Frontend/Backend 기존 검증도 계속 수행합니다.

```bash
cd giftmarket-api
./gradlew test

cd giftmarket-web
npm run lint
npm run build
```

---

# 22. 운영 전 체크리스트

- Toss 또는 선택 PG 계약과 카드사 심사 완료
- 네이버페이·카카오페이·토스페이 등 실제 결제수단 활성화 확인
- 테스트/운영 client key와 secret 완전 분리
- secret Git 미포함 및 로그 마스킹
- HTTPS와 운영 success/fail/webhook URL 등록
- webhook signature 검증
- 금액·통화·merchant ID 서버 검증
- 승인·취소 idempotency key 적용
- DB unique constraint 적용
- Order/Payment 상태 전이 테스트
- pessimistic lock과 deadlock 재시도 정책 확인
- PENDING_PAYMENT 만료 scheduler
- CONFIRMING/CANCELING 장기 체류 reconciliation
- 일일 PG 거래와 DB 결제 대사
- 중복 승인·취소 실패·금액 불일치 운영 알림
- 관리자 결제 조회 및 재동기화 기능
- 개인정보·PG key 로그 노출 점검
- 환불 권한 관리자 분리와 감사 로그
- 장애 시 결제 버튼 차단 또는 점검 안내
- 배포 중 webhook 처리 유실 방지
- DB migration 및 rollback 계획
- CS용 merchantPaymentId 검색 기능
- 재고 복원 중복 방지 검증
- 실결제 소액 승인·전체 취소 smoke test

---

# 23. 안전한 구현 단계

다음 6단계가 적절합니다.

1. Payment domain과 DB migration
2. 기존 주문 생성을 결제 준비 흐름으로 전환
3. PaymentGateway와 Toss 승인 adapter
4. Frontend 결제위젯과 success/fail 복구
5. 결제 취소·webhook·만료 처리
6. reconciliation·운영 도구·통합 테스트

각 단계가 독립적으로 검증 가능해야 하며, 특히 2단계와 3단계를 한꺼번에 크게 변경하지 않는 것이 좋습니다.

---

# 24. 단계별 수정 파일과 기능

아래는 구현 시 예상되는 정확한 변경 범위입니다. 실제 package naming은 현재 `com.giftmarket` 구조를 유지합니다.

## 1단계: Payment domain

기존 수정:

- `order/domain/Order.java`
- `order/domain/OrderItem.java`
- `order/domain/OrderStatus.java`
- `order/repository/OrderRepository.java`

신규:

- `payment/domain/Payment.java`
- `payment/domain/PaymentStatus.java`
- `payment/domain/PaymentProvider.java`
- `payment/domain/PaymentMethod.java`
- `payment/domain/EasyPayProvider.java`
- `payment/domain/PaymentCancellation.java`
- `payment/domain/PaymentCancellationStatus.java`
- `payment/repository/PaymentRepository.java`
- `payment/repository/PaymentCancellationRepository.java`
- DB migration 파일

기능:

- Order `PENDING_PAYMENT`
- Payment 1:N
- 멱등성 unique constraint
- `sourceCartItemId`
- `@Version`
- 기존 `ORDERED → PAID` migration

## 2단계: 주문 준비와 재고 예약

수정:

- `order/service/OrderService.java`
- `order/controller/OrderController.java`
- `order/dto/OrderCreateRequest.java`
- `order/dto/DirectOrderCreateRequest.java`
- `order/dto/OrderCreateResponse.java`
- `order/dto/OrderDetailResponse.java`
- `order/dto/OrderListResponse.java`
- 관련 OrderException
- `cart/repository/CartItemRepository.java`

기능:

- 기존 장바구니/direct 검증 유지
- 주문 생성 대신 결제 대기 Order 준비
- 재고 예약
- CartItem 삭제 연기
- client idempotency key
- 예약 만료시각 반환

## 3단계: 공통 PaymentService와 Toss adapter

신규:

- `payment/service/PaymentService.java`
- `payment/gateway/PaymentGateway.java`
- `payment/gateway/GatewayConfirmCommand.java`
- `payment/gateway/GatewayConfirmResult.java`
- `payment/gateway/GatewayCancelCommand.java`
- `payment/gateway/GatewayCancelResult.java`
- `payment/infrastructure/toss/TossPaymentGateway.java`
- `payment/infrastructure/toss/TossPaymentClient.java`
- `payment/infrastructure/toss/TossPaymentMapper.java`
- `payment/infrastructure/toss/TossPaymentProperties.java`
- `payment/controller/PaymentController.java`
- `payment/dto/PaymentConfirmRequest.java`
- `payment/dto/PaymentResponse.java`
- 관련 PaymentException

수정:

- Backend application 환경설정
- `SecurityConfig.java`

기능:

- 승인 전 `CONFIRMING`
- transaction 밖 PG 호출
- 금액·통화·주문번호 검증
- 승인 멱등성
- PAID 확정
- 결제 성공 후 CartItem 삭제

## 4단계: Frontend 결제 UX

수정:

- `app/order/page.tsx`
- `lib/order-api.ts`
- `types/order.ts`
- 관련 order CSS
- `/my/orders`
- `/my/orders/[orderId]`

신규:

- `lib/payment-api.ts`
- `types/payment.ts`
- `components/payment/TossPaymentWidget.tsx`
- `app/payment/success/page.tsx`
- `app/payment/fail/page.tsx`
- 결제 확인 중 관련 CSS

기능:

- 주문 준비와 실제 결제 분리
- Toss 공개 client key만 사용
- 더블클릭 방지
- success confirm
- 새로고침 멱등 처리
- timeout/status polling
- 주문 상태별 UI

## 5단계: 취소·webhook·만료

신규:

- `payment/controller/PaymentWebhookController.java`
- `payment/service/PaymentWebhookService.java`
- `payment/domain/PaymentWebhookEvent.java`
- `payment/repository/PaymentWebhookEventRepository.java`
- `payment/infrastructure/toss/TossWebhookVerifier.java`
- `payment/service/PaymentExpirationService.java`
- scheduler 설정

수정:

- `order/service/OrderService.java`
- 기존 주문 취소 endpoint
- `SecurityConfig.java`
- 주문 상세 취소 UI

기능:

- 정확한 Toss webhook endpoint만 permit
- signature 검증과 event dedupe
- 전체 결제 취소
- 취소 성공 후 재고 복원
- PENDING_PAYMENT 만료
- Cart 유지

## 6단계: 운영 안정성

신규 또는 확장:

- `PaymentReconciliationService`
- 장기 `CONFIRMING/CANCELING` 조회 scheduler
- 관리자 결제 조회·재동기화 API
- 결제/취소 실패 알림
- 일일 대사 batch 또는 export
- 통합·동시성 테스트

향후 이니시스나 PortOne으로 교체할 때는 주로 다음만 추가합니다.

```text
InicisPaymentGateway
InicisPaymentClient
InicisPaymentMapper
InicisWebhookVerifier
InicisProperties
Frontend Inicis launcher 또는 PortOne SDK adapter
PG별 환경설정과 계약
```

OrderService, PaymentService, Payment Entity, 재고 예약·복원, 멱등성, Cart 후처리, 대부분의 결제 결과 화면은 유지합니다.

---

## 아주 쉽게 요약하면

1. 결제하기를 누르면 서버가 상품 가격과 재고를 다시 확인합니다.
2. 재고를 잠시 예약하고 “결제 대기 주문”을 만듭니다.
3. 이때 장바구니 상품은 아직 삭제하지 않습니다.
4. 사용자가 Toss 결제창에서 결제를 완료하면 서버가 Toss에 직접 승인 여부를 확인합니다.
5. 돈이 정상 승인된 것이 확인되어야 주문을 완료하고 장바구니 상품을 삭제합니다.
6. 응답이 끊겨도 재조회와 webhook으로 결제 결과를 복구합니다.
7. 결제 실패나 만료가 확실할 때만 예약 재고를 돌려놓습니다.
8. 환불도 PG 취소 성공을 확인한 뒤 주문 취소와 재고 복원을 처리합니다.
9. 나중에 PG를 바꿔도 PG 연결 adapter만 바꾸고 주문·재고·결제 안전장치는 그대로 사용합니다.
