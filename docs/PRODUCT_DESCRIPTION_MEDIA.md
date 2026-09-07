# 상품 상세 콘텐츠 미디어

> 기준: 현재 StorageProvider/S3/MinIO, Product description LONGTEXT 및 Tiptap 3 구조.
> 운영 DB migration과 S3 object 복사/삭제는 이 작업에서 실행하지 않는다.

## 기존 이미지 문제와 확인 범위

기존 ProductEditor는 `uploadContentImage()`에서 받은 object key에 `resolveImageUrl()`을 적용해 절대 URL을 만든 뒤 Tiptap `img.src`에 넣었다. `editor.getHTML()`이 상품 description에 저장되고, 구매자와 판매자 상품 상세 화면은 해당 HTML을 그대로 출력했다. 대표/갤러리 이미지의 별도 key 저장 방식과 달랐다.

따라서 `http://localhost:9000/gift-market/...`가 저장된 과거 상세 이미지는 운영에서도 사용자 기기의 localhost를 요청한다. Storage base URL을 S3로 변경하는 것만으로 DB의 절대 URL이 갱신되지는 않는다. 실제 production DB 내용, S3 파일 존재 및 이전 완료 여부는 이 저장소 코드만으로 확정할 수 없다.

현재 key 생성은 StorageService가 공통 담당하고 S3StorageProvider/MinioStorageProvider 모두 전달받은 key를 그대로 사용한다. 상세 이미지는 `products/{sellerId}/content/{uuid}.{ext}`다. 같은 key로 MinIO 파일을 S3에 복사했다면 호환된다. Provider 선택을 바꾸는 기능은 파일 이전 기능이 아니며, 자동 MinIO → S3 복사는 구현되어 있지 않다.

## 신규 저장 / 표시

```html
<img data-storage-key="products/12/content/12345678-1234-1234-1234-123456789abc.png" alt="상품 안내">
<video data-storage-key="products/12/content/video/12345678-1234-1234-1234-123456789abc.mp4" controls preload="metadata"></video>
```

- description LONGTEXT와 Product request/response 구조를 유지한다. 새 DB 컬럼/테이블은 없다.
- 직접 업로드한 상세 미디어는 HTML의 `data-storage-key`에 key만 저장한다. 영구 저장 HTML에 현재 S3 domain이나 presigned URL을 넣지 않는다.
- Tiptap Image 확장과 custom `productVideo` Node는 저장 HTML과 에디터 표시 DOM을 분리한다. NodeView가 현재 Storage URL을 `src`로 적용한다.
- 구매자/판매자 상세 화면은 Backend에서 sanitize된 HTML의 미디어 key만 검사해 `resolveImageUrl()`로 `src`를 적용한다. HTML 전체에 대한 URL 문자열 치환은 하지 않는다.
- `NEXT_PUBLIC_STORAGE_BASE_URL`은 bucket에 해당하는 공개 base URL이다. CDN/custom domain으로 전환 시 같은 object key를 제공하고 환경변수 변경 후 Frontend를 재배포한다. 신규 key 기반 description은 DB UPDATE가 필요 없다.
- 기존 http/https 절대 URL은 그대로 유지한다. 기존 데이터는 아래 절차로 필요한 항목만 변환한다.
- 잘못된 media key는 Backend에서 거부하며 path traversal·외부 URL을 key로 받지 않는다. Frontend에서도 key 형식을 검사한다.

## 업로드 정책

| StorageType | 최대 크기 | 형식 / key |
| --- | --- | --- |
| PRODUCT_REPRESENTATIVE | 20MB | 기존 JPG/JPEG, PNG, WEBP, GIF / 기존 representative key |
| PRODUCT_GALLERY | 20MB | 위와 동일 / 기존 gallery key, 최대 10장 유지 |
| PRODUCT_CONTENT | 20MB | 위와 동일 / products/{sellerId}/content/{uuid}.{ext}, 한 번에 최대 20장 유지 |
| PRODUCT_CONTENT_VIDEO | 50MB | MIME video/mp4 + 확장자 mp4 / products/{sellerId}/content/video/{uuid}.mp4 |
| PROFILE, REVIEW, RETURN_EVIDENCE, EXCHANGE_EVIDENCE, BANNER | 기존 5MB | 기존 타입별 형식 제한 유지 |

크기는 1MB = 1024 × 1024 bytes 기준이다. 동영상은 description당 최대 3개이며 프론트 삽입/붙여넣기 transaction과 Backend sanitizer가 모두 제한한다. 동영상만 있는 description도 콘텐츠로 유지한다.

상품 미디어 presign은 활성 Seller가 필요하며 userId가 아니라 sellerId를 key에 사용한다. Presigned URL 유효기간 300초와 직접 PUT 구조를 유지한다. Spring에는 파일 본문을 전송하지 않는다.

