"use client";

import {
  type ChangeEvent,
  type MouseEvent,
  useEffect,
  useRef,
  useState,
} from "react";
import { EditorContent, useEditor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Image from "@tiptap/extension-image";
import Link from "@tiptap/extension-link";
import Underline from "@tiptap/extension-underline";
import TextAlign from "@tiptap/extension-text-align";
import Placeholder from "@tiptap/extension-placeholder";

import { uploadContentImage } from "@/lib/storage-api";
import { resolveImageUrl } from "@/utils/image-url";

const MAX_IMAGE_FILE_SIZE = 5 * 1024 * 1024;
const MAX_IMAGE_SELECTION_COUNT = 20;

const ALLOWED_IMAGE_TYPES = [
  "image/jpeg",
  "image/png",
  "image/webp",
  "image/gif",
];

interface ProductEditorProps {
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
}

interface ToolbarButtonProps {
  label: string;
  title: string;
  active?: boolean;
  disabled?: boolean;
  onClick: () => void;
}

function ToolbarButton({
  label,
  title,
  active = false,
  disabled = false,
  onClick,
}: ToolbarButtonProps) {
  const handleMouseDown = (event: MouseEvent<HTMLButtonElement>) => {
    // 에디터 선택 영역을 유지하고 버튼 클릭으로 인한 스크롤 이동을 막습니다.
    event.preventDefault();
  };

  return (
    <button
      type="button"
      className={[
        "seller-product-editor-toolbar-button",
        active ? "seller-product-editor-toolbar-button-active" : "",
      ]
        .filter(Boolean)
        .join(" ")}
      title={title}
      aria-label={title}
      aria-pressed={active}
      disabled={disabled}
      onMouseDown={handleMouseDown}
      onClick={onClick}
    >
      {label}
    </button>
  );
}

function normalizeEditorContent(value: string): string {
  return value.trim() ? value : "";
}

export default function ProductEditor({
  value,
  onChange,
  disabled = false,
}: ProductEditorProps) {
  const imageInputRef = useRef<HTMLInputElement>(null);

  const [isUploadingImage, setIsUploadingImage] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const editor = useEditor({
    immediatelyRender: false,
    editable: !disabled,
    extensions: [
      StarterKit.configure({
        link: false,
        underline: false,
      }),
      Underline,
      Link.configure({
        openOnClick: false,
        autolink: true,
        linkOnPaste: true,
        HTMLAttributes: {
          rel: "noopener noreferrer nofollow",
          target: "_blank",
        },
      }),
      Image.configure({
        inline: false,
        allowBase64: false,
        HTMLAttributes: {
          class: "seller-product-editor-content-image",
        },
      }),
      TextAlign.configure({
        types: ["heading", "paragraph"],
      }),
      Placeholder.configure({
        placeholder:
          "상품의 특징, 구성, 사용 방법, 주의사항 등을 자유롭게 작성해주세요.",
      }),
    ],
    content: normalizeEditorContent(value),
    editorProps: {
      attributes: {
        class: "seller-product-editor-content",
        spellcheck: "true",
      },
    },
    onUpdate: ({ editor: currentEditor }) => {
      const html = currentEditor.getHTML();
      const hasContent =
        currentEditor.getText().trim().length > 0 || html.includes("<img");

      onChange(hasContent ? html : "");
    },
  });

  useEffect(() => {
    if (!editor) {
      return;
    }

    editor.setEditable(!disabled);
  }, [disabled, editor]);

  useEffect(() => {
    if (!editor) {
      return;
    }

    const normalizedValue = normalizeEditorContent(value);

    const currentHtml = editor.getHTML();

    const currentValue =
      editor.getText().trim().length > 0 || currentHtml.includes("<img")
        ? currentHtml
        : "";

    if (currentValue === normalizedValue) {
      return;
    }

    editor.commands.setContent(normalizedValue, {
      emitUpdate: false,
    });

    editor.commands.blur();
  }, [editor, value]);

  const focusWithoutScroll = () => {
    return editor?.chain().focus(null, {
      scrollIntoView: false,
    });
  };

  const validateImageFile = (file: File): string | null => {
    if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
      return `${file.name}: JPG, PNG, WEBP, GIF 이미지만 업로드할 수 있습니다.`;
    }

    if (file.size > MAX_IMAGE_FILE_SIZE) {
      return `${file.name}: 이미지는 파일당 최대 5MB까지 업로드할 수 있습니다.`;
    }

    return null;
  };

  const handleImageButtonClick = () => {
    if (disabled || isUploadingImage || !editor) {
      return;
    }

    setUploadError(null);
    imageInputRef.current?.click();
  };

  const handleImageChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? []);

    event.target.value = "";

    if (files.length === 0 || !editor) {
      return;
    }

    if (files.length > MAX_IMAGE_SELECTION_COUNT) {
      setUploadError(
        `본문 이미지는 한 번에 최대 ${MAX_IMAGE_SELECTION_COUNT}장까지 선택할 수 있습니다.`,
      );
      return;
    }

    const validationErrors = files
      .map(validateImageFile)
      .filter((message): message is string => Boolean(message));

    if (validationErrors.length > 0) {
      setUploadError(validationErrors.join("\n"));
      return;
    }

    try {
      setIsUploadingImage(true);
      setUploadError(null);

      const uploadedImages: Array<{
        src: string;
        alt: string;
        title: string;
      }> = [];
      const failedFileNames: string[] = [];

      for (const file of files) {
        try {
          const objectKey = await uploadContentImage(file);
          const imageUrl = resolveImageUrl(objectKey);

          if (!imageUrl) {
            throw new Error("이미지 주소 생성 실패");
          }

          uploadedImages.push({
            src: imageUrl,
            alt: file.name,
            title: file.name,
          });
        } catch {
          failedFileNames.push(file.name);
        }
      }

      if (uploadedImages.length > 0) {
        const content = uploadedImages.flatMap((image) => [
          {
            type: "image",
            attrs: image,
          },
          {
            type: "paragraph",
          },
        ]);

        focusWithoutScroll()?.insertContent(content).run();
      }

      if (failedFileNames.length > 0) {
        setUploadError(
          `다음 이미지를 업로드하지 못했습니다: ${failedFileNames.join(", ")}`,
        );
      }
    } finally {
      setIsUploadingImage(false);
    }
  };

  const handleSetLink = () => {
    if (!editor || disabled) {
      return;
    }

    const previousUrl = editor.getAttributes("link").href ?? "";
    const url = window.prompt("연결할 주소를 입력해주세요.", previousUrl);

    if (url === null) {
      return;
    }

    const trimmedUrl = url.trim();

    if (!trimmedUrl) {
      focusWithoutScroll()?.extendMarkRange("link").unsetLink().run();
      return;
    }

    focusWithoutScroll()
      ?.extendMarkRange("link")
      .setLink({ href: trimmedUrl })
      .run();
  };

  if (!editor) {
    return (
      <div className="seller-product-editor-loading">
        에디터를 불러오는 중입니다.
      </div>
    );
  }

  return (
    <div
      className={[
        "seller-product-editor",
        disabled ? "seller-product-editor-disabled" : "",
      ]
        .filter(Boolean)
        .join(" ")}
    >
      <input
        ref={imageInputRef}
        type="file"
        multiple
        accept="image/jpeg,image/png,image/webp,image/gif"
        className="seller-product-editor-image-input"
        disabled={disabled || isUploadingImage}
        onChange={handleImageChange}
      />

      <div className="seller-product-editor-toolbar">
        <div className="seller-product-editor-toolbar-group">
          <ToolbarButton
            label="본문"
            title="본문"
            active={editor.isActive("paragraph")}
            disabled={disabled}
            onClick={() => {
              focusWithoutScroll()?.setParagraph().run();
            }}
          />
          <ToolbarButton
            label="제목 1"
            title="제목 1"
            active={editor.isActive("heading", { level: 1 })}
            disabled={disabled}
            onClick={() => {
              focusWithoutScroll()?.toggleHeading({ level: 1 }).run();
            }}
          />
          <ToolbarButton
            label="제목 2"
            title="제목 2"
            active={editor.isActive("heading", { level: 2 })}
            disabled={disabled}
            onClick={() => {
              focusWithoutScroll()?.toggleHeading({ level: 2 }).run();
            }}
          />
          <ToolbarButton
            label="제목 3"
            title="제목 3"
            active={editor.isActive("heading", { level: 3 })}
            disabled={disabled}
            onClick={() => {
              focusWithoutScroll()?.toggleHeading({ level: 3 }).run();
            }}
          />
        </div>

        <div className="seller-product-editor-toolbar-group">
          <ToolbarButton
            label="굵게"
            title="굵게"
            active={editor.isActive("bold")}
            disabled={disabled}
            onClick={() => {
              focusWithoutScroll()?.toggleBold().run();
            }}
          />
          <ToolbarButton
            label="기울임"
            title="기울임"
            active={editor.isActive("italic")}
            disabled={disabled}
            onClick={() => {
              focusWithoutScroll()?.toggleItalic().run();
            }}
          />
          <ToolbarButton
            label="밑줄"
            title="밑줄"
            active={editor.isActive("underline")}
            disabled={disabled}
            onClick={() => {
              focusWithoutScroll()?.toggleUnderline().run();
            }}
          />
          <ToolbarButton
            label="취소선"
            title="취소선"
            active={editor.isActive("strike")}
            disabled={disabled}
            onClick={() => {
              focusWithoutScroll()?.toggleStrike().run();
            }}
          />
        </div>

        <div className="seller-product-editor-toolbar-group">
          <ToolbarButton
            label="왼쪽"
            title="왼쪽 정렬"
            active={editor.isActive({ textAlign: "left" })}
            disabled={disabled}
            onClick={() => {
              focusWithoutScroll()?.setTextAlign("left").run();
            }}
          />
          <ToolbarButton
            label="가운데"
            title="가운데 정렬"
            active={editor.isActive({ textAlign: "center" })}
            disabled={disabled}
            onClick={() => {
              focusWithoutScroll()?.setTextAlign("center").run();
            }}
          />
          <ToolbarButton
            label="오른쪽"
            title="오른쪽 정렬"
            active={editor.isActive({ textAlign: "right" })}
            disabled={disabled}
            onClick={() => {
              focusWithoutScroll()?.setTextAlign("right").run();
            }}
          />
        </div>

        <div className="seller-product-editor-toolbar-group">
          <ToolbarButton
            label="목록"
            title="글머리 기호 목록"
            active={editor.isActive("bulletList")}
            disabled={disabled}
            onClick={() => {
              focusWithoutScroll()?.toggleBulletList().run();
            }}
          />
          <ToolbarButton
            label="번호"
            title="번호 목록"
            active={editor.isActive("orderedList")}
            disabled={disabled}
            onClick={() => {
              focusWithoutScroll()?.toggleOrderedList().run();
            }}
          />
          <ToolbarButton
            label="인용"
            title="인용문"
            active={editor.isActive("blockquote")}
            disabled={disabled}
            onClick={() => {
              focusWithoutScroll()?.toggleBlockquote().run();
            }}
          />
          <ToolbarButton
            label="구분선"
            title="구분선 삽입"
            disabled={disabled}
            onClick={() => {
              focusWithoutScroll()?.setHorizontalRule().run();
            }}
          />
        </div>

        <div className="seller-product-editor-toolbar-group">
          <ToolbarButton
            label="링크"
            title="링크 설정"
            active={editor.isActive("link")}
            disabled={disabled}
            onClick={handleSetLink}
          />
          <ToolbarButton
            label={isUploadingImage ? "업로드 중" : "이미지"}
            title="본문 이미지 추가"
            disabled={disabled || isUploadingImage}
            onClick={handleImageButtonClick}
          />
        </div>

        <div className="seller-product-editor-toolbar-group">
          <ToolbarButton
            label="실행 취소"
            title="실행 취소"
            disabled={disabled || !editor.can().undo()}
            onClick={() => {
              focusWithoutScroll()?.undo().run();
            }}
          />
          <ToolbarButton
            label="다시 실행"
            title="다시 실행"
            disabled={disabled || !editor.can().redo()}
            onClick={() => {
              focusWithoutScroll()?.redo().run();
            }}
          />
        </div>
      </div>

      <EditorContent editor={editor} />

      {uploadError && (
        <p className="seller-product-editor-error" role="alert">
          {uploadError}
        </p>
      )}

      <div className="seller-product-editor-footer">
        <span>텍스트, 이미지, GIF, 링크를 자유롭게 배치할 수 있습니다.</span>
        <span>
          이미지는 한 번에 최대 {MAX_IMAGE_SELECTION_COUNT}장, 파일당 최대 5MB
        </span>
      </div>
    </div>
  );
}
