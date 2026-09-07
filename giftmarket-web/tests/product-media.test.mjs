import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { resolve } from "node:path";
import test from "node:test";
import vm from "node:vm";
import ts from "typescript";

const root = resolve(import.meta.dirname, "..");
const require = createRequire(resolve(root, "package.json"));
const modules = new Map();
function load(file) {
  if (modules.has(file)) return modules.get(file);
  const exports = {};
  vm.runInNewContext(ts.transpileModule(readFileSync(resolve(root, file), "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText, {
    exports,
    require: (name) => name.startsWith("@/") ? load(`${name.slice(2)}.ts`) : require(name),
    process: { env: { NEXT_PUBLIC_STORAGE_BASE_URL: "https://cdn.example/media", NODE_ENV: "test" } },
    URL, console,
  });
  modules.set(file, exports);
  return exports;
}

const media = load("lib/product-media.ts");
const { ProductContentImage, ProductContentVideo, countProductVideos } = load("components/seller/ProductMediaNodes.ts");
const { getSchema } = require("@tiptap/core");
const { StarterKit } = require("@tiptap/starter-kit");
const schema = getSchema([StarterKit, ProductContentImage, ProductContentVideo]);
const imageKey = "products/12/content/12345678-1234-1234-1234-123456789abc.png";
const videoKey = "products/12/content/video/12345678-1234-1234-1234-123456789abc.mp4";
const MB = 1024 * 1024;

test("all product images share 20MB boundary and retain allowed formats", () => {
  for (const [name, type] of [["a.jpg", "image/jpeg"], ["a.jpeg", "image/jpeg"], ["a.png", "image/png"], ["a.webp", "image/webp"], ["a.gif", "image/gif"]]) {
    assert.equal(media.validateProductImage({ name, type, size: 20 * MB }), null);
    assert.match(media.validateProductImage({ name, type, size: 20 * MB + 1 }), /20MB/);
  }
  assert.ok(media.validateProductImage({ name: "a.png", type: "image/png", size: 0 }));
  assert.ok(media.validateProductImage({ name: "a.svg", type: "image/svg+xml", size: 1 }));
  assert.ok(media.validateProductImage({ name: "a.mp4", type: "image/png", size: 1 }));
});

test("MP4 validation enforces 50MB and three-video limit", () => {
  const file = { name: "clip.mp4", type: "video/mp4", size: 50 * MB };
  assert.equal(media.validateProductVideo(file, 2), null);
  assert.match(media.validateProductVideo(file, 3), /3개/);
  assert.match(media.validateProductVideo({ ...file, size: 50 * MB + 1 }, 0), /50MB/);
  assert.ok(media.validateProductVideo({ ...file, size: 0 }, 0));
  assert.ok(media.validateProductVideo({ ...file, name: "clip.mov" }, 0));
  assert.ok(media.validateProductVideo({ ...file, type: "video/webm" }, 0));
});

test("Tiptap image/video DOM serialization stores keys without storage origin", () => {
  for (const [type, key, tag] of [["image", imageKey, "img"], ["productVideo", videoKey, "video"]]) {
    const node = schema.nodes[type].create({ storageKey: key, src: "https://old.example/file" });
    const spec = schema.nodes[type].spec.toDOM(node);
    assert.equal(spec[0], tag);
    assert.equal(spec[1]["data-storage-key"], key);
    assert.equal(spec[1].src, undefined);
    if (tag === "video") {
      assert.equal(spec[1].controls, "");
      assert.equal(spec[1].preload, "metadata");
      assert.equal(spec[1].autoplay, undefined);
    }
    assert.ok(schema.nodes[type].spec.parseDOM.some((rule) => rule.tag === `${tag}[data-storage-key]`));
  }
});

test("video-only document survives JSON round trip and fourth-video transaction is rejected", () => {
  const video = schema.nodes.productVideo.create({ storageKey: videoKey });
  const document = schema.nodes.doc.create(null, [video]);
  assert.equal(countProductVideos(schema.nodeFromJSON(document.toJSON())), 1);
  const plugin = ProductContentVideo.config.addProseMirrorPlugins.call(ProductContentVideo)[0];
  const three = schema.nodes.doc.create(null, [video, video, video]);
  const four = schema.nodes.doc.create(null, [video, video, video, video]);
  assert.equal(plugin.spec.filterTransaction({ docChanged: true, doc: three }, { doc: document }), true);
  assert.equal(plugin.spec.filterTransaction({ docChanged: true, doc: four }, { doc: three }), false);
  assert.equal(plugin.spec.filterTransaction({ docChanged: true, doc: three }, { doc: four }), true);
});

test("current CDN resolves keys, legacy URLs remain unchanged, unsafe keys/sources are rejected", () => {
  assert.equal(media.resolveProductMediaSource(imageKey, null, "img"), `https://cdn.example/media/${imageKey}`);
  assert.equal(media.resolveProductMediaSource(videoKey, null, "video"), `https://cdn.example/media/${videoKey}`);
  assert.equal(media.resolveProductMediaSource(null, "http://localhost:9000/gift-market/old.png", "img"), "http://localhost:9000/gift-market/old.png");
  for (const key of ["../secret", "https://evil.example", "profiles/12/a.png", "products/12/content/../../secret"]) {
    assert.equal(media.resolveProductMediaSource(key, null, "img"), null);
  }
  assert.equal(media.resolveProductMediaSource(null, "javascript:alert(1)", "video"), null);
});
