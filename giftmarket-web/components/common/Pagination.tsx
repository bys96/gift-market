"use client";

import Link from "next/link";

export interface PaginationProps {
  currentPage: number;
  totalPages: number;
  ariaLabel: string;
  disabled?: boolean;
  mode?: "numbers" | "summary";
  pageWindowSize?: number;
  showPreviousNext?: boolean;
  onPageChange?: (page: number) => void;
  getPageHref?: (page: number) => string;
  scroll?: boolean;
  className?: string;
}

export default function Pagination({
  currentPage,
  totalPages,
  ariaLabel,
  disabled = false,
  mode = "summary",
  pageWindowSize = 5,
  showPreviousNext = true,
  onPageChange,
  getPageHref,
  scroll,
  className,
}: PaginationProps) {
  if (totalPages <= 1) {
    return null;
  }

  const safeCurrentPage = Math.min(
    Math.max(0, currentPage),
    totalPages - 1,
  );
  const safeWindowSize = Math.max(1, Math.floor(pageWindowSize));
  const windowStart =
    Math.floor(safeCurrentPage / safeWindowSize) * safeWindowSize;
  const windowEnd = Math.min(windowStart + safeWindowSize, totalPages);
  const pageNumbers = Array.from(
    { length: windowEnd - windowStart },
    (_, index) => windowStart + index,
  );

  const renderItem = (
    page: number,
    label: string | number,
    options: { active?: boolean; unavailable?: boolean; className?: string } = {},
  ) => {
    const unavailable = disabled || options.unavailable;
    const itemClassName = [
      options.className,
      options.active ? "is-active" : "",
      unavailable ? "is-disabled" : "",
    ]
      .filter(Boolean)
      .join(" ");

    if (getPageHref) {
      return (
        <Link
          href={getPageHref(page)}
          scroll={scroll}
          className={itemClassName || undefined}
          aria-current={options.active ? "page" : undefined}
          aria-disabled={unavailable || undefined}
          tabIndex={unavailable ? -1 : undefined}
          onClick={(event) => {
            if (unavailable) {
              event.preventDefault();
            }
          }}
        >
          {label}
        </Link>
      );
    }

    return (
      <button
        type="button"
        className={itemClassName || undefined}
        disabled={unavailable || !onPageChange}
        aria-current={options.active ? "page" : undefined}
        onClick={() => onPageChange?.(page)}
      >
        {label}
      </button>
    );
  };

  return (
    <nav className={className} aria-label={ariaLabel}>
      {showPreviousNext &&
        renderItem(Math.max(0, safeCurrentPage - 1), "이전", {
          unavailable: safeCurrentPage === 0,
          className: "pagination-control",
        })}

      {mode === "summary" ? (
        <span className="pagination-summary">
          {safeCurrentPage + 1} / {totalPages}
        </span>
      ) : (
        <div className="pagination-pages">
          {pageNumbers.map((pageNumber) => (
            <span key={pageNumber} className="pagination-page-item">
              {renderItem(pageNumber, pageNumber + 1, {
                active: pageNumber === safeCurrentPage,
                className: "pagination-number",
              })}
            </span>
          ))}
        </div>
      )}

      {showPreviousNext &&
        renderItem(Math.min(totalPages - 1, safeCurrentPage + 1), "다음", {
          unavailable: safeCurrentPage === totalPages - 1,
          className: "pagination-control",
        })}
    </nav>
  );
}
