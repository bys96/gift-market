import type { Metadata } from "next";

import PolicyPage from "@/components/policy/PolicyPage";

export const metadata: Metadata = {
  title: "이용약관 | Gift Market",
  description: "Gift Market 외부 테스트 운영 이용 안내",
};

export default function TermsPage() {
  return (
    <PolicyPage
      eyebrow="TERMS OF USE"
      title="이용약관"
      description="Gift Market 외부 테스트 이용에 필요한 기본 사항을 안내합니다."
    >
      <section>
        <h2>테스트 운영 안내</h2>
        <p>
          Gift Market은 현재 외부 테스트와 시연을 위한 단계입니다. 이 문서는
          현재 구현된 서비스 이용 흐름을 설명하는 안내이며, 정식 운영 전 최종
          약관과 사업자 정보가 확정되면 보완됩니다.
        </p>
      </section>

      <section>
        <h2>회원과 계정</h2>
        <p>
          회원은 Google 또는 Kakao 로그인을 통해 서비스를 이용할 수 있습니다.
          본인의 계정을 안전하게 관리해야 하며, 주문·배송지·리뷰 등 회원 전용
          기능은 로그인한 계정을 기준으로 처리됩니다.
        </p>
      </section>

      <section>
        <h2>상품 주문과 결제</h2>
        <p>
          상품 가격, 옵션, 재고와 배송비는 주문 시점에 다시 확인됩니다. 결제는
          Toss Payments 결제창을 통해 진행되며, 결제 결과가 Gift Market 서버에서
          확인된 뒤 주문에 반영됩니다.
        </p>
      </section>

      <section>
        <h2>취소·반품·교환과 구매확정</h2>
        <p>
          주문 상태와 처리 가능한 수량에 따라 주문 상세에서 취소, 반품, 교환을
          신청할 수 있습니다. 구매확정된 수량은 취소·반품·교환 대상에서 제외되며,
          자세한 기준은 취소·반품·교환 정책에서 확인할 수 있습니다.
        </p>
      </section>

      <section>
        <h2>게시물 이용</h2>
        <p>
          상품문의와 리뷰에는 타인의 권리를 침해하거나 서비스 운영을 방해하는
          내용을 등록해서는 안 됩니다. 리뷰는 실제 구매확정된 주문상품에 한해
          작성할 수 있습니다.
        </p>
      </section>

      <aside className="policy-note">
        정식 운영 전 법률 검토를 거쳐 적용일, 사업자 정보, 책임과 분쟁 처리 기준을
        포함한 최종 약관을 확정해야 합니다.
      </aside>
    </PolicyPage>
  );
}
