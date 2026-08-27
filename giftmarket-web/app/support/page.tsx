import type { Metadata } from "next";
import Link from "next/link";

import PolicyPage from "@/components/policy/PolicyPage";

export const metadata: Metadata = {
  title: "고객지원 | Gift Market",
  description: "Gift Market 외부 테스트 고객지원 안내",
};

export default function SupportPage() {
  return (
    <PolicyPage
      eyebrow="SUPPORT"
      title="고객지원"
      description="문제 유형에 따라 가장 빠르게 확인할 수 있는 경로를 안내합니다."
    >
      <section>
        <h2>주문 관련 문제</h2>
        <p>
          주문 상태, 배송 정보와 처리 가능한 수량은 주문 상세에서 먼저 확인해 주세요.
          가능한 상태라면 주문 상세의 취소, 반품, 교환 또는 구매확정 기능을 이용할 수
          있습니다.
        </p>
        <Link href="/my/orders" className="policy-action-link">
          주문 내역 확인하기
        </Link>
      </section>

      <section>
        <h2>결제·환불·교환 배송비</h2>
        <p>
          결제 실패 시 결제 결과 화면에서 다시 시도하거나 주문 내역을 확인해 주세요.
          취소·반품 환불과 교환 배송비 결제 상태는 해당 주문 상세에 표시됩니다.
        </p>
      </section>

      <section>
        <h2>상품에 관한 질문</h2>
        <p>
          상품 구성, 옵션과 판매 정보에 관한 질문은 해당 상품 상세의 상품문의 기능을
          이용해 주세요. 상품문의는 일반 주문 문제를 접수하는 고객지원 창구와는
          구분됩니다.
        </p>
      </section>

      <section>
        <h2>별도 고객지원 연락처</h2>
        <p>
          Gift Market은 현재 외부 테스트 운영 단계로, 공식 전화번호와 고객지원
          이메일은 준비 중입니다. 테스트 참여자에게 별도로 제공된 연락 경로가 있다면
          주문번호와 화면에 표시된 오류 내용을 함께 전달해 주세요.
        </p>
      </section>

      <aside className="policy-note">
        이 페이지에는 존재하지 않는 상담 티켓이나 채팅 기능을 제공하지 않습니다.
        정식 운영 전 공식 문의 접점과 운영시간을 확정해 안내할 예정입니다.
      </aside>
    </PolicyPage>
  );
}
