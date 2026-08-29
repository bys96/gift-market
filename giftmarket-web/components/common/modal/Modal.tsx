"use client";

import {
  type KeyboardEventHandler,
  type ReactNode,
  type RefObject,
  useEffect,
  useRef,
  useState,
} from "react";
import { createPortal } from "react-dom";

interface ModalProps {
  children: ReactNode;
  onClose: () => void;
  overlayClassName: string;
  contentClassName: string;
  ariaLabelledBy?: string;
  ariaLabel?: string;
  ariaDescribedBy?: string;
  initialFocusRef?: RefObject<HTMLElement | null>;
  closeOnEscape?: boolean;
  closeOnBackdrop?: boolean;
  onContentKeyDown?: KeyboardEventHandler<HTMLDivElement>;
}

const FOCUSABLE_SELECTOR = [
  "a[href]",
  "button:not([disabled])",
  "input:not([disabled]):not([type='hidden'])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  "[tabindex]:not([tabindex='-1'])",
  "[contenteditable='true']",
].join(",");

const modalStack: symbol[] = [];
let scrollLockCount = 0;
let previousBodyOverflow = "";

function lockBodyScroll() {
  if (scrollLockCount === 0) {
    previousBodyOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
  }

  scrollLockCount += 1;
}

function unlockBodyScroll() {
  scrollLockCount = Math.max(0, scrollLockCount - 1);

  if (scrollLockCount === 0) {
    document.body.style.overflow = previousBodyOverflow;
    previousBodyOverflow = "";
  }
}

function getFocusableElements(container: HTMLElement) {
  return Array.from(
    container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR),
  ).filter(
    (element) =>
      !element.hasAttribute("disabled") &&
      element.getAttribute("aria-hidden") !== "true" &&
      element.getClientRects().length > 0,
  );
}

export default function Modal({
  children,
  onClose,
  overlayClassName,
  contentClassName,
  ariaLabelledBy,
  ariaLabel,
  ariaDescribedBy,
  initialFocusRef,
  closeOnEscape = true,
  closeOnBackdrop = true,
  onContentKeyDown,
}: ModalProps) {
  const modalIdRef = useRef(Symbol("modal"));
  const contentRef = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  const closeOnEscapeRef = useRef(closeOnEscape);
  const initialFocusRefRef = useRef(initialFocusRef);
  const [isMounted, setIsMounted] = useState(false);

  useEffect(() => {
    onCloseRef.current = onClose;
    closeOnEscapeRef.current = closeOnEscape;
    initialFocusRefRef.current = initialFocusRef;
  }, [closeOnEscape, initialFocusRef, onClose]);

  useEffect(() => {
    const modalId = modalIdRef.current;
    const previouslyFocusedElement =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;

    modalStack.push(modalId);
    lockBodyScroll();
    // Portal target은 client mount 이후에만 사용할 수 있다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setIsMounted(true);

    const isTopmostModal = () => modalStack.at(-1) === modalId;

    const focusInsideModal = () => {
      const content = contentRef.current;
      if (!content) return;

      const requestedFocus = initialFocusRefRef.current?.current;
      const focusTarget =
        requestedFocus && content.contains(requestedFocus)
          ? requestedFocus
          : getFocusableElements(content)[0] ?? content;

      focusTarget.focus();
    };

    const focusFrame = window.requestAnimationFrame(() => {
      if (isTopmostModal()) focusInsideModal();
    });

    const handleKeyDown = (event: KeyboardEvent) => {
      if (!isTopmostModal()) return;

      if (event.key === "Escape" && closeOnEscapeRef.current) {
        event.preventDefault();
        event.stopPropagation();
        onCloseRef.current();
        return;
      }

      if (event.key !== "Tab") return;

      const content = contentRef.current;
      if (!content) return;

      const focusableElements = getFocusableElements(content);
      if (focusableElements.length === 0) {
        event.preventDefault();
        content.focus();
        return;
      }

      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];
      const activeElement = document.activeElement;

      if (event.shiftKey) {
        if (activeElement === firstElement || !content.contains(activeElement)) {
          event.preventDefault();
          lastElement.focus();
        }
      } else if (
        activeElement === lastElement ||
        !content.contains(activeElement)
      ) {
        event.preventDefault();
        firstElement.focus();
      }
    };

    const handleFocusIn = (event: FocusEvent) => {
      const content = contentRef.current;
      if (
        !isTopmostModal() ||
        !content ||
        content.contains(event.target as Node)
      ) {
        return;
      }

      focusInsideModal();
    };

    document.addEventListener("keydown", handleKeyDown, true);
    document.addEventListener("focusin", handleFocusIn, true);

    return () => {
      window.cancelAnimationFrame(focusFrame);
      document.removeEventListener("keydown", handleKeyDown, true);
      document.removeEventListener("focusin", handleFocusIn, true);

      const stackIndex = modalStack.lastIndexOf(modalId);
      if (stackIndex >= 0) modalStack.splice(stackIndex, 1);
      unlockBodyScroll();

      if (
        previouslyFocusedElement?.isConnected &&
        typeof previouslyFocusedElement.focus === "function"
      ) {
        previouslyFocusedElement.focus();
      }
    };
  }, []);

  if (!isMounted) return null;

  return createPortal(
    <div
      className={overlayClassName}
      onMouseDown={(event) => {
        if (
          closeOnBackdrop &&
          event.target === event.currentTarget &&
          modalStack.at(-1) === modalIdRef.current
        ) {
          onCloseRef.current();
        }
      }}
    >
      <div
        ref={contentRef}
        className={contentClassName}
        role="dialog"
        aria-modal="true"
        aria-labelledby={ariaLabelledBy}
        aria-label={ariaLabelledBy ? undefined : ariaLabel}
        aria-describedby={ariaDescribedBy}
        tabIndex={-1}
        onMouseDown={(event) => event.stopPropagation()}
        onKeyDown={onContentKeyDown}
      >
        {children}
      </div>
    </div>,
    document.body,
  );
}
