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
