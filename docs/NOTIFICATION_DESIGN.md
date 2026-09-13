# Gift Market Notification 1단계 설계

## 범위

- BUYER, SELLER, ADMIN context별 알림 목록과 읽음 상태를 관리한다.
- 1단계는 조회, unread count, 개별 읽음, 전체 읽음 API만 제공한다.
- 기존 주문, 배송, 취소, 반품, 교환, 문의, 판매자 신청 서비스와의 생성 연결은 2단계에서 진행한다.
- polling, SSE, WebSocket은 1단계 범위에 포함하지 않는다.

## ERD

```text
User 1 ── N Notification

Notification
- id: BIGINT PK
- user_id: BIGINT NOT NULL FK -> users.id
- context: VARCHAR(20) NOT NULL
- type: VARCHAR(50) NOT NULL
- title: VARCHAR(100) NOT NULL
- message: VARCHAR(500) NOT NULL
- target_url: VARCHAR(500) NULL
- read_at: DATETIME(6) NULL
- created_at: DATETIME(6) NOT NULL
- updated_at: DATETIME(6) NOT NULL
```

사용 인덱스:

- `(user_id, context, created_at)`
- `(user_id, context, read_at)`

## Context / Type

### BUYER

- `ORDER_SHIPPED`
- `ORDER_CANCELLED_BY_SELLER`
- `CANCELLATION_COMPLETED`
- `RETURN_APPROVED`
- `RETURN_REJECTED`
- `RETURN_COMPLETED`
- `EXCHANGE_APPROVED`
- `EXCHANGE_REJECTED`
- `EXCHANGE_RESHIPPED`
- `EXCHANGE_COMPLETED`
- `PRODUCT_INQUIRY_ANSWERED`

### SELLER

- `NEW_ORDER`
- `CANCELLATION_REQUESTED`
- `RETURN_REQUESTED`
- `EXCHANGE_REQUESTED`
- `PRODUCT_INQUIRY_CREATED`

### ADMIN

- `SELLER_APPLICATION_CREATED`

`ORDER_PAID`는 1단계 type에 포함하지 않는다.

## API

| Context | Method | Path | 기능 |
| --- | --- | --- | --- |
| BUYER | GET | `/api/notifications` | 최신순 목록 |
| BUYER | GET | `/api/notifications/unread-count` | 안 읽은 개수 |
| BUYER | PATCH | `/api/notifications/{notificationId}/read` | 개별 읽음 |
| BUYER | PATCH | `/api/notifications/read-all` | 전체 읽음 |
| SELLER | GET | `/api/seller/notifications` | 최신순 목록 |
| SELLER | GET | `/api/seller/notifications/unread-count` | 안 읽은 개수 |
| SELLER | PATCH | `/api/seller/notifications/{notificationId}/read` | 개별 읽음 |
| SELLER | PATCH | `/api/seller/notifications/read-all` | 전체 읽음 |
| ADMIN | GET | `/api/admin/notifications` | 최신순 목록 |
| ADMIN | GET | `/api/admin/notifications/unread-count` | 안 읽은 개수 |
| ADMIN | PATCH | `/api/admin/notifications/{notificationId}/read` | 개별 읽음 |
| ADMIN | PATCH | `/api/admin/notifications/read-all` | 전체 읽음 |

목록은 0-based `page`, `size` pagination을 사용하고 `createdAt DESC, id DESC`로 정렬한다. 기본 `size`는 20이고 최대 100이다.

## 권한 / 소유권

- BUYER API는 인증 사용자의 BUYER context만 조회한다.
- SELLER API는 인증 후 사용자에게 속한 ACTIVE Seller row를 Service에서 확인한다.
- ADMIN API는 Security의 `ROLE_ADMIN`과 Service의 ADMIN role을 모두 확인한다.
- 개별 읽음은 `notificationId + userId + context`를 동시에 조건으로 조회한다.
- 다른 사용자나 다른 context의 알림은 존재 여부를 노출하지 않고 동일하게 404로 처리한다.

## DB 적용

production은 `ddl-auto=validate`이므로 Notification 코드 배포 전 `docs/sql/notifications.sql`을 수동 적용한다. 이 SQL은 자동 migration이 아니다.

## 2단계 도메인 연결

