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
const phoneUtils = {};
vm.runInNewContext(
  ts.transpileModule(
    fs.readFileSync(resolve(import.meta.dirname, "../utils/phone.ts"), "utf8"),
    { compilerOptions: { module: ts.ModuleKind.CommonJS } },
  ).outputText,
  { exports: phoneUtils },
);
const imageUtils = {};
vm.runInNewContext(
  ts.transpileModule(
    fs.readFileSync(
      resolve(import.meta.dirname, "../utils/image-url.ts"),
      "utf8",
    ),
    { compilerOptions: { module: ts.ModuleKind.CommonJS } },
  ).outputText,
  {
    exports: imageUtils,
    URL,
    process: {
      env: {
        NEXT_PUBLIC_STORAGE_BASE_URL: "https://storage.example.test/bucket///",
        NODE_ENV: "production",
      },
    },
  },
);
const publicUrl = (key) => imageUtils.resolveImageUrl(key);
const store = {
  id: 7,
  storeName: "original",
  introduction: "intro",
  logoImageKey: "logo",
  bannerImageKey: "banner",
  customerServicePhone: null,
  customerServiceEmail: null,
  customerServiceOpenTime: null,
  customerServiceCloseTime: null,
  customerServiceClosedDays: null,
  customerServiceNote: null,
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
  const createdPreviews = [];
  const revokedPreviews = [];
  vm.runInNewContext(code, {
    exports,
    Error,
    URL: {
      createObjectURL: (file) => {
        const url = `blob:preview/${createdPreviews.length + 1}`;
        createdPreviews.push({ url, file });
        return url;
      },
      revokeObjectURL: (url) => revokedPreviews.push(url),
    },
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
      if (name === "@/utils/image-url") return imageUtils;
      if (name === "@/utils/phone") return phoneUtils;
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
  const cleanups = effects.map((effect) => effect());
  return {
    createdPreviews,
    revokedPreviews,
    unmount: () => cleanups.forEach((cleanup) => cleanup?.()),
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

function selectImage(page, field) {
  page
    .find((node) => node.props?.id === field)
    .props.onChange({
      currentTarget: {
        files: [{ name: "preview.png", type: "image/png" }],
        value: "preview.png",
      },
    });
  page.render();
  return page.createdPreviews.at(-1).url;
}

test("immediate local previews switch to response public URLs on save and reload", async () => {
  assert.equal(
    publicUrl("///stores/7/logo/image.png"),
    "https://storage.example.test/bucket/stores/7/logo/image.png",
  );
  for (const field of ["logoImageKey", "bannerImageKey"]) {
    const upload = deferred();
    const savedStore = { ...store, [field]: "/stores/7/canonical.png" };
    let request;
    const page = mount({
      upload: () => upload.promise,
      updateSellerStore: async (payload) => {
        request = payload;
        return savedStore;
      },
    });
    await tick();
    page.render();
    page.click("수정");
    page.render();
    const preview = selectImage(page, field);
    assert.ok(
      page
        .all()
        .some((node) => node.props?.src === preview && node.props.unoptimized),
    );
    assert.equal(
      page.find((node) => node.props?.type === "submit").props.disabled,
      true,
    );
    upload.resolve("stores/7/uploaded.png");
    await tick();
    page.render();
    assert.ok(page.all().some((node) => node.props?.src === preview));
    assert.equal(page.revokedPreviews.length, 0);
    await page.save();
    page.render();
    assert.equal(request[field], "stores/7/uploaded.png");
    assert.ok(
      Object.values(request).every(
        (value) => !String(value).startsWith("blob:"),
      ),
    );
    assert.ok(page.revokedPreviews.includes(preview));
    assert.ok(
      page
        .all()
        .some(
          (node) =>
            node.props?.src === publicUrl(savedStore[field]) &&
            node.props.unoptimized,
        ),
    );
    const reloaded = mount({ getSellerStore: async () => savedStore });
    await tick();
    reloaded.render();
    assert.ok(
      reloaded
        .all()
        .some((node) => node.props?.src === publicUrl(savedStore[field])),
    );
  }
});

test("preview cleanup covers replacement, deletion, cancellation, unmount and late upload responses", async () => {
  for (const [field, label] of [
    ["logoImageKey", "로고"],
    ["bannerImageKey", "배너"],
  ]) {
    const lateUpload = deferred();
    let count = 0;
    const page = mount({
      upload: () =>
        ++count < 3 ? Promise.resolve("new-key") : lateUpload.promise,
    });
    await tick();
    page.render();
    page.click("수정");
    page.render();
    const first = selectImage(page, field);
    await tick();
    page.render();
    const second = selectImage(page, field);
    assert.ok(page.revokedPreviews.includes(first));
    await tick();
    page.render();
    page.click(label + " 삭제");
    page.render();
    assert.ok(page.revokedPreviews.includes(second));
    assert.ok(
      !page.all().some((node) => node.props?.alt === "스토어 " + label),
    );
    page.click("취소");
    page.render();
    assert.ok(
      page.all().some((node) => node.props?.src === publicUrl(store[field])),
    );
    page.click("수정");
    page.render();
    const canceled = selectImage(page, field);
    page.click("취소");
    page.render();
    assert.ok(page.revokedPreviews.includes(canceled));
    page.click("수정");
    page.render();
    lateUpload.resolve("late-key");
    await tick();
    page.render();
    assert.ok(
      page.all().some((node) => node.props?.src === publicUrl(store[field])),
    );
    const last = selectImage(page, field);
    page.unmount();
    await tick();
    assert.ok(page.revokedPreviews.includes(last));
  }
});

test("failed uploads restore the previous image and failed saves retain previews with image error placeholders", async () => {
  const failure = mount({
    upload: async () => {
      throw Error("upload failed");
    },
  });
  await tick();
  failure.render();
  failure.click("수정");
  failure.render();
  const failedPreview = selectImage(failure, "logoImageKey");
  await tick();
  failure.render();
  assert.ok(failure.revokedPreviews.includes(failedPreview));
  assert.ok(
    failure
      .all()
      .some((node) => node.props?.src === publicUrl(store.logoImageKey)),
  );
  const page = mount({
    upload: async () => "uploaded-key",
    updateSellerStore: async () => {
      throw Error("save failed");
    },
  });
  await tick();
  page.render();
  page.click("수정");
  page.render();
  const preview = selectImage(page, "bannerImageKey");
  await tick();
  page.render();
  await page.save();
  page.render();
  assert.ok(page.all().some((node) => node.props?.src === preview));
  assert.ok(!page.revokedPreviews.includes(preview));
  for (const alt of ["스토어 로고", "스토어 배너"]) {
    page.find((node) => node.props?.alt === alt).props.onError();
    page.render();
    assert.ok(!page.all().some((node) => node.props?.alt === alt));
  }
});

test("weekday toggles restore enum values and save ordered CSV with Korean read labels", async () => {
  let request;
  const page = mount({
    getSellerStore: async () => ({
      ...store,
      customerServiceClosedDays: " SUNDAY, SATURDAY,SUNDAY ",
    }),
    updateSellerStore: async (payload) => {
      request = payload;
      return { ...store, ...payload };
    },
  });
  await tick();
  page.render();
  assert.ok(
    page
      .all()
      .some(
        (node) =>
          node.type === "dd" && node.props.children === "토요일, 일요일",
      ),
  );
  page.click("수정");
  page.render();
  assert.ok(
    !page.all().some((node) => node.props?.id === "customerServiceClosedDays"),
  );
  for (const label of ["토요일", "일요일"]) {
    assert.equal(
      page.find(
        (node) => node.type === "button" && node.props["aria-label"] === label,
      ).props["aria-pressed"],
      true,
    );
  }
  for (const label of ["월", "화", "수", "목", "금"]) {
    page.click(label);
    page.render();
  }
  await page.save();
  page.render();
  assert.equal(
    request.customerServiceClosedDays,
    "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY",
  );
  page.click("수정");
  page.render();
  for (const label of ["월", "화", "수", "목", "금", "토", "일"]) {
    page.click(label);
    page.render();
  }
  await page.save();
  page.render();
  assert.equal(request.customerServiceClosedDays, null);
});

test("both time fields open the picker on click and preserve unsupported and manual-input fallbacks", async () => {
  const page = mount();
  await tick();
  page.render();
  page.click("수정");
  page.render();
  for (const field of ["customerServiceOpenTime", "customerServiceCloseTime"]) {
    const input = page.find((node) => node.props?.id === field);
    assert.equal(input.props.type, "time");
    let calls = 0;
    input.props.onClick({
      currentTarget: {
        showPicker() {
          calls++;
        },
      },
    });
    assert.equal(calls, 1);
    assert.doesNotThrow(() => input.props.onClick({ currentTarget: {} }));
    assert.doesNotThrow(() =>
      input.props.onClick({
        currentTarget: {
          showPicker() {
            throw Error("unsupported");
          },
        },
      }),
    );
    input.props.onClick({
      currentTarget: {
        disabled: true,
        showPicker() {
          calls++;
        },
      },
    });
    assert.equal(calls, 1);
    page.input(field, "13:30");
    page.render();
    assert.equal(
      page.find((node) => node.props?.id === field).props.value,
      "13:30",
    );
  }
});
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
  assert.equal(
    h.find((n) => n.props?.id === "customerServiceEmail").props.type,
    "email",
  );
  for (const [value, expected] of [
    ["01012345678", "010-1234-5678"],
    ["0212345678", "02-1234-5678"],
    ["0311234567", "031-123-4567"],
    ["15881234", "1588-1234"],
    ["010 abc 1234 5678", "010-1234-5678"],
    ["", ""],
  ]) {
    h.input("customerServicePhone", value);
    h.render();
    assert.equal(
      h.find((n) => n.props?.id === "customerServicePhone").props.value,
      expected,
    );
  }
  for (const [field, label] of [
    ["logoImageKey", "로고"],
    ["bannerImageKey", "배너"],
  ]) {
    let selected = false;
    h.find((n) => n.props?.id === field).props.ref({
      click: () => {
        selected = true;
      },
    });
    h.click(`${label} 변경`);
    assert.equal(selected, true);
  }
  h.input("customerServiceOpenTime", "09:00");
  h.render();
  h.input("customerServiceCloseTime", "18:00");
  h.render();
  h.click("토");
  h.render();
  h.input("customerServiceNote", "lunch break");
  h.render();
  h.input("storeName", "changed");
  h.render();
  h.click("로고 삭제");
  h.render();
  h.click("배너 삭제");
  h.render();
  assert.ok(
    h
      .all()
      .some(
        (n) =>
          n.type === "button" && n.props.children.join?.("") === "로고 추가",
      ),
  );
  assert.ok(
    h
      .all()
      .some(
        (n) =>
          n.type === "button" && n.props.children.join?.("") === "배너 추가",
      ),
  );
  h.click("취소");
  h.render();
  h.click("수정");
  h.render();
  assert.equal(
    h.find((n) => n.props?.id === "storeName").props.value,
    "original",
  );
  assert.ok(h.all().some((n) => n.props?.src === publicUrl("logo")));
  assert.ok(h.all().some((n) => n.props?.src === publicUrl("banner")));
  for (const field of [
    "customerServiceOpenTime",
    "customerServiceCloseTime",
    "customerServiceNote",
  ]) {
    assert.equal(h.find((n) => n.props?.id === field).props.value, "");
  }

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
  assert.ok(u.all().some((n) => n.props?.src === publicUrl("logo")));
  assert.ok(!u.all().some((n) => n.props?.src === publicUrl("late")));

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
  s.input("customerServiceOpenTime", "09:00");
  s.render();
  s.input("customerServiceCloseTime", "18:00");
  s.render();
  s.click("토");
  s.render();
  s.click("일");
  s.render();
  s.input("customerServiceNote", "lunch break");
  s.render();
  const pending = s.save();
  s.render();
  assert.equal(s.find((n) => n.type === "fieldset").props.disabled, true);
  assert.equal(request.storeName, "trimmed");
  assert.equal(request.customerServiceOpenTime, "09:00");
  assert.equal(request.customerServiceCloseTime, "18:00");
  assert.equal(request.customerServiceClosedDays, "SATURDAY,SUNDAY");
  assert.equal(request.customerServiceNote, "lunch break");
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
    assert.ok(
      uploaded
        .all()
        .some((n) => n.props?.src === uploaded.createdPreviews[0].url),
    );
    uploaded.click("취소");
    uploaded.render();
    assert.ok(
      !uploaded
        .all()
        .some((n) => n.props?.src === uploaded.createdPreviews[0].url),
    );
  }
  assert.deepEqual(Object.keys(request).sort(), [
    "bannerImageKey",
    "customerServiceCloseTime",
    "customerServiceClosedDays",
    "customerServiceEmail",
    "customerServiceNote",
    "customerServiceOpenTime",
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
