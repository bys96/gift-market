# Settlement v1 현재 구현 기준

이 문서는 현재 `com.giftmarket.settlement` Entity·Service·Controller와 `docs/sql/settlement-v1-foundation.sql`의 간단한 참조다. 금액·상태 처리의 최종 기준은 코드다. 정산은 판매자 지급/송금 도메인이 아니다.

## 관계와 원장

```text
Seller 1:N Settlement
SellerOrder 1:N SettlementLedgerEntry
Settlement 1:N SettlementLedgerEntry (settlement_id nullable)
```

`SettlementItem`은 없다. 각 원장은 Seller와 SellerOrder에 귀속되고, `sourceType + sourceId + sourceDetailKey`가 경제 이벤트 중복 방지 키다. 금액은 KRW의 signed `Long/BIGINT`다. 생성 후 경제 payload는 수정하지 않고, `eligibleAt` 확정과 미귀속 원장의 Settlement 할당만 허용한다. 이미 확정된 Settlement와 귀속 원장은 수정하지 않는다.

| 원장 type | 부호 | 발생 근거 |
|---|---:|---|
| `SALE_PRODUCT` | + | 결제 성공 시 주문 상품 snapshot |
| `SALE_SHIPPING` | + | 결제 성공 시 주문 배송비 snapshot; 0원 생략 |
| `COMMISSION` | - | 당시 수수료율·상품 매출 기준; 0원 생략 |
| `CANCELLATION_REFUND` | - | 성공한 `PaymentCancellation.amount` |
| `RETURN_REFUND` | - | 완료 반품의 성공한 PG 환불액; 0원 생략 |
| `COMMISSION_REVERSAL` | + | 취소·반품 누적 상품환불액과 최초 수수료율 snapshot |
| `MANUAL_ADJUSTMENT` | +/- | 원장 type은 존재하나 관리자 조정 API는 현재 없음 |

`occurredAt`은 결제/PG 확정 시각을 사용한다. 최초 판매 원장의 `eligibleAt`은 처음에는 null이며 `ORIGINAL_OUTBOUND` 배송완료에서 `SellerOrder.deliveredAt + settlement.hold-days`로 확정한다. 취소·반품 원장은 기존 eligibility service가 배송·환불 완료 시각을 반영한다. `eligibleAt`은 시간 기준일 뿐 claim 부재를 보장하지 않는다.

## 생성·확정

관리자 수동 `generate(sellerId, periodStart, periodEnd, cutoff)`는 해당 Seller의 미귀속 원장 중 `eligibleAt < periodEnd`, `eligibleAt <= cutoff`를 잠금 조회한다. `periodStart`는 원장 조회 하한이 아니므로 과거 회차의 미귀속 원장도 catch-up한다. 활성 취소(`REQUESTED`, `PROCESSING`), 반품(`REQUESTED`부터 `REFUNDING`까지), 교환(`REQUESTED`부터 `RESHIPPING`까지)이 있는 SellerOrder의 미귀속 원장은 전체 제외한다. 종료 후 다음 생성에서 다시 고려할 수 있다.

Seller는 `ACTIVE`·`SALES_SUSPENDED`일 때 생성 가능하다. 대상이 없으면 빈 Settlement를 만들지 않는다. 판매자 행·원장·SellerOrder 잠금과 `(seller_id, period_start, period_end)` unique로 중복 생성을 방어한다. 정산번호는 `ST-YYYYMMDD-`와 UUID를 결합한다. 생성된 READY의 원장 구성은 다음 generate로 변경하지 않는다.

집계는 다음과 같고 `settlementAmount == Σ ledger.amount`를 검증한다. 음수 정산도 허용한다.

```text
상품 매출 + 배송비 매출 - 취소 환불 - 반품 환불 - 순 수수료 + 조정액
순 수수료 = COMMISSION 절댓값 합계 - COMMISSION_REVERSAL 합계
```

상태 전이는 `READY → ON_HOLD → READY`와 `READY → CONFIRMED`만 가능하다. 보류에는 사유와 관리자 감사정보가 남는다. 확정 전에는 귀속 원장의 소유권·건수·각 집계 snapshot·signed 합계를 다시 검증한다. `CONFIRMED`는 최종 상태이자 **정산 금액 확정**이며 지급 완료가 아니다. 사후 환불은 과거 확정분을 수정하지 않고 새 원장으로 다음 회차에 반영한다.

## API·운영 경계

| 주체 | 현재 API |
|---|---|
| 판매자 | `GET /api/seller/settlements` (`status`, `page`, `size`), `/summary`, `/{settlementId}` |
| 관리자 | `GET /api/admin/settlements` (`sellerId`, `status`, `periodStart`, `periodEnd`, `page`, `size`), `/{settlementId}` |
| 관리자 | `POST /api/admin/settlements/generate`, `/{settlementId}/hold`, `/{settlementId}/release`, `/{settlementId}/confirm` |

판매자는 자신의 정산만 조회한다. 관리자 API는 `ADMIN` 전용이며 생성 요청은 판매자·기간·cutoff만 받는다. 판매자/관리자 화면은 API 데이터를 사용한다. 현재 자동 Settlement Scheduler, 실제 Payout, 계좌/KYC, 지급 상태·재시도는 없다. 향후 Scheduler는 기존 `SettlementGenerationService`를 호출해야 하며 수동 generate는 운영 대응 기능으로 남길 수 있다.

운영은 `ddl-auto=validate`이므로 정산 Entity와 DB 스키마를 대조한 뒤 수동 SQL을 먼저 적용한다. [`settlement-v1-foundation.sql`](./sql/settlement-v1-foundation.sql)은 자동 migration이 아니다. 기존 주문의 backfill이나 실제 지급은 이 SQL·정산 생성 API에 포함되지 않는다.