```text
파일 선택 → Frontend 형식/크기/영상 개수 검사
→ /api/storage/presigned-url → Storage Provider의 presigned PUT URL
→ 브라우저에서 S3/MinIO direct PUT → 에디터에 key 삽입
→ 상품 저장 → Backend sanitizer → description HTML 저장
→ 구매자/판매자 상세에서 현재 Storage URL로 렌더링
```

에디터 업로드 중 중복 업로드와 상품 저장/임시저장/초안 불러오기를 막는다. 오류는 기존 에디터 오류 영역에 표시한다.

## HTML 보안 / 재생

- video는 src, data-storage-key, controls, preload만 허용한다. src protocol은 http/https만 허용한다.
- controls는 강제하고 preload는 metadata로 고정한다. autoplay/loop/muted/event handler/script는 저장되지 않는다.
- 기존 img/link/table/text formatting은 유지하며 style은 에디터에서 사용하는 안전한 text-align 값만 남긴다. 임의 CSS나 URL을 포함하는 style은 제거한다.
- key가 있으면 서버는 src를 제거해 Storage domain이 영구 저장되지 않게 한다.
- 구매자·판매자 미리보기·에디터 video는 width/max-width 100%, height auto로 표시한다.

## 과거 description migration

1. production DB 백업을 확보하고 작업 중 상품 수정과 충돌하지 않는 시간을 정한다.
2. `docs/sql/product-description-media-key-migration.sql`의 SELECT로 실제 legacy src와 상품 ID를 확인한다. 예상과 다른 domain/HTML/key 구조는 일괄 치환하지 않는다.
3. old URL의 bucket 이후 key가 `products/{sellerId}/content/{uuid}.{ext}`인지 확인한다. 다른 legacy key 구조라면 이 SQL의 적용 대상이 아니다.
4. 운영자가 해당 key의 원본 MinIO 파일과 대상 S3 파일을 확인한다. 대상 S3 파일이 없다면 **먼저 같은 key로 복사**해야 한다. URL/HTML 수정만으로 유실된 파일을 복원할 수 없다.
5. 현재 Storage base URL + key로 이미지가 조회되는지 확인한다. bucket public read 정책, CORS와 MIME도 확인한다.
6. 새 Backend/Frontend를 배포한 뒤 검토한 상품 ID와 key를 SQL 변수에 넣고 before/after HTML을 비교한다. 코드 변경과 데이터 변환의 배포 순서를 뒤집으면 기존 sanitizer가 key를 제거할 수 있다.
7. SQL은 한 상품의 정확히 일치하는 src attribute만 data-storage-key로 바꾼다. version 증가와 행 잠금으로 동시 수정 충돌을 고려하고 기본 종료는 ROLLBACK이다. 확인 후 명시적으로 COMMIT할 때만 반영한다.
8. 구매자 상세·판매자 미리보기·편집 재저장을 확인한다. 이미 key 기반인 데이터는 재변환하지 않는다.

예전 S3 절대 URL도 같은 방식으로 확인한 정확한 src만 key로 바꿀 수 있다. 원본 URL이 signed query를 포함하거나 작은따옴표/다른 마크업을 사용하면 위 SQL을 그대로 적용하지 않고 HTML 파싱 기반의 별도 검토가 필요하다. `product_drafts`의 JSON 초안은 이 SQL의 대상이 아니며, 오래된 초안을 다시 불러오면 legacy URL이 재유입될 수 있으므로 재저장 전 확인한다.

## 운영 확인 / 후속 범위

- 실제 S3 CORS PUT/GET 및 video/mp4 Content-Type, Range 요청(206)/탐색 재생, 모바일 Safari/Chrome/Samsung Internet의 MP4 codec 호환성을 확인한다. mp4 컨테이너만으로 모든 codec의 재생을 보장하지 않는다.
- presign API가 타입별 최대 크기를 검증한 뒤 S3/MinIO Provider가 그 파일의 정확한 `Content-Length`와 정규화한 `Content-Type`을 PUT 서명에 포함한다. 실제 요청의 크기/MIME 헤더가 다르면 서명이 유효하지 않다. 파일 본문의 형식·codec·magic byte를 검사하는 기능은 아니다.
- 20MB/50MB 업로드 허용은 전송 이미지 최적화·영상 transcoding과 별개다. 리사이징/CDN 변환/영상 최적화는 후속 범위다.
- 상세 이미지와 동영상은 업로드 후 상품 저장 취소·탭 종료·실패 시 orphan object가 남을 수 있다. 기존 코드에 이 상세 미디어의 자동 cleanup이 없어 TODO로 남긴다. 참조 확인 없이 S3 object를 삭제하지 않는다.
- 이번 변경은 실제 production DB/S3를 조회·수정하지 않으므로 파일 이전 및 실제 재생 E2E는 운영자가 확인해야 한다.

## 실제 업로드 byte 제한 보완

