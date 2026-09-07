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
import Link from "@tiptap/extension-link";
import Underline from "@tiptap/extension-underline";
import TextAlign from "@tiptap/extension-text-align";
import Placeholder from "@tiptap/extension-placeholder";

import { uploadContentImage, uploadContentVideo } from "@/lib/storage-api";
import { resolveImageUrl } from "@/utils/image-url";
import { MAX_PRODUCT_VIDEO_COUNT, validateProductImage, validateProductVideo } from "@/lib/product-media";
import { countProductVideos, ProductContentImage, ProductContentVideo } from "@/components/seller/ProductMediaNodes";

const MAX_IMAGE_SELECTION_COUNT = 20;

interface ProductEditorProps {
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  onUploadingChange?: (uploading: boolean) => void;
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
  onUploadingChange,
}: ProductEditorProps) {
  const imageInputRef = useRef<HTMLInputElement>(null);
  const videoInputRef = useRef<HTMLInputElement>(null);
  const uploadInProgressRef = useRef(false);

  const [isUploadingImage, setIsUploadingImage] = useState(false);
  const [isUploadingVideo, setIsUploadingVideo] = useState(false);
  const isUploadingMedia = isUploadingImage || isUploadingVideo;
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
      ProductContentImage.configure({
        inline: false,
        allowBase64: false,
        HTMLAttributes: {
          class: "seller-product-editor-content-image",
        },
      }),
      ProductContentVideo,
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
        currentEditor.getText().trim().length > 0 || /<(img|video)\b/.test(html);

      onChange(hasContent ? html : "");
    },
  });

  useEffect(() => {
    if (!editor) {
      return;
    }

    editor.setEditable(!disabled && !isUploadingMedia);
  }, [disabled, editor, isUploadingMedia]);

  useEffect(() => {
    if (!editor) {
      return;
    }

    const normalizedValue = normalizeEditorContent(value);

    const currentHtml = editor.getHTML();

    const currentValue =
      editor.getText().trim().length > 0 || /<(img|video)\b/.test(currentHtml)
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
    const error = validateProductImage(file);
    return error ? `${file.name}: ${error}` : null;
  };

  const handleImageButtonClick = () => {
    if (disabled || uploadInProgressRef.current || !editor) {
      return;
    }

    setUploadError(null);
    imageInputRef.current?.click();
  };

  const handleImageChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? []);

    event.target.value = "";

    if (files.length === 0 || !editor || disabled || uploadInProgressRef.current) {
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
      uploadInProgressRef.current = true;
      onUploadingChange?.(true);
      setIsUploadingImage(true);
      setUploadError(null);

      const uploadedImages: Array<{
        storageKey: string;
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
            storageKey: objectKey,
            alt: file.name,
            title: file.name,
          });
        } catch {
          failedFileNames.push(file.name);
        }
      }

      if (uploadedImages.length > 0 && !editor.isDestroyed) {
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
      uploadInProgressRef.current = false;
      onUploadingChange?.(false);
      setIsUploadingImage(false);
    }
  };

  const handleVideoButtonClick = () => {
    if (disabled || uploadInProgressRef.current || !editor) return;
    if (countProductVideos(editor.state.doc) >= MAX_PRODUCT_VIDEO_COUNT) {
      setUploadError("상세 설명에는 동영상을 최대 3개까지 등록할 수 있습니다.");
      return;
    }
    setUploadError(null);
    videoInputRef.current?.click();
  };

  const handleVideoChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file || !editor || disabled || uploadInProgressRef.current) return;
    const error = validateProductVideo(file, countProductVideos(editor.state.doc));
    if (error) { setUploadError(error); return; }
    const position = editor.state.selection.from;
    try {
      uploadInProgressRef.current = true;
      onUploadingChange?.(true);
      setIsUploadingVideo(true);
      setUploadError(null);
      const objectKey = await uploadContentVideo(file);
      if (editor.isDestroyed) return;
      if (!resolveImageUrl(objectKey)) throw new Error("동영상 주소를 생성할 수 없습니다. Storage URL 설정을 확인해주세요.");
      const latestError = validateProductVideo(file, countProductVideos(editor.state.doc));
      if (latestError) throw new Error(latestError);
      editor.chain().focus(null, { scrollIntoView: false }).insertContentAt(
        Math.min(position, editor.state.doc.content.size),
        [{ type: "productVideo", attrs: { storageKey: objectKey } }, { type: "paragraph" }],
      ).run();
    } catch (failure) {
      setUploadError(failure instanceof Error ? failure.message : "동영상 업로드에 실패했습니다.");
    } finally {
      // TODO: 상품 저장 취소/업로드 후 이탈 시 남는 object는 기존 상세 이미지와 함께 cleanup 대상으로 관리한다.
      uploadInProgressRef.current = false;
      onUploadingChange?.(false);
      setIsUploadingVideo(false);
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
        disabled={disabled || isUploadingMedia}
        onChange={handleImageChange}
      />
      <input
        ref={videoInputRef}
        type="file"
        accept="video/mp4"
        className="seller-product-editor-image-input"
        disabled={disabled || isUploadingMedia}
        onChange={handleVideoChange}
      />

      <div className="seller-product-editor-toolbar">
        <div className="seller-product-editor-toolbar-group">
          <ToolbarButton
            label="본문"
            title="본문"
            active={editor.isActive("paragraph")}
            disabled={disabled || isUploadingMedia}
            onClick={() => {
              focusWithoutScroll()?.setParagraph().run();
            }}
          />
          <ToolbarButton
            label="제목 1"
            title="제목 1"
            active={editor.isActive("heading", { level: 1 })}
            disabled={disabled || isUploadingMedia}
            onClick={() => {
              focusWithoutScroll()?.toggleHeading({ level: 1 }).run();
            }}
          />
          <ToolbarButton
            label="제목 2"
            title="제목 2"
            active={editor.isActive("heading", { level: 2 })}
            disabled={disabled || isUploadingMedia}
            onClick={() => {
              focusWithoutScroll()?.toggleHeading({ level: 2 }).run();
            }}
          />
          <ToolbarButton
            label="제목 3"
            title="제목 3"
            active={editor.isActive("heading", { level: 3 })}
            disabled={disabled || isUploadingMedia}
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
            disabled={disabled || isUploadingMedia}
            onClick={() => {
              focusWithoutScroll()?.toggleBold().run();
            }}
          />
          <ToolbarButton
            label="기울임"
            title="기울임"
            active={editor.isActive("italic")}
            disabled={disabled || isUploadingMedia}
            onClick={() => {
              focusWithoutScroll()?.toggleItalic().run();
            }}
          />
          <ToolbarButton
            label="밑줄"
            title="밑줄"
            active={editor.isActive("underline")}
            disabled={disabled || isUploadingMedia}
            onClick={() => {
              focusWithoutScroll()?.toggleUnderline().run();
            }}
          />
          <ToolbarButton
            label="취소선"
            title="취소선"
            active={editor.isActive("strike")}
            disabled={disabled || isUploadingMedia}
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
            disabled={disabled || isUploadingMedia}
            onClick={() => {
              focusWithoutScroll()?.setTextAlign("left").run();
            }}
          />
          <ToolbarButton
            label="가운데"
            title="가운데 정렬"
            active={editor.isActive({ textAlign: "center" })}
            disabled={disabled || isUploadingMedia}
            onClick={() => {
              focusWithoutScroll()?.setTextAlign("center").run();
            }}
          />
          <ToolbarButton
            label="오른쪽"
            title="오른쪽 정렬"
            active={editor.isActive({ textAlign: "right" })}
            disabled={disabled || isUploadingMedia}
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
            disabled={disabled || isUploadingMedia}
            onClick={() => {
              focusWithoutScroll()?.toggleBulletList().run();
            }}
          />
          <ToolbarButton
            label="번호"
            title="번호 목록"
            active={editor.isActive("orderedList")}
            disabled={disabled || isUploadingMedia}
            onClick={() => {
              focusWithoutScroll()?.toggleOrderedList().run();
            }}
          />
          <ToolbarButton
            label="인용"
            title="인용문"
            active={editor.isActive("blockquote")}
            disabled={disabled || isUploadingMedia}
            onClick={() => {
              focusWithoutScroll()?.toggleBlockquote().run();
            }}
          />
          <ToolbarButton
            label="구분선"
            title="구분선 삽입"
            disabled={disabled || isUploadingMedia}
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
            disabled={disabled || isUploadingMedia}
            onClick={handleSetLink}
          />
          <ToolbarButton
            label={isUploadingImage ? "업로드 중" : "이미지"}
            title="본문 이미지 추가"
            disabled={disabled || isUploadingMedia}
            onClick={handleImageButtonClick}
          />
          <ToolbarButton
            label={isUploadingVideo ? "업로드 중" : "동영상"}
            title="본문 MP4 동영상 추가"
            disabled={disabled || isUploadingMedia}
            onClick={handleVideoButtonClick}
          />
        </div>

        <div className="seller-product-editor-toolbar-group">
          <ToolbarButton
            label="실행 취소"
            title="실행 취소"
            disabled={disabled || isUploadingMedia || !editor.can().undo()}
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
        <span>텍스트, 이미지, GIF, MP4, 링크를 자유롭게 배치할 수 있습니다.</span>
        <span>
          이미지: 한 번에 최대 {MAX_IMAGE_SELECTION_COUNT}장·파일당 20MB / 동영상: 최대 3개·파일당 50MB
        </span>
      </div>
    </div>
  );
}
