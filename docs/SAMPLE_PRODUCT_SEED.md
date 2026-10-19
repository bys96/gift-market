# 정식 상품 50개 일괄 등록

## 목적과 안전 정책

`GM-SAMPLE-50-V1` 데이터셋의 상품 50개를 지정한 활성 판매자 한 명의 일반 상품으로 등록한다. 애플리케이션 시작 시 자동 실행되지 않으며, `seedSampleProducts` Gradle 명령과 명시적인 `sellerId`, 이미지 manifest가 있어야만 실행된다.

- 기존 상품, 카테고리, 판매자, 선물 상황은 수정하거나 삭제하지 않는다.
- 고정 PK를 가정하지 않는다. 활성 leaf 카테고리 전체 경로와 활성 선물 상황 이름으로 조회한다.
- 각 상품은 기존 `ProductRegistrationService`를 거쳐 기본 정보, 이미지, 선물 상황, 옵션, Variant를 저장하고 마지막에 `ON_SALE`로 전환한다.
- 기존 상품 등록 Validation이나 주문·결제 검증을 우회하지 않는다.
- `GM-SAMPLE-50-V1-{001..050}-{A|B}` SKU 100개를 시드 식별자로 사용한다.
- 정상적으로 100개 SKU와 50개 상품이 이미 존재하면 전체를 skip한다. 일부만 존재하거나 충돌하면 기존 데이터를 건드리지 않고 중단한다.
- 50건은 한 트랜잭션에서 처리하므로 중간 오류가 발생하면 전체 등록이 롤백된다.
- `prod` 또는 `production` profile에서는 DB 조회 전에 실행을 차단한다.

## 사전 조건

### 판매자

`sellerId`에 해당하는 Seller가 존재하고 `ACTIVE` 상태여야 한다. 50개 상품은 모두 이 Seller의 사용자 계정으로 기존 상품 등록 서비스를 호출해 등록한다.

### 카테고리와 선물 상황

데이터셋에 지정된 활성 leaf 카테고리 경로가 모두 존재해야 한다. 상품과 일치하지 않는 다른 카테고리로 대체하지 않는다. 다음 활성 선물 상황도 모두 필요하다.

```text
생일, 감사, 축하, 응원, 집들이, 기념일, 출산·육아, 가벼운 선물
```

상품별로 미리 정의한 관련 선물 상황 1~3개만 연결한다. 누락된 마스터 데이터가 있으면 사용 가능한 값과 누락 값을 로그에 남기고 중단한다.

## 이미지 준비와 manifest

명령은 이미지를 업로드하지 않는다. 기존 판매자 이미지 업로드 API로 사용 권한이 확보된 이미지를 먼저 업로드하고, 응답의 실제 object key를 manifest에 기록한다.

- 대표 이미지: `PRODUCT_REPRESENTATIVE`
- 갤러리 이미지: `PRODUCT_GALLERY`
- 대표 이미지 권장 규격: 1:1, 1600×1600px 이상, WebP(권장) 또는 JPEG, sRGB

키 구조는 기존 Storage API와 같아야 한다.

```text
products/{sellerId}/representative/{uuid}.{extension}
products/{sellerId}/gallery/{uuid}.{extension}
```

manifest 예시:

```json
{
  "products": {
    "GM-SAMPLE-50-V1-001": {
      "representativeImageKey": "products/15/representative/actual-upload-uuid.webp",
      "galleryImageKeys": [
        "products/15/gallery/actual-upload-uuid.webp"
      ]
    }
  }
}
```

실제 파일에는 `001`부터 `050`까지 정확히 50개 항목이 필요하다. 상품마다 대표 이미지 1개가 필수이고 갤러리는 0~10개다. 실행 전 다음을 모두 검사한다.

- 상품 코드 누락·초과 여부
- 모든 key가 지정 Seller의 대표/갤러리 prefix에 속하는지
- object key 중복 여부
- S3/MinIO에 각 object가 실제 존재하는지

manifest가 없거나 이미지가 하나라도 유효하지 않으면 dry-run을 포함해 등록을 중단하며 DB를 변경하지 않는다. 존재하지 않는 임의 key는 저장하지 않는다.