기존 Provider는 key와 만료 시간만으로 PUT URL을 발급했다. 따라서 presign 요청의 `fileSize=1MB` 검증만 통과한 후 51MB 영상이나 21MB 상품 이미지를 업로드해도 이 코드에서 생성한 서명에는 이를 거부할 크기 조건이 없었다. 실제 운영 버킷에서 공격 업로드를 실행하지는 않았다.

검토한 방법:

| 방식 | 판단 |
| --- | --- |
| Presigned POST + content-length-range | S3에서 최소/최대 byte 범위를 강제할 수 있다. 다만 응답에 form fields 추가, FormData POST 전환 및 CORS POST 설정이 필요하다. |
| PUT 후 HEAD 검사/삭제 | 저장 후에야 초과를 알 수 있고 검증 호출 생략/검증 후 재업로드도 고려해야 한다. 임시 key 및 공개·참조 승인 절차 없이 도입하면 최종 보장이 부족하다. |
| PUT의 정확한 Content-Length + Content-Type 서명 | 현재 File body PUT과 API 응답을 유지하면서 실제 요청에 제약을 적용할 수 있어 채택했다. 최대 범위 대신 검증된 파일의 정확한 byte 수를 고정한다. |

StorageService는 기존 20MB/50MB/5MB 상한 검증 후 `request.fileSize()`와 검증된 MIME을 Provider에 전달한다. S3 SDK `PutObjectRequest.contentLength/contentType`, MinIO SDK `extraHeaders`로 둘을 서명한다. 설치된 SDK에서 `X-Amz-SignedHeaders=content-length;content-type;host` 및 실제 서명을 단위 테스트한다. S3 Provider는 SDK가 필수 헤더를 서명하지 않으면 URL 발급을 실패시킨다.

예를 들어 1MB로 발급받은 URL은 정확히 1MB 본문에만 사용할 수 있다. 실제 51MB/21MB에 맞춰 Content-Length를 보내면 서명 불일치다. Content-Length를 1MB로 거짓 지정하더라도 HTTP 메시지 경계는 그 길이이므로 초과 byte가 해당 object 본문으로 저장되도록 허용하는 정책이 아니다. 길이가 없는 chunked 업로드로 바꾸면 필수 서명 헤더가 없어 유효하지 않다.

Frontend는 기존 `fetch(url, { method: "PUT", headers: { "Content-Type": file.type }, body: file })`를 유지한다. 브라우저가 File의 실제 크기로 Content-Length를 생성하며 JavaScript에서 이 금지 헤더를 직접 설정하지 않는다. 업로드 전에 파일을 변환했다면 변환된 파일 기준으로 새 URL을 받아야 한다. Spring API 경로/request/response 및 300초 만료는 그대로다.

서명은 MIME 헤더 변조를 막지만 동일 크기의 다른 내용이나 잘못된 파일 형식까지 판별하지 않는다. 별도의 credential/public write 권한으로 업로드하는 경로는 이 URL 정책의 적용 대상이 아니므로 버킷은 익명 쓰기를 허용하면 안 된다. 배포 이전 발급 URL에는 제약이 소급 적용되지 않으며 기존 300초 만료까지 남을 수 있다.

### 배포 전 수동 검증

전용 테스트 key/버킷에서 확인하며 URL·credential을 로그/문서에 기록하지 않는다.

1. metadata 1MB로 받은 영상/상세 이미지 URL에 각각 실제 51MB/21MB File을 PUT: 실패하고 초과 object가 생성되지 않는지 확인한다.
2. 타입별 20MB/50MB/5MB 경계 크기를 정확히 신고해 같은 File을 PUT: 성공 후 HEAD Content-Length가 실제 크기와 같은지 확인한다. 최대값 +1 byte metadata는 API에서 거부되어야 한다.
3. 동일 URL의 Content-Type을 application/octet-stream 등으로 변경: 서명 오류로 실패하는지 확인한다.
4. Content-Length 누락/chunked 요청 및 잘못된 Content-Length 요청: 초과 object가 저장되지 않는지 확인한다. 잘못된 HTTP framing은 서명 오류 대신 HTTP 오류/연결 종료가 될 수도 있다.
5. Chrome/Samsung Internet/모바일 브라우저 및 로컬 MinIO에서 대표·갤러리·상세·프로필·리뷰·클레임 이미지 업로드를 확인한다. 프록시가 요청 body/Content-Length/Content-Type을 바꾸지 않아야 한다. 기존 PUT CORS 설정을 유지한다.

자동 테스트는 실제 SDK가 만든 서명을 독립적인 SigV4 계산으로 검증하여 정상 길이/MIME, 크기 변조, MIME 변조, 길이 누락, 타입별 상한을 확인한다. 실제 AWS 수신/저장 및 브라우저 전송 E2E를 실행했다는 의미는 아니다.

참고: [AWS presigned 요청과 signed headers](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3-presign.html), [S3 Content-Length 정의](https://docs.aws.amazon.com/AmazonS3/latest/API/RESTCommonRequestHeaders.html), [POST content-length-range 정책](https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-HTTPPOSTConstructPolicy.html).
