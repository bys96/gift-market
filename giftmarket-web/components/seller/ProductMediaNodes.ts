import { Node, mergeAttributes } from "@tiptap/react";
import Image from "@tiptap/extension-image";
import { Plugin } from "@tiptap/pm/state";
import type { Node as ProseMirrorNode } from "@tiptap/pm/model";

import { isProductMediaKey, MAX_PRODUCT_VIDEO_COUNT, resolveProductMediaSource } from "@/lib/product-media";

const storageKeyAttribute = {
  default: null,
  parseHTML: (element: HTMLElement) => element.getAttribute("data-storage-key"),
  renderHTML: (attributes: Record<string, unknown>) => attributes.storageKey
    ? { "data-storage-key": attributes.storageKey }
    : {},
};

function mediaAttributes(attributes: Record<string, string>, tag: "img" | "video") {
  const result = { ...attributes };
  const key = result["data-storage-key"];
  if (key && isProductMediaKey(key, tag)) {
    delete result.src;
  } else {
    delete result["data-storage-key"];
    if (!resolveProductMediaSource(null, result.src, tag)) delete result.src;
  }
  return result;
}

function mediaNodeView(node: ProseMirrorNode, tag: "img" | "video") {
  const dom = document.createElement(tag);
  const src = resolveProductMediaSource(node.attrs.storageKey, node.attrs.src, tag);
  if (src) dom.setAttribute("src", src);
  if (tag === "video") {
    dom.setAttribute("controls", "");
    dom.setAttribute("preload", "metadata");
  } else {
    dom.setAttribute("alt", node.attrs.alt ?? "");
    if (node.attrs.title) dom.setAttribute("title", node.attrs.title);
    for (const attribute of ["width", "height"]) {
      const value = Number(node.attrs[attribute]);
      if (Number.isFinite(value) && value > 0) dom.setAttribute(attribute, String(value));
    }
  }
  return { dom };
}

export const ProductContentImage = Image.extend({
  addAttributes() {
    return { ...this.parent?.(), storageKey: storageKeyAttribute };
  },
  parseHTML() {
    return [{ tag: "img[data-storage-key]" }, ...(this.parent?.() ?? [])];
  },
  renderHTML({ HTMLAttributes }) {
    return ["img", mediaAttributes(mergeAttributes(this.options.HTMLAttributes, HTMLAttributes), "img")];
  },
  addNodeView() {
    return ({ node }) => mediaNodeView(node, "img");
  },
});

export function countProductVideos(document: ProseMirrorNode): number {
  let count = 0;
  document.descendants((node) => { if (node.type.name === "productVideo") count += 1; });
  return count;
}

export const ProductContentVideo = Node.create({
  name: "productVideo",
  group: "block",
  atom: true,
  draggable: true,
  addAttributes() {
    return { src: { default: null }, storageKey: storageKeyAttribute };
  },
  parseHTML() {
    return [{ tag: "video[data-storage-key]" }, { tag: "video[src]" }];
  },
  renderHTML({ HTMLAttributes }) {
    return ["video", { ...mediaAttributes(HTMLAttributes, "video"), controls: "", preload: "metadata" }];
  },
  addNodeView() {
    return ({ node }) => mediaNodeView(node, "video");
  },
  addProseMirrorPlugins() {
    return [new Plugin({
      filterTransaction: (transaction, state) => !transaction.docChanged
        || countProductVideos(transaction.doc) <= MAX_PRODUCT_VIDEO_COUNT
        || countProductVideos(transaction.doc) < countProductVideos(state.doc),
    })];
  },
});
