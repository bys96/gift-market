import Link from "next/link";

export default function Footer() {
  return (
    <footer className="layout-footer">
      <div className="layout-footer-inner">
        <nav className="layout-footer-links" aria-label="정책 및 고객지원">
          <Link href="/terms">이용약관</Link>
          <Link href="/privacy">개인정보처리방침</Link>
          <Link href="/policy/returns">취소·반품·교환 정책</Link>
          <Link href="/support">고객지원</Link>
        </nav>
        <p>© 2026 Gift Market. All rights reserved.</p>
      </div>
    </footer>
  );
}