## 재고, 상태와 고객 노출

각 상품에 옵션 값 2개와 활성 Variant 2개를 생성한다. Variant마다 양수 재고를 설정하고, 기존 `ProductVariantService`가 합계를 Product 재고로 동기화한다. 상품별 총 재고는 40개 이상이다.

이미지, 옵션, Variant, 선물 상황 저장이 모두 성공하면 기존 상품 판매 시작 로직으로 `ON_SALE` 전환한다. 따라서 메인·검색·카테고리·상세 화면에서 일반 상품과 동일하게 노출되고 별도의 샘플/테스트 표시는 없다.

이 상품에는 별도의 구매 차단 장치가 없다. `ON_SALE` 상태와 양수 재고이므로 기존 주문·결제 흐름으로 실제 구매가 가능하다. 실행 전에 반드시 실제 공급 가능 수량, 가격, 배송비, 출고일, 반품·교환 배송비, 판매자 정산·CS 책임을 확정해야 한다.

## 실행 방법

Backend 디렉터리에서 실행한다.

```powershell
cd giftmarket-api
```

먼저 manifest와 DB 사전 조건만 검증한다. dry-run은 DB를 변경하지 않는다.

```powershell
.\gradlew seedSampleProducts -PsellerId=15 -PimageManifest="C:\images\gift-market-products.json" -PdryRun=true --no-daemon
```

로그에서 `planned=50`, `created=0`, `imagesAttached=50`을 확인한 뒤 실제 등록한다.

```powershell
.\gradlew seedSampleProducts -PsellerId=15 -PimageManifest="C:\images\gift-market-products.json" -PdryRun=false --no-daemon
```

성공 시 `created=50`이다. 같은 명령을 다시 실행하면 `created=0`, `skipped=50`이어야 한다.

## 운영 DB 반영 전 절차

현재 코드는 `prod`/`production` profile 실행을 의도적으로 차단한다. 운영 반영 시 임의로 profile 이름을 바꾸거나 차단을 우회하지 않는다. 별도 변경 리뷰를 통해 다음 절차를 준비한 뒤 차단 정책 변경을 배포해야 한다.

1. 운영 DB 백업과 복구 절차를 확인한다.
2. 대상 Seller ID와 `ACTIVE` 상태, 소유 사용자, 정산·배송·CS 준비 상태를 확인한다.
3. 운영 카테고리와 선물 상황 마스터가 데이터셋과 정확히 일치하는지 확인한다.
4. 운영 S3에 권리 확보 이미지 50개 이상을 정상 업로드하고 Seller prefix와 object 존재 여부를 확인한다.
5. 운영과 같은 데이터 사본 또는 staging에서 dry-run과 실제 50건 등록, 재실행 skip을 검증한다.
6. 가격, 옵션 추가금, Variant별 실재고, 배송·반품·교환 비용을 판매 책임자가 승인한다.
7. 제한된 실행 주체와 변경 시간대를 정하고 로그·모니터링 및 실패 시 대응 담당자를 확보한다.
8. 운영 차단을 해제하는 별도 코드 변경을 리뷰·배포한 뒤 운영 dry-run을 먼저 수행한다.
9. 실제 실행 직후 상품 수, Seller 소유권, `ON_SALE`, 이미지, 재고, 검색·카테고리 노출을 확인한다.

이미 같은 `GM-SAMPLE-50-V1` 시드가 존재하면 이 기능은 기존 상품을 보정하거나 덮어쓰지 않고 skip한다. 데이터 변경이 필요하면 일반 판매자 상품 관리 기능을 사용한다.

## 검증 명령

```powershell
.\gradlew compileJava --no-daemon
.\gradlew test --tests "com.giftmarket.product.seed.*" --no-daemon
.\gradlew test --tests "com.giftmarket.product.service.ProductStockIntegrationTest" --no-daemon
.\gradlew test --no-daemon
```

운영 DB와 운영 S3에서는 이 문서 작성 및 로컬 검증 과정의 명령을 실행하지 않는다.
