import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";
import ts from "typescript";

// Execute the actual components with isolated hook state and navigation, without API/auth calls.
function component(path, modules) {
  const source = readFileSync(new URL(path, import.meta.url), "utf8");
  const compiled = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
  } }).outputText;
  const slots = [];
  let cursor = 0;
  let effects = [];
  const hooks = {
    Suspense: "Suspense",
    useState(initial) {
      const index = cursor++;
      if (!slots[index]) {
        const slot = { value: initial };
        slot.set = (next) => { slot.value = typeof next === "function" ? next(slot.value) : next; };
        slots[index] = slot;
      }
      return [slots[index].value, slots[index].set];
    },
    useEffect(callback, deps) {
      const index = cursor++;
      const old = slots[index];
      if (!old || deps.some((value, i) => !Object.is(value, old.deps[i]))) {
        effects.push(() => {
          old?.cleanup?.();
          slots[index] = { deps, cleanup: callback() };
        });
      }
    },
  };
  const jsx = (type, props, key) => ({ type, props, key });
  const context = { exports: {},
    window: { matchMedia: () => ({ matches: false, addEventListener() {}, removeEventListener() {} }) },
    require(name) {
      if (name === "react") return hooks;
      if (name === "react/jsx-runtime") return { jsx, jsxs: jsx, Fragment: "Fragment" };
      if (name in modules) return typeof modules[name] === "string" ? { default: modules[name] } : modules[name];
      throw new Error(`Unexpected import: ${name}`);
    },
  };
  vm.runInNewContext(compiled, context);
  return (props = {}) => {
    cursor = 0;
    effects = [];
    const tree = context.exports.default(props);
    effects.forEach((effect) => effect());
    return tree;
  };
}

function find(tree, type) {
  if (!tree || typeof tree !== "object") return [];
  if (Array.isArray(tree)) return tree.flatMap((child) => find(child, type));
  return [...(tree.type === type ? [tree] : []), ...find(tree.props?.children, type)];
}

function setup(initial) {
  let url = new URL(initial, "https://shop.example");
  const pushes = [];
  const navigation = { usePathname: () => url.pathname,
    useRouter: () => ({ push: (path) => pushes.push(path) }),
    useSearchParams: () => new URLSearchParams(url.search) };
  const header = component("../components/layout/Header.tsx", {
    "next/navigation": navigation, "next/image": "Image", "next/link": "Link",
    "@/stores/auth-store": { useAuthStore: (select) => select({ user: null, isAuthenticated: false }) },
    "@/components/notification/NotificationBell": "NotificationBell",
    "@/types/user": { roleLabel: {} }, "@/utils/image-url": { resolveImageUrl: () => null },
    "@/lib/role": { isAdminRole: () => false },
    "@/components/common/SearchBox": "SearchBox",
    "@/components/layout/HeaderSearchSync": "HeaderSearchSync",
  });
  const sync = component("../components/layout/HeaderSearchSync.tsx", { "next/navigation": navigation });
  function render() {
    const tree = header();
    sync(find(tree, "HeaderSearchSync")[0].props);
    return find(header(), "SearchBox");
  }
  return { render, pushes, go(path) { url = new URL(path, url); return render(); } };
}

function values(boxes) { return boxes.map((box) => box.props.value); }

test("desktop and mobile clear on home, my page and detail, even with a stray keyword parameter", () => {
  for (const destination of ["/", "/my", "/products/65", "/my?keyword=침구", "/products"]) {
    const app = setup("/products?keyword=침구");
    const before = app.render();
    assert.deepEqual(values(before), ["침구", "침구"]);
    const after = app.go(destination);
    assert.deepEqual(values(after), ["", ""]);
    // A different React key discards both suggestion lists and runs their abort cleanup.
    after.forEach((box, index) => assert.notEqual(box.key, before[index].key));
  }
});

test("direct entry, filtering, sorting and pagination use the URL keyword on both search boxes", () => {
  const app = setup("/products?keyword=%20침구%20");
  assert.deepEqual(values(app.render()), ["침구", "침구"]);
  for (const suffix of ["&sort=PRICE_ASC", "&categoryIds=33", "&page=2&size=10", "&excludeSoldOut=true"]) {
    assert.deepEqual(values(app.go(`/products?keyword=침구${suffix}`)), ["침구", "침구"]);
  }
});

test("typing stays editable across rerenders and is shared between desktop and mobile", () => {
  const app = setup("/");
  let boxes = app.render();
  boxes[0].props.onChange("침");
  boxes = app.render();
  assert.deepEqual(values(boxes), ["침", "침"]);
  const keys = boxes.map((box) => box.key);
  boxes[1].props.onChange("침구류");
  boxes = app.render();
  assert.deepEqual(values(boxes), ["침구류", "침구류"]);
  assert.deepEqual(boxes.map((box) => box.key), keys); // Typing must not remount/debounce-reset the input.
  boxes[1].props.onSearch(" 침구류 ");
  assert.deepEqual(app.pushes, [`/products?keyword=${encodeURIComponent("침구류")}`]);
  assert.deepEqual(values(app.go(app.pushes[0])), ["침구류", "침구류"]);
});

test("back/forward and changed keyword restore URL state instead of an unsent draft", () => {
  const app = setup("/products?keyword=침구");
  app.render()[0].props.onChange("아직 검색하지 않은 값");
  for (const [path, expected] of [["/my", ""], ["/products?keyword=침구", "침구"],
    ["/products?keyword=침구류", "침구류"], ["/products?keyword=침구", "침구"], ["/my", ""]]) {
    assert.deepEqual(values(app.go(path)), [expected, expected]);
  }
});
