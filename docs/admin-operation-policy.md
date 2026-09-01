# Admin 운영 권한 정책

## 1. 목적

Gift Market의 Admin 조회 기능 이후 실제 서비스 운영에 필요한 관리자 권한과 제재 범위를 정의한다.

관리자 기능은 다음 두 영역을 구분한다.

1. 계정·판매·노출 제어: 회원 이용 정지, 판매자 판매 정지, 상품 관리자 강제 숨김
2. 금전·주문·물류 상태 개입: 주문 강제 취소, 환불, 반품·교환 중재, 결제·재고 직접 변경

1차 운영 기능은 상대적으로 안전한 계정·판매·노출 제어부터 구현한다. 주문·결제·클레임 workflow에 직접 개입하는 기능은 기존 정합성 로직을 우회할 위험이 있으므로 별도 정책과 command workflow를 설계한 뒤 확장한다.

---

## 2. 1차 운영 기능 범위

확정된 구현 순서는 다음과 같다.

1. `AdminActionLog`
2. User 정지/해제
3. Product 관리자 강제 숨김/해제
4. Seller `SALES_SUSPENDED` 판매 정지/해제
5. Admin 운영 기능 최종 회귀 검증

다음 기능은 1차 범위에서 제외한다.

- Seller `SUSPENDED` 계정 강제 정지
- Admin 강제 주문 취소·환불
- Admin 반품·교환 중재 및 강제 처리
- 결제 상태 직접 변경
- 임의 재고 직접 수정
- 관리자 강제 회원 탈퇴·복구

---

## 3. 최종 상태 정책 표

### User

| 상태 | 표시 | 의미 |
| --- | --- | --- |
| `ACTIVE` | 정상 | 정상 회원 |
| `SUSPENDED` | 이용 정지 | 로그인 및 인증이 필요한 서비스 이용 차단 |
| `WITHDRAWN` | 탈퇴 | 탈퇴 회원 |

### Seller

| 상태 | 표시 | Seller Center | 신규 판매 | 기존 거래 처리 |
| --- | --- | ---: | ---: | ---: |
| `ACTIVE` | 정상 | O | O | O |
| `SALES_SUSPENDED` | 판매 정지 | O | X | O |
| `SUSPENDED` | 계정 정지 | X | X | X |
| `WITHDRAWN` | 탈퇴 | X | X | 별도 운영 정책 |

### Product

Product는 단일 status만으로 관리자 제재까지 표현하지 않는다. 판매자가 관리하는 상품 상태와 관리자가 부여하는 제재 상태를 분리한다.

- `DRAFT`: 임시저장
- `ON_SALE`: 판매중
- `SOLD_OUT`: 품절
- `HIDDEN`: 판매자 판매중지
- `adminHidden = true`: 관리자 판매중지
- `deletedAt != null`: 삭제

---

## 4. User 정지/해제 정책

기존 `UserStatus` 구조를 유지한다.

### 상태 의미

#### `ACTIVE`

- 정상 회원
- 로그인 및 인증이 필요한 서비스 이용 가능

#### `SUSPENDED`

- 관리자에 의한 이용 정지
- 로그인 차단
- 기존 Access Token을 포함하여 인증이 필요한 서비스 이용 차단
- Refresh Token 재발급 및 OAuth 재로그인도 차단

#### `WITHDRAWN`

- 탈퇴 회원
- 관리자가 임의로 `ACTIVE` 상태로 복구하지 않는다.

### 허용되는 Admin 상태 전이

```text
ACTIVE -> SUSPENDED
SUSPENDED -> ACTIVE
```

### 안전장치

- Admin 자기 자신 정지 금지
- 다른 `ADMIN` 계정 정지 금지
- `WITHDRAWN` 회원 정지·활성화 및 복구 금지
- 기존 주문·취소·반품·교환·리뷰·문의 이력은 보존
- 상태 변경과 `AdminActionLog` 기록은 같은 transaction에서 처리

---

## 5. Seller 상태 정책

Seller 상태는 다음 네 가지 의미를 명확히 구분한다.

### `ACTIVE`

정상 판매자다.

- Seller Center 접근 O
- 상품 등록·수정 O
- 상품 판매 상태 변경 O
- 신규 판매 O
- 기존 주문·배송 처리 O
- 기존 취소·반품·교환 처리 O

