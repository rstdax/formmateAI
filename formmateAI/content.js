const FORMMATE_ID_ATTRIBUTE = "data-formmate-id";

function labelForElement(element) {
  if (element.labels?.[0]?.textContent) {
    return element.labels[0].textContent.trim();
  }
  const labelledBy = element.getAttribute("aria-label");
  if (labelledBy) return labelledBy.trim();
  const name = element.getAttribute("name") || element.id;
  return name ? name.replace(/[-_]+/g, " ") : "form field";
}

function semanticTypeForElement(element) {
  const value = [
    element.name,
    element.id,
    element.type,
    element.getAttribute("autocomplete"),
    labelForElement(element),
  ]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();

  if (value.includes("email")) return "email";
  if (value.includes("phone") || value.includes("mobile")) return "phone";
  if (value.includes("birth") || element.type === "date") return "date_of_birth";
  if (value.includes("first")) return "first_name";
  if (value.includes("last") || value.includes("surname")) return "last_name";
  if (value.includes("name")) return "full_name";
  if (value.includes("pin") || value.includes("zip")) return "pincode";
  if (value.includes("address")) return "address";
  if (value.includes("city")) return "city";
  if (value.includes("district")) return "district";
  if (value.includes("state")) return "state";
  if (value.includes("gender") || value.includes("sex")) return "gender";
  if (value.includes("course") || value.includes("program")) return "course";
  if (element.type === "checkbox") return "consent";
  return "general";
}

function fieldKind(element) {
  if (element.tagName === "SELECT") return "select";
  if (element.type === "radio") return "radio";
  if (element.type === "checkbox") return "checkbox";
  return element.tagName === "TEXTAREA" ? "textarea" : "text";
}

function optionsForElement(element) {
  if (element.tagName === "SELECT") {
    return Array.from(element.options).map((option) => ({
      label: option.textContent.trim(),
      value: option.value,
    }));
  }
  return [];
}

function scanForm() {
  const elements = Array.from(
    document.querySelectorAll("input, textarea, select"),
  ).filter((element) => {
    if (element.disabled || element.type === "hidden") return false;
    return element.type !== "submit" && element.type !== "button";
  });

  return elements.map((element, index) => {
    const id = `formmate-field-${index + 1}`;
    element.setAttribute(FORMMATE_ID_ATTRIBUTE, id);
    return {
      id,
      label: labelForElement(element),
      semanticType: semanticTypeForElement(element),
      kind: fieldKind(element),
      inputType: element.type || "text",
      options: optionsForElement(element),
      required: element.required,
      currentValue:
        element.type === "checkbox"
          ? element.checked
            ? "Yes"
            : ""
          : element.value,
    };
  });
}

function setElementValue(element, value) {
  if (element.type === "checkbox") {
    element.checked = /^(yes|true|checked|on|agree|accept)/i.test(String(value));
  } else if (element.type === "radio") {
    element.checked = element.value === String(value);
  } else {
    const prototype = Object.getPrototypeOf(element);
    const descriptor = Object.getOwnPropertyDescriptor(prototype, "value");
    descriptor?.set?.call(element, String(value));
  }
  element.dispatchEvent(new Event("input", { bubbles: true }));
  element.dispatchEvent(new Event("change", { bubbles: true }));
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type === "FORMMATE_SCAN_PAGE") {
    sendResponse({ fields: scanForm() });
    return false;
  }
  if (message?.type === "FORMMATE_FILL_FIELD") {
    const element = document.querySelector(
      `[${FORMMATE_ID_ATTRIBUTE}="${CSS.escape(String(message.fieldId))}"]`,
    );
    if (!element) {
      sendResponse({ ok: false, error: "Field is no longer on the page." });
      return false;
    }
    setElementValue(element, message.value);
    sendResponse({ ok: true });
  }
  return false;
});
