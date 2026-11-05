"use client";

import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { getProductSuggestions } from "@/lib/product-api";
import { moveSuggestionIndex, scheduleSuggestionRequest } from "@/lib/suggestion-request";
import type { ProductSuggestions } from "@/types/product";

interface Props {
  id: string;
  value: string;
  onChange: (value: string) => void;
  onSearch: (value: string) => void;
  className?: string;
  inputClassName?: string;
  buttonClassName?: string;
  placeholder?: string;
}

export default function SearchBox({ id, value, onChange, onSearch, className, inputClassName, buttonClassName, placeholder = "선물할 상품을 검색해 보세요" }: Props) {
  const formRef = useRef<HTMLFormElement>(null);
  const queryRef = useRef(value.trim());
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const [status, setStatus] = useState<"idle" | "loading" | "success" | "error">("idle");
  const [result, setResult] = useState<{ query: string; data: ProductSuggestions } | null>(null);
  const query = value.trim();
  const currentResult = result?.query === query ? result.data : null;
  const items = currentResult?.keywords ?? [];
  const visible = open && query.length > 0;

  useEffect(() => {
    queryRef.current = query;
    if (!open || !query) return;
    return scheduleSuggestionRequest(query, getProductSuggestions,
      () => setStatus("loading"),
      (data) => { if (queryRef.current === query) { setResult({ query, data }); setStatus("success"); } },
      () => { if (queryRef.current === query) { setResult(null); setStatus("error"); } });
  }, [open, query, value]);

  useEffect(() => {
    const handleOutside = (event: PointerEvent) => {
      if (!formRef.current?.contains(event.target as Node)) { setOpen(false); setActive(-1); }
    };
    document.addEventListener("pointerdown", handleOutside);
    return () => document.removeEventListener("pointerdown", handleOutside);
  }, []);

  const choose = (index: number) => {
    const item = items[index];
    if (!item) return;
    setOpen(false); setActive(-1);
    onChange(item); onSearch(item);
  };
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (visible && active >= 0 && items[active]) { choose(active); return; }
    if (query) { setOpen(false); onSearch(query); }
  };
  const keyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.nativeEvent.isComposing) return;
    if (event.key === "Escape") { setOpen(false); setActive(-1); }
    else if (event.key === "ArrowDown" && items.length) { event.preventDefault(); setOpen(true); setActive((index) => moveSuggestionIndex(index, items.length, 1)); }
    else if (event.key === "ArrowUp" && items.length) { event.preventDefault(); setOpen(true); setActive((index) => moveSuggestionIndex(index, items.length, -1)); }
  };

  return (
    <form ref={formRef} className={`${className ?? ""} search-box`} onSubmit={submit}>
      <label htmlFor={id} className="sr-only">상품 검색</label>
      <input id={id} type="search" value={value} maxLength={100} className={inputClassName}
        placeholder={placeholder} autoComplete="off" role="combobox" aria-autocomplete="list"
        aria-expanded={visible} aria-controls={`${id}-suggestions`}
        aria-activedescendant={visible && active >= 0 && active < items.length ? `${id}-option-${active}` : undefined}
        onFocus={() => {
          queryRef.current = query;
          setActive(-1);
          if (!open) { setResult(null); setStatus("idle"); setOpen(true); }
        }} onKeyDown={keyDown}
        onChange={(event) => { queryRef.current = event.target.value.trim(); onChange(event.target.value); setResult(null); setStatus("idle"); setActive(-1); setOpen(true); }} />
      <button type="submit" className={buttonClassName}>검색</button>
      {visible && <div className="search-suggestions">
        {(status === "idle" || status === "loading") && <p role="status">추천 검색어를 찾고 있습니다.</p>}
        {status === "error" && <p role="status">추천 검색어를 불러오지 못했습니다.</p>}
        {status === "success" && items.length === 0 && <p role="status">검색 결과가 없습니다.</p>}
        <ul id={`${id}-suggestions`} role="listbox" aria-label="검색 추천">
          {items.map((item, index) => <li id={`${id}-option-${index}`} key={`k${item}`}
            role="option" aria-selected={active === index} className={active === index ? "search-suggestion-active" : ""}
            onMouseDown={(event) => event.preventDefault()} onMouseEnter={() => setActive(index)} onClick={() => choose(index)}>
            <span className="search-suggestion-keyword-icon" aria-hidden="true">⌕</span><span className="search-suggestion-name">{item}</span><small>추천 검색어</small>
          </li>)}
        </ul>
      </div>}
    </form>
  );
}