### `SALES_SUSPENDED`

관리자에 의한 일반적인 판매 정지 상태다. 1차 Admin 판매 정지 기능은 이 상태를 사용한다.

- Seller Center 접근 O
- 신규 영업 X
- Buyer 상품 노출 X
- 신규 주문 X
- 상품 등록 X
- 상품 수정 X
- 상품 판매 재개 등 신규 영업 행위 X
- 기존 주문 처리 O
- 기존 배송 처리 O
- 기존 취소 처리 O
- 기존 반품 처리 O
- 기존 교환 처리 O
- 기존 고객 응대에 필요한 기능은 허용하는 방향

허용되는 Admin 상태 전이는 다음과 같다.

```text
ACTIVE -> SALES_SUSPENDED
SALES_SUSPENDED -> ACTIVE
```

판매 정지 시 목록·검색만 차단해서는 안 된다. Buyer 상품 상세, 장바구니 담기, 바로구매, 주문 생성 단계의 서버 검증에서도 신규 구매를 차단해야 한다.

Seller Center는 `SALES_SUSPENDED` 판매자에게 열어 두되, 기존 주문·배송·취소·반품·교환 및 필요한 고객 응대 화면만 사용할 수 있도록 제한한다.

### `SUSPENDED`

판매자 계정 자체의 강한 정지 상태다. `SALES_SUSPENDED`와 구분한다.

- Seller Center 접근 X
- 신규 판매 X
- 판매자 업무 전체 X
- 기존 주문·배송 처리 X
- 기존 취소·반품·교환 처리 X

사기, 보안 사고, 심각한 운영 위반 등 강한 제재에 사용할 수 있다. 다만 1차 Admin의 일반 판매 정지 버튼은 이 상태를 사용하지 않는다.

향후 `SUSPENDED`를 실제 강제 제재 기능으로 사용하려면 판매자가 처리하지 못하는 미처리 주문·배송·취소·반품·교환을 Admin이 중재하거나 강제 처리하는 정책을 함께 마련해야 한다.

### `WITHDRAWN`

판매자 탈퇴 또는 영업 종료 상태다.

- Seller Center 접근 X
- 신규 판매 X

현재 프로젝트에는 `SellerStatus.WITHDRAWN`과 관련 도메인 구조가 존재하지만 실제 판매자 탈퇴 프로세스와 API는 구현되어 있지 않다.

판매자 탈퇴는 향후 별도 기능으로 구현한다. 기존 주문·취소·반품·교환이 남아 있을 때의 탈퇴 가능 조건과 잔여 업무 처리 정책도 그때 함께 설계한다.

---

## 6. Product 관리자 강제 숨김/해제 정책

기존 `ProductStatus`는 유지한다.

- `DRAFT`
- `ON_SALE`
- `SOLD_OUT`
- `HIDDEN`

`ProductStatus.HIDDEN`은 판매자가 직접 설정하고 해제할 수 있는 판매자 상품 상태다. 판매자는 `HIDDEN` 상품을 다시 `ON_SALE`로 변경할 수 있으므로 Admin 제재에 `HIDDEN`을 재사용하지 않는다.

관리자 상품 제재는 별도 속성으로 관리한다.

```text
adminHidden
adminHiddenReason
adminHiddenAt
```

### 의미

`adminHidden = true`이면 기존 `ProductStatus`와 관계없이 다음을 차단한다.

- Buyer 상품 목록·검색 노출
- Buyer 상품 상세 접근
- 장바구니 신규 추가
- 바로구매
- 장바구니 기반 신규 주문

### 관리자 숨김 해제

Admin 숨김 해제는 다음 제재 정보만 정리한다.

- `adminHidden = false`
- 관리자 숨김 사유 정리
- 관리자 숨김 시각 정리

기존 `ProductStatus`를 강제로 `ON_SALE`로 변경하지 않는다.

예를 들어 판매자가 숨긴 상품에 관리자 제재까지 적용된 경우, 관리자 제재를 해제해도 판매자의 `ProductStatus.HIDDEN`은 유지된다.

책임은 다음과 같이 분리한다.

