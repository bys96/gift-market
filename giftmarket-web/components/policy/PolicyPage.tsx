import Link from "next/link";
import type { ReactNode } from "react";

interface PolicyPageProps {
  eyebrow: string;
  title: string;
  description: string;
  children: ReactNode;
}

const POLICY_LINKS = [
  { href: "/terms", label: "이용약관" },
  { href: "/privacy", label: "개인정보처리방침" },
  { href: "/policy/returns", label: "취소·반품·교환 정책" },
  { href: "/support", label: "고객지원" },
];

export default function PolicyPage({
  eyebrow,
  title,
  description,
  children,
}: PolicyPageProps) {
  return (
    <main className="policy-page">
      <header className="policy-hero">
        <p>{eyebrow}</p>
        <h1>{title}</h1>
        <span>{description}</span>
      </header>

      <div className="policy-layout">
        <nav className="policy-navigation" aria-label="정책 및 고객지원">
          {POLICY_LINKS.map((link) => (
            <Link href={link.href} key={link.href}>
              {link.label}
            </Link>
          ))}
        </nav>

        <article className="policy-content">{children}</article>
      </div>
    </main>
  );
}
