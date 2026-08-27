import type { Metadata } from "next";

import PolicyPage from "@/components/policy/PolicyPage";

export const metadata: Metadata = {
  title: "개인정보처리방침 | Gift Market",
  description: "Gift Market 외부 테스트 개인정보 처리 안내",
};

export default function PrivacyPage() {
  return (
    <PolicyPage
      eyebrow="PRIVACY"
      title="개인정보처리방침"
      description="테스트 서비스에서 개인정보가 사용되는 범위를 안내합니다."
    >
      <section>
        <h2>처리하는 정보</h2>
        <p>
          소셜 로그인 식별정보, 이름과 프로필 이미지, 배송지와 연락처, 주문·결제·배송
          및 취소·반품·교환 기록, 상품문의와 리뷰 정보를 서비스 제공 과정에서
          처리할 수 있습니다. 결제수단의 상세 정보는 결제 제공사의 화면에서
          처리됩니다.
        </p>
      </section>

      <section>
        <h2>이용 목적</h2>
        <p>
          회원 식별, 상품 주문과 배송, 결제 결과 확인, 취소·환불·반품·교환 처리,
          판매자 기능 제공, 문의와 리뷰 운영, 서비스 오류 확인과 보안을 위해
          필요한 범위에서 이용합니다.
        </p>
      </section>

      <section>
        <h2>외부 서비스 이용</h2>
        <p>
          로그인에는 Google과 Kakao, 결제에는 Toss Payments를 이용합니다. 이미지
          파일은 Gift Market이 사용하는 객체 저장소에 보관될 수 있습니다. 각 외부
          서비스가 직접 처리하는 정보에는 해당 서비스의 정책이 함께 적용됩니다.
        </p>
      </section>

      <section>
        <h2>보관과 삭제</h2>
        <p>
          정보는 서비스 제공과 테스트 운영에 필요한 동안 보관하고, 목적이 끝나면
          관련 법령과 확정된 운영 정책에 따라 삭제하거나 별도로 보관합니다. 테스트
          계정과 데이터 삭제가 필요한 경우 고객지원 안내를 확인해 주세요.
        </p>
      </section>

      <section>
        <h2>이용자의 권리</h2>
        <p>
          회원은 마이페이지에서 프로필과 배송지를 확인·수정할 수 있습니다. 그 밖의
          개인정보 열람, 정정 또는 삭제 요청 접점은 정식 운영 전에 확정될 예정입니다.
        </p>
      </section>

      <aside className="policy-note">
        개인정보 보호책임자, 처리 위탁 세부 내역, 보유기간과 공식 요청 접점은 정식
        운영 전 실제 사업자·인프라 기준으로 확정해야 합니다.
      </aside>
    </PolicyPage>
  );
}
