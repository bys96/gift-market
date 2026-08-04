import type { ReactNode } from "react";

import SellerSidebar from "@/components/seller/SellerSidebar";

interface SellerCenterLayoutProps {
  children: ReactNode;
}

export default function SellerCenterLayout({
  children,
}: SellerCenterLayoutProps) {
  return (
    <div className="seller-center-layout">
      <SellerSidebar />

      <div className="seller-center-content">{children}</div>
    </div>
  );
}
