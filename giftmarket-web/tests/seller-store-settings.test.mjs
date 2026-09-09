import assert from "node:assert/strict";
import fs from "node:fs";
import { resolve } from "node:path";
import test from "node:test";
import vm from "node:vm";
import ts from "typescript";

const code = ts.transpileModule(
  fs.readFileSync(
    resolve(
      import.meta.dirname,
      "../app/seller/(seller-center)/settings/page.tsx",
    ),
    "utf8",
  ),
  {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      jsx: ts.JsxEmit.ReactJSX,
    },
  },
).outputText;
const store = {
  id: 7,
  storeName: "original",
  introduction: "intro",
  logoImageKey: "logo",
  bannerImageKey: "banner",
  customerServicePhone: null,
  customerServiceEmail: null,
  customerServiceHours: null,
};
function deferred() {
  let resolve, reject;
  const promise = new Promise((a, b) => {
    resolve = a;
    reject = b;
  });
  return { promise, resolve, reject };
}
function mount(overrides = {}) {
  // 기존 node:test 환경에서 페이지 핸들러와 렌더 분기를 검증한다.
  // React DOM/browser E2E가 아니며 외부 API와 hook 실행은 격리한다.
  let i = 0,
    tree;
  const state = [],
    effects = [],
    exports = {};
  const jsx = (type, props) => ({ type, props });
  vm.runInNewContext(code, {
    exports,
    Error,
    require: (name) => {
      if (name === "react")
        return {
          useState: (v) => {
            const n = i++;
            if (!(n in state)) state[n] = v;
            return [
              state[n],
              (v) => {
                state[n] = typeof v === "function" ? v(state[n]) : v;
              },
            ];
          },
          useRef: (v) => {
            const n = i++;
            return state[n] ?? (state[n] = { current: v });
          },
          useEffect: (f) => {
            const n = i++;
            if (!state[n]) {
              state[n] = true;
              effects.push(f);
            }
          },
        };
      if (name === "react/jsx-runtime")
        return { jsx, jsxs: jsx, Fragment: "fragment" };
      if (name === "next/image") return { default: "img" };
      if (name === "@/lib/seller-api")
        return {
          getSellerStore: async () => store,
          updateSellerStore: async (r) => ({ ...r, id: 7 }),
          ...overrides,
        };
      if (name === "@/lib/storage-api")
        return {
          uploadStoreLogo: overrides.upload,
          uploadStoreBanner: overrides.upload,
        };
      if (name === "@/utils/image-url")
        return { resolveImageUrl: (k) => k || null };
      throw Error(name);
    },
  });
  const render = () => {
    i = 0;
    tree = exports.default();
  };
  const walk = (n) =>
    !n || typeof n !== "object"
      ? []
      : Array.isArray(n)
        ? n.flatMap(walk)
        : [n, ...walk(n.props?.children)];
  const all = () => walk(tree),
    find = (p) => {
      const n = all().find(p);
      assert.ok(n, "element exists");
      return n;
    };
  const text = (n) =>
    Array.isArray(n) ? n.map(text).join("") : typeof n === "string" ? n : "";
  render();
  effects.forEach((f) => f());
  return {
    render,
    all,
    find,
    click: (t) =>
      find(
        (n) => n.type === "button" && text(n.props.children) === t,
      ).props.onClick(),
    input: (id, value) =>
      find((n) => n.props?.id === id).props.onChange({
        currentTarget: { value },
      }),
    save: () =>
      find((n) => n.type === "form").props.onSubmit({ preventDefault() {} }),
  };
}
const tick = () => new Promise((r) => setImmediate(r));
test("SellerStore read/edit/save/upload regressions", async () => {
  const h = mount();
  await tick();
  h.render();
  assert.equal(
    h.all().filter((n) => ["input", "textarea"].includes(n.type)).length,
    0,
  );
  h.click("수정");
  h.render();
  h.input("storeName", "changed");
  h.render();
  h.click("로고 삭제");
  h.render();
  h.click("배너 삭제");
  h.render();
  h.click("취소");
  h.render();
  h.click("수정");
  h.render();
  assert.equal(
    h.find((n) => n.props?.id === "storeName").props.value,
    "original",
  );
  assert.ok(h.all().some((n) => n.props?.src === "logo"));
  assert.ok(h.all().some((n) => n.props?.src === "banner"));

  const d = deferred(),
    u = mount({ upload: () => d.promise });
  await tick();
  u.render();
  u.click("수정");
  u.render();
  u.find((n) => n.props?.id === "logoImageKey").props.onChange({
    currentTarget: { files: [{}], value: "file" },
  });
  u.render();
  assert.equal(u.find((n) => n.props?.type === "submit").props.disabled, true);
  u.click("취소");
  u.render();
  u.click("수정");
  u.render();
  d.resolve("late");
  await tick();
  u.render();
  assert.ok(u.all().some((n) => n.props?.src === "logo"));
  assert.ok(!u.all().some((n) => n.props?.src === "late"));

  const f = mount({
    updateSellerStore: async () => {
      throw Error("failed");
    },
  });
  await tick();
  f.render();
  f.click("수정");
  f.render();
  f.input("storeName", "retained");
  f.render();
  await f.save();
  f.render();
  assert.equal(
    f.find((n) => n.props?.id === "storeName").props.value,
    "retained",
  );
  assert.ok(f.all().some((n) => n.props?.role === "alert"));

  let request;
  const d2 = deferred(),
    s = mount({
      updateSellerStore: (r) => {
        request = r;
        return d2.promise;
      },
    });
  await tick();
  s.render();
  s.click("수정");
  s.render();
  s.input("storeName", "  trimmed  ");
  s.render();
  const pending = s.save();
  s.render();
  assert.equal(s.find((n) => n.type === "fieldset").props.disabled, true);
  assert.equal(request.storeName, "trimmed");
  assert.equal(Object.hasOwn(request, "id"), false);
  d2.resolve({ ...store, storeName: "server response" });
  await pending;
  s.render();
  assert.equal(s.all().filter((n) => n.type === "input").length, 0);
  s.click("수정");
  s.render();
  assert.equal(
    s.find((n) => n.props?.id === "storeName").props.value,
    "server response",
  );

  for (const field of ["logoImageKey", "bannerImageKey"]) {
    const uploaded = mount({ upload: async () => field + "-new" });
    await tick();
    uploaded.render();
    uploaded.click("수정");
    uploaded.render();
    uploaded
      .find((n) => n.props?.id === field)
      .props.onChange({ currentTarget: { files: [{}], value: "file" } });
    await tick();
    uploaded.render();
    assert.ok(uploaded.all().some((n) => n.props?.src === field + "-new"));
    uploaded.click("취소");
    uploaded.render();
    assert.ok(!uploaded.all().some((n) => n.props?.src === field + "-new"));
  }
  assert.deepEqual(Object.keys(request).sort(), [
    "bannerImageKey",
    "customerServiceEmail",
    "customerServiceHours",
    "customerServicePhone",
    "introduction",
    "logoImageKey",
    "storeName",
  ]);
  assert.ok(Object.values(request).every((value) => value !== undefined));
  const l = mount({
    getSellerStore: async () => {
      throw Error("load failed");
    },
  });
  await tick();
  l.render();
  assert.equal(
    l.all().filter((n) => n.type === "button" || n.type === "form").length,
    0,
  );
  assert.ok(l.all().some((n) => n.props?.role === "alert"));
});