- 구매자의 상품문의 생성이 커밋되면 상품 판매자에게 `SELLER / PRODUCT_INQUIRY_CREATED` 알림을 생성한다. 이동 경로는 `/seller/inquiries/{inquiryId}`이다.
- 판매자가 최초 답변을 등록하고 커밋되면 문의 작성자에게 `BUYER / PRODUCT_INQUIRY_ANSWERED` 알림을 생성한다. 이동 경로는 `/products/{productId}#product-inquiries`이다. 기존 답변 수정 시에는 추가 알림을 생성하지 않는다.
- 일반 회원의 판매자 신청이 커밋되면 `ACTIVE` 상태인 모든 ADMIN 사용자에게 `ADMIN / SELLER_APPLICATION_CREATED` 알림을 생성한다. 이동 경로는 `/admin/seller-applications`이다. ADMIN 자기 신청의 즉시 승인 흐름에는 검토 알림을 생성하지 않는다.
- 비즈니스 트랜잭션에서는 최소 값만 담은 이벤트를 발행하고, 알림 리스너는 `AFTER_COMMIT`에 실행한다. 알림 저장은 `REQUIRES_NEW` 트랜잭션으로 분리하며, 실패 시 원 비즈니스 결과에 영향을 주지 않고 오류 로그를 남긴다.
- 2A에서는 상품문의와 판매자 신청, 2B에서는 주문·배송·취소, 2C에서는 반품·교환 알림 연결을 추가했다. 실시간 전송은 아직 포함하지 않는다.

### 2B 주문·배송·취소 연결

- 결제가 최초로 `PAID` 확정되면 각 `SellerOrder`의 판매자에게 `SELLER / NEW_ORDER` 알림을 생성한다. 이동 경로는 `/seller/orders/{sellerOrderId}`이다.
- 최초 outbound 배송이 `SHIPPED`로 전이되면 구매자에게 `BUYER / ORDER_SHIPPED` 알림을 생성한다. 이동 경로는 `/my/orders/{orderId}`이다.
- 구매자의 신규 취소 요청 중 판매자 승인이 필요한 요청만 판매자에게 `SELLER / CANCELLATION_REQUESTED` 알림을 생성한다. 이동 경로는 `/seller/orders/cancellations/{cancellationId}`이다.
- 취소가 최초 `COMPLETED`로 전이되면 requester가 BUYER인 경우 `BUYER / CANCELLATION_COMPLETED`, SELLER인 경우 `BUYER / ORDER_CANCELLED_BY_SELLER` 알림을 생성한다. 이동 경로는 `/my/orders/{orderId}`이다.
- 결제·취소의 기존 상태 잠금과 멱등 조기 반환을 중복 방지 장벽으로 사용한다. webhook, reconciliation, clientRequestKey 재요청 및 완료 재호출에서 이미 최종 상태이면 이벤트를 다시 발행하지 않는다.

### 2C 반품·교환 연결

- 신규 반품 요청이 커밋되면 판매자에게 `SELLER / RETURN_REQUESTED` 알림을 생성한다. 이동 경로는 `/seller/orders/returns/{returnRequestId}`이다.
- 반품이 최초 `APPROVED`, `REJECTED`, `COMPLETED`로 전이되고 커밋되면 구매자에게 각각 `BUYER / RETURN_APPROVED`, `RETURN_REJECTED`, `RETURN_COMPLETED` 알림을 생성한다. 이동 경로는 `/my/orders/{orderId}`이다.
- 신규 교환 요청이 커밋되면 판매자에게 `SELLER / EXCHANGE_REQUESTED` 알림을 생성한다. 이동 경로는 `/seller/orders/exchanges/{exchangeRequestId}`이다.
- 교환이 최초 승인 또는 거절되면 구매자에게 각각 `BUYER / EXCHANGE_APPROVED`, `EXCHANGE_REJECTED` 알림을 생성한다.
- 교환 outbound Shipment가 생성되고 요청이 최초 `RESHIPPING`으로 전이되면 `BUYER / EXCHANGE_RESHIPPED`, 재배송 완료 후 요청이 최초 `COMPLETED`로 전이되면 `BUYER / EXCHANGE_COMPLETED` 알림을 생성한다. 구매자 이동 경로는 `/my/orders/{orderId}`이다.
- 요청 생성의 clientRequestKey 멱등 반환과 각 상태 전이의 선행 상태 검증 및 완료 조기 반환을 중복 방지 장벽으로 그대로 사용한다.
