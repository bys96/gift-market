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
  showFirstLast?: boolean;
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
  showFirstLast = true,
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
  const safeWindowSize = Math.min(5, Math.max(1, Math.floor(pageWindowSize)));
  const windowStart = Math.min(
    Math.max(0, safeCurrentPage - Math.floor(safeWindowSize / 2)),
    Math.max(0, totalPages - safeWindowSize),
  );
  const windowEnd = Math.min(windowStart + safeWindowSize, totalPages);
  const pageNumbers = Array.from(
    { length: windowEnd - windowStart },
    (_, index) => windowStart + index,
  );

  const renderItem = (
    page: number,
    label: string | number,
    options: {
      active?: boolean;
      unavailable?: boolean;
      className?: string;
      ariaLabel: string;
    },
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
          aria-label={options.ariaLabel}
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
        aria-label={options.ariaLabel}
        onClick={() => onPageChange?.(page)}
      >
        {label}
      </button>
    );
  };

  return (
    <nav className={className} aria-label={ariaLabel}>
      {showFirstLast &&
        renderItem(0, "<<", {
          unavailable: safeCurrentPage === 0,
          className: "pagination-control pagination-first",
          ariaLabel: "첫 페이지",
        })}

      {showPreviousNext &&
        renderItem(Math.max(0, safeCurrentPage - 1), "<", {
          unavailable: safeCurrentPage === 0,
          className: "pagination-control",
          ariaLabel: "이전 페이지",
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
                ariaLabel: `${pageNumber + 1} 페이지`,
              })}
            </span>
          ))}
        </div>
      )}

      {showPreviousNext &&
        renderItem(Math.min(totalPages - 1, safeCurrentPage + 1), ">", {
          unavailable: safeCurrentPage === totalPages - 1,
          className: "pagination-control",
          ariaLabel: "다음 페이지",
        })}

      {showFirstLast &&
        renderItem(totalPages - 1, ">>", {
          unavailable: safeCurrentPage === totalPages - 1,
          className: "pagination-control pagination-last",
          ariaLabel: "마지막 페이지",
        })}
    </nav>
  );
}
