import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";

const source = readFileSync(
  resolve(import.meta.dirname, "../components/seller/ProductForm.tsx"),
  "utf8",
);

function sectionBetween(start, end) {
  const startIndex = source.indexOf(start);
  const endIndex = source.indexOf(end, startIndex);
  assert.notEqual(startIndex, -1, `${start} section is missing`);
  assert.notEqual(endIndex, -1, `${end} section is missing`);
  return source.slice(startIndex, endIndex);
}

test("registration validation follows the product form field order before submitting", () => {
  const validation = sectionBetween(
    "const validateRequiredRegistrationFields",
    "const createCommonRequest",
  );

  const orderedFields = [
    "form.categoryId",
    "form.name.trim()",
    "representativeImage",
    "form.description.trim()",
    "form.price",
    "form.stockQuantity",
    "form.shippingFee",
    "form.shippingPreparationDays",
    "form.returnShippingFee",
    "form.exchangeShippingFee",
  ];

  let previousIndex = -1;
  for (const field of orderedFields) {
    const index = validation.indexOf(field, previousIndex + 1);
    assert.ok(index > previousIndex, `${field} must be validated in form order`);
    previousIndex = index;
  }
});

test("request construction consumes prevalidated values and runs before lock-protected submission", () => {
  const commonRequest = sectionBetween(
    "const createCommonRequest",
    "const validateOptionConfiguration",
  );
  assert.doesNotMatch(commonRequest, /parseRequiredNumber\(/);

  const submit = sectionBetween("const handleSave", "const handleSubmit");
  assert.ok(
    submit.indexOf("validateRequiredRegistrationFields(startSale)") <
      submit.indexOf("submissionLockRef.current = true"),
  );
  assert.ok(
    submit.indexOf("createCommonRequest(") < submit.indexOf("await uploadImages()"),
  );
});