```text
ProductStatus.HIDDEN = 판매자가 판매 중지
adminHidden = true    = 관리자가 강제 판매 중지
```

상태 변경과 `AdminActionLog` 기록은 같은 transaction에서 처리한다.

---

## 7. Buyer 노출 및 신규 구매 기준

신규 판매가 가능한 기본 조건은 다음과 같다.

```text
Product.status == ON_SALE
AND Product.deletedAt IS NULL
AND Product.adminHidden == false
AND Seller.status == ACTIVE
```

`Seller.status == SALES_SUSPENDED`이면 해당 Seller 상품은 Buyer 영역에서 노출하거나 신규 주문할 수 없다.

이 조건은 다음 단계에 일관되게 적용한다.

- 상품 목록
- 검색
- 상품 상세
- 장바구니 담기
- 바로구매
- 주문 생성 직전 서버 validation

Frontend 노출 제어만 신뢰하지 않으며 Backend를 최종 권위로 둔다.

---

## 8. AdminActionLog

Admin write 기능은 관리자 행동 이력을 남긴다.

최소 구조:

```text
AdminActionLog

id
adminUserId
actionType
targetType
targetId
reason
createdAt
```

ActionType 예시:

```text
USER_SUSPENDED
USER_REACTIVATED
SELLER_SALES_SUSPENDED
SELLER_SALES_REACTIVATED
PRODUCT_ADMIN_HIDDEN
PRODUCT_ADMIN_UNHIDDEN
```

목적:

- 처리 관리자 식별
- 처리 시각 확인
- 처리 사유 확인
- CS 및 분쟁 대응
- 관리자 오조작 추적
- 운영 장애 분석

가능한 상태 변경 API에는 사유 입력을 요구한다. 상태 변경과 이력 저장은 동일 transaction에서 성공하거나 함께 rollback되어야 한다.

---

## 9. Admin 주문·결제·클레임 개입 정책

현재 Admin 취소·반품·교환 기능은 조회 전용으로 유지한다.

다음과 같은 단순 상태 변경 API는 만들지 않는다.

```text
PATCH /api/admin/returns/{returnId}/status
status=COMPLETED
```

이 방식은 Payment, PaymentCancellation, Order, SellerOrder, OrderItem, Shipment, Product/Variant stock 및 각종 처리 수량을 불일치 상태로 만들 수 있다.

향후 Admin 중재가 필요하면 단순 상태 변경이 아닌 운영 행위를 표현하는 command API로 설계하고 기존 도메인 workflow와 정합성 primitive를 재사용한다.

```text
POST /api/admin/returns/{returnId}/interventions/approve
POST /api/admin/returns/{returnId}/interventions/refund
```

Admin이라는 이유로 주문·결제·클레임 invariant를 우회하지 않는다.

---

## 10. 향후 확장 대상

1차 운영 기능 완료 후 실제 운영 필요성을 확인하여 다음을 별도 설계한다.

- Seller `SUSPENDED` 계정 강제 정지
- 정지 Seller의 미처리 주문·클레임에 대한 Admin 중재
- Admin 주문 강제 취소
- Admin 환불 command
- Admin 반품·교환 중재
- 리뷰·문의 관리자 숨김
- 회원 구매 제한
- 판매자 정산 보류
- Inventory Adjustment와 변경 이력
- 판매자 탈퇴 및 잔여 거래 처리

결제 상태 직접 변경, 임의 재고 수정, workflow를 우회하는 클레임 상태 변경은 계속 금지한다.

---

## 11. 최종 개발 방향

현재 Admin은 회원, 판매자, 상품, 주문, 취소, 반품, 교환 조회와 판매자 신청 승인·거절 기능을 제공한다.

다음 개발 범위는 아래 순서로 제한한다.

```text
AdminActionLog
-> User 정지/해제
-> Product 관리자 강제 숨김/해제
-> Seller SALES_SUSPENDED 판매 정지/해제
-> Admin 운영 기능 최종 회귀 검증
```

Seller `SUSPENDED` 강제 정지와 Admin 취소·환불·반품·교환 중재는 1차 범위에 포함하지 않는다. 실제 CS·분쟁 요구사항과 미처리 거래 대행 정책이 확정된 후 기존 workflow를 재사용하는 별도 command 구조로 확장한다.
