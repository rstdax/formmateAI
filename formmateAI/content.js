(() => {
  if (window.__FORMMATE_BRIDGE_INSTALLED__) return;
  window.__FORMMATE_BRIDGE_INSTALLED__ = true;

  const registry = new Map();
  let nextId = 1;

  const Core = window.FormmateCore || {
    normalizeText: (v) => String(v || "").toLowerCase().replace(/[^a-z0-9]+/g, " ").trim(),
    matchOption: (options, val) => {
      const w = String(val || "").toLowerCase().trim();
      const match = options.find((o) => o.label.toLowerCase() === w || o.value.toLowerCase() === w);
      return { matched: match || null };
    },
    parseBooleanAnswer: (v) => {
      const s = String(v || "").toLowerCase();
      if (/\b(no|not|disagree|dont)\b/.test(s)) return false;
      if (/\b(yes|agree|accept|correct)\b/.test(s)) return true;
      return null;
    },
    parseSpokenDate: (v) => v,
    parseSpokenEmail: (v) => v,
    parseSpokenPhone: (v) => v,
    parseSpokenDigits: (v) => v,
    normalizeSpeechAnswer: (v) => v,
    stripConversationalFraming: (v) => v
  };

  const humanize = (value) =>
    String(value || "")
      .replace(/([a-z])([A-Z])/g, "$1 $2")
      .replace(/[_\-./]+/g, " ")
      .replace(/\s+/g, " ")
      .trim()
      .replace(/\b\w/g, (c) => c.toUpperCase());

  const norm = (value) =>
    String(value || "")
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, " ")
      .trim();

  function isVisible(el) {
    if (!el || !el.isConnected) return false;
    const style = window.getComputedStyle(el);
    if (style.display === "none" || style.visibility === "hidden" || Number(style.opacity) === 0) return false;
    if (el.hidden || el.getAttribute("aria-hidden") === "true") return false;
    const rect = el.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  }

  function getTextFromIds(el) {
    const ids = (el.getAttribute("aria-labelledby") || "").split(/\s+/).filter(Boolean);
    return ids.map((id) => document.getElementById(id)?.innerText || "").join(" ").trim();
  }

  function nearbyText(el) {
    const parent = el.closest("fieldset, .form-group, .form-field, .field, .form-row, .input-group, li, td, p, label");
    if (parent) {
      const text = (parent.innerText || parent.textContent || "").replace(/\s+/g, " ").trim();
      if (text.length > 0 && text.length < 180) return text;
    }
    const parentDiv = el.parentElement;
    if (parentDiv && parentDiv.children.length <= 5) {
      const text = (parentDiv.innerText || parentDiv.textContent || "").replace(/\s+/g, " ").trim();
      if (text.length > 0 && text.length < 120) return text;
    }
    return "";
  }

  function labelFor(el) {
    const explicit = (el.labels ? Array.from(el.labels).map((l) => l.innerText || l.textContent || "").join(" ") : "").trim();
    const aria = (el.getAttribute("aria-label") || "").trim();
    const labelledBy = getTextFromIds(el);
    const placeholder = (el.getAttribute("placeholder") || "").trim();
    const title = (el.getAttribute("title") || "").trim();
    const dataLabel = (el.getAttribute("data-voice-field") || el.getAttribute("data-formmate-field") || "").trim();
    const nearby = nearbyText(el);
    const name = humanize(el.getAttribute("name") || "");
    const id = humanize(el.id || "");
    const choices = [
      [dataLabel, 0.99],
      [explicit, 0.96],
      [aria, 0.94],
      [labelledBy, 0.93],
      [placeholder, 0.82],
      [title, 0.76],
      [nearby, 0.69],
      [name, 0.59],
      [id, 0.53]
    ];
    const chosen = choices.find(([text]) => text && String(text).trim());
    let label = chosen ? String(chosen[0]).trim() : "Unlabelled field";
    label = label.replace(/\s+/g, " ").replace(/[*:]+$/g, "").trim();
    if (label.length > 90) label = label.slice(0, 87) + "…";
    return { label, confidence: chosen ? chosen[1] : 0.35 };
  }

  function semanticType(label, el) {
    const text = norm([
      label,
      el.getAttribute("name"),
      el.id,
      el.getAttribute("autocomplete"),
      el.getAttribute("data-voice-field"),
      el.getAttribute("data-formmate-field")
    ].join(" "));
    const type = (el.type || "").toLowerCase();

    if (type === "email" || /\b(e mail|email|mail address)\b/.test(text)) return "email";
    if (type === "tel" || /\b(phone|mobile|telephone|contact number|whatsapp number)\b/.test(text)) return "phone";
    if (/\b(full name|applicant name|your name|name of applicant|student name)\b/.test(text)) return "full_name";
    if (/\b(first name|given name|forename)\b/.test(text)) return "first_name";
    if (/\b(last name|surname|family name)\b/.test(text)) return "last_name";
    if (/\b(date of birth|birth date|dob|born)\b/.test(text)) return "date_of_birth";
    if (type === "date" || /\b(date)\b/.test(text)) return "date";
    if (/\b(age|years old)\b/.test(text)) return "age";
    if (/\b(pin ?code|postal code|postcode|zip code|pincode)\b/.test(text)) return "pincode";
    if (/\b(address|street|locality|village|house number|residential address)\b/.test(text)) return "address";
    if (/\b(city|town|place of residence)\b/.test(text)) return "city";
    if (/\b(district)\b/.test(text)) return "district";
    if (/\b(state|province)\b/.test(text)) return "state";
    if (/\b(country|nation)\b/.test(text)) return "country";
    if (/\b(gender|sex)\b/.test(text)) return "gender";
    if (/\b(occupation|profession|job title)\b/.test(text)) return "occupation";
    if (/\b(course|preferred course|major|stream)\b/.test(text)) return "course";
    if (/\b(education|qualification|degree)\b/.test(text)) return "education";
    if (/\b(college|school|institution)\b/.test(text)) return "institution";
    if (/\b(guardian|father|mother|parent)\b/.test(text)) return "guardian_name";
    if (/\b(agree|consent|terms|declaration|accept)\b/.test(text) || type === "checkbox") return "consent";
    if (type === "number") return "number";
    return "general";
  }

  function fieldsetLabel(el) {
    const fieldset = el.closest("fieldset");
    const legend = fieldset?.querySelector("legend");
    return (legend?.innerText || legend?.textContent || "").trim();
  }

  function optionLabel(el) {
    const labels = el.labels ? Array.from(el.labels).map((label) => label.innerText || label.textContent || "").join(" ") : "";
    if (labels.trim()) return labels.trim();
    const value = el.value;
    return value ? humanize(value) : "Option";
  }

  function currentValueFor(item) {
    if (item.kind === "radio") {
      const selected = item.elements.find((el) => el.checked);
      return selected ? optionLabel(selected) : "";
    }
    const el = item.element;
    if (item.kind === "checkbox") return el.checked ? "Yes" : "";
    if (item.kind === "select") {
      const option = el.options[el.selectedIndex];
      return option && option.value !== "" ? (option.textContent || option.value).trim() : "";
    }
    return String(el.value || "").trim();
  }

  function getSelectOptions(selectEl) {
    return Array.from(selectEl.options).map((o) => ({
      value: o.value,
      label: (o.textContent || o.value).trim(),
      disabled: Boolean(o.disabled)
    }));
  }

  function addRegistryItem(item) {
    const id = `fm-field-${nextId++}`;
    item.id = id;

    const elements = item.kind === "radio" ? item.elements : [item.element];
    elements.forEach((el) => el.setAttribute("data-formmate-runtime-id", id));

    const source = item.kind === "radio" ? item.elements[0] : item.element;
    const labelInfo = item.kind === "radio" && fieldsetLabel(source)
      ? { label: fieldsetLabel(source), confidence: 0.93 }
      : labelFor(source);

    const type = item.kind === "radio" ? "radio" : item.kind;
    const sem = semanticType(labelInfo.label, source);
    const required = item.kind === "radio"
      ? item.elements.some((el) => el.required)
      : Boolean(item.element.required || item.element.getAttribute("aria-required") === "true");

    let options = [];
    if (item.kind === "select") {
      options = getSelectOptions(item.element);
      item.options = options; // Stored directly on registry item! Fixes Bug A!
    } else if (item.kind === "radio") {
      options = item.elements.map((el) => ({
        value: el.value,
        label: optionLabel(el),
        disabled: Boolean(el.disabled)
      }));
      item.options = options;
    }

    item.label = labelInfo.label;
    item.semanticType = sem;
    item.required = required;
    item.confidence = labelInfo.confidence;

    registry.set(id, item);

    const currentValue = currentValueFor(item);
    return {
      id,
      label: labelInfo.label,
      kind: type,
      inputType: source.type || type,
      semanticType: sem,
      required,
      confidence: labelInfo.confidence,
      placeholder: source.getAttribute("placeholder") || "",
      options,
      currentValue,
      filled: Boolean(currentValue),
      autocomplete: source.getAttribute("autocomplete") || "",
      multiple: item.kind === "select" && item.element.multiple
    };
  }

  function scanFields() {
    registry.clear();
    nextId = 1;
    const nodes = Array.from(document.querySelectorAll("input, textarea, select"));
    const usable = nodes.filter((el) => {
      const type = (el.type || "").toLowerCase();
      if (["hidden", "password", "file", "submit", "reset", "button", "image"].includes(type)) return false;
      if (el.disabled || el.readOnly || el.getAttribute("aria-disabled") === "true") return false;
      return isVisible(el);
    });

    const radioGroups = new Map();
    const fields = [];
    usable.forEach((el, index) => {
      const type = (el.type || "").toLowerCase();
      if (type === "radio") {
        const formIndex = Array.from(document.forms).indexOf(el.form);
        const groupName = el.name ? `form${formIndex}:${el.name}` : `radio-single-${index}`;
        if (!radioGroups.has(groupName)) radioGroups.set(groupName, []);
        radioGroups.get(groupName).push(el);
        return;
      }
      let kind = "input";
      if (el.tagName.toLowerCase() === "textarea") kind = "textarea";
      else if (el.tagName.toLowerCase() === "select") kind = "select";
      else if (type === "checkbox") kind = "checkbox";
      fields.push(addRegistryItem({ kind, element: el }));
    });

    radioGroups.forEach((elements) => {
      fields.push(addRegistryItem({ kind: "radio", elements }));
    });

    // Preserve visual DOM order
    const order = new Map(usable.map((el, index) => [el, index]));
    fields.sort((a, b) => {
      const aItem = registry.get(a.id);
      const bItem = registry.get(b.id);
      const aEl = aItem.kind === "radio" ? aItem.elements[0] : aItem.element;
      const bEl = bItem.kind === "radio" ? bItem.elements[0] : bItem.element;
      return (order.get(aEl) ?? 0) - (order.get(bEl) ?? 0);
    });

    return fields;
  }

  function setNativeValue(el, value) {
    const proto = Object.getPrototypeOf(el);
    const descriptor = Object.getOwnPropertyDescriptor(proto, "value");
    if (descriptor && descriptor.set) {
      descriptor.set.call(el, value);
    } else {
      el.value = value;
    }
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
  }

  function fillField(fieldId, rawValue) {
    const item = registry.get(fieldId);
    if (!item) {
      return { success: false, error: "This field reference is out of date. Please scan the page again." };
    }

    // Safety check: verify elements are still connected in the DOM
    if (item.kind === "radio") {
      if (!item.elements.every((el) => el && el.isConnected)) {
        return { success: false, error: "The radio controls are no longer connected to the page. Please rescan." };
      }
    } else {
      if (!item.element || !item.element.isConnected) {
        return { success: false, error: "This field element has been removed or replaced on the webpage. Please rescan." };
      }
    }

    const value = String(
      Core.normalizeSpeechAnswer(rawValue, item.semanticType) ?? ""
    ).trim();
    if (!value) {
      return { success: false, error: "The provided answer is empty." };
    }

    // --- RADIO GROUPS ---
    if (item.kind === "radio") {
      const liveOptions = item.elements.map((el) => ({
        value: el.value,
        label: optionLabel(el),
        disabled: Boolean(el.disabled)
      }));

      const matchRes = Core.matchOption(liveOptions, value, item.semanticType);
      if (matchRes.ambiguous) {
        return {
          success: false,
          ambiguous: true,
          candidates: matchRes.candidates,
          error: matchRes.error
        };
      }
      if (!matchRes.matched) {
        return { success: false, error: matchRes.error };
      }

      const target = item.elements.find((el) => el.value === matchRes.matched.value);
      if (!target) {
        return { success: false, error: "The chosen radio option is no longer available." };
      }

      target.checked = true;
      target.dispatchEvent(new Event("input", { bubbles: true }));
      target.dispatchEvent(new Event("change", { bubbles: true }));
      target.focus({ preventScroll: true });

      return {
        success: true,
        value: matchRes.matched.label,
        rawValue: matchRes.matched.value,
        fieldId
      };
    }

    const el = item.element;

    // --- CHECKBOXES (Bug D Fix) ---
    if (item.kind === "checkbox") {
      const parsedBool = Core.parseBooleanAnswer(value);
      if (parsedBool === null) {
        return {
          success: false,
          error: "For this checkbox, please answer with “Yes, I agree” or “No, I do not agree”."
        };
      }

      if (parsedBool === false) {
        el.checked = false;
        el.dispatchEvent(new Event("input", { bubbles: true }));
        el.dispatchEvent(new Event("change", { bubbles: true }));
        el.focus({ preventScroll: true });

        if (item.required) {
          return {
            success: false,
            requiredConsentDeclined: true,
            value: "Unchecked",
            fieldId,
            error: "The checkbox was left unchecked. This declaration is required to complete the registration, but your decision is respected."
          };
        }
        return { success: true, value: "Unchecked", fieldId };
      }

      // Affirmative: true
      el.checked = true;
      el.dispatchEvent(new Event("input", { bubbles: true }));
      el.dispatchEvent(new Event("change", { bubbles: true }));
      el.focus({ preventScroll: true });

      return { success: true, value: "I agree (Checked)", fieldId };
    }

    // --- SELECT DROPDOWNS (Bug A Fix) ---
    if (item.kind === "select") {
      const liveOptions = getSelectOptions(el);
      item.options = liveOptions;

      const matchRes = Core.matchOption(liveOptions, value, item.semanticType);
      if (matchRes.ambiguous) {
        return {
          success: false,
          ambiguous: true,
          candidates: matchRes.candidates,
          error: matchRes.error
        };
      }
      if (!matchRes.matched) {
        return { success: false, error: matchRes.error };
      }

      const matchedOption = matchRes.matched;
      setNativeValue(el, matchedOption.value);

      // Explicitly set selectedIndex to guarantee web framework recognition
      const optIdx = Array.from(el.options).findIndex((o) => o.value === matchedOption.value);
      if (optIdx >= 0) {
        el.selectedIndex = optIdx;
      }

      el.focus({ preventScroll: true });

      return {
        success: true,
        value: matchedOption.label,
        rawValue: matchedOption.value,
        fieldId
      };
    }

    // --- DATE INPUTS ---
    if (el.type === "date" || item.semanticType === "date" || item.semanticType === "date_of_birth") {
      const normalizedDate = Core.parseSpokenDate(value);
      if (!normalizedDate) {
        return {
          success: false,
          error: "Please provide a valid date, such as 4th October 2003 or 04/10/2003."
        };
      }
      setNativeValue(el, normalizedDate);
      el.focus({ preventScroll: true });
      return { success: true, value: normalizedDate, fieldId };
    }

    // --- EMAIL INPUTS ---
    if (el.type === "email" || item.semanticType === "email") {
      const emailVal = Core.parseSpokenEmail(value) || value.trim();
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailVal)) {
        return {
          success: false,
          error: "That doesn't look like a valid email address. Please say or type it carefully (e.g., name@example.com)."
        };
      }
      setNativeValue(el, emailVal);
      el.focus({ preventScroll: true });
      return { success: true, value: emailVal, fieldId };
    }

    // --- TELEPHONE INPUTS ---
    if (el.type === "tel" || item.semanticType === "phone") {
      const phoneVal = Core.parseSpokenPhone(value) || value.replace(/[^\d+]/g, "").trim();
      const digitsOnly = phoneVal.replace(/\D/g, "");
      if (digitsOnly.length > 0 && (digitsOnly.length < 7 || digitsOnly.length > 15)) {
        return {
          success: false,
          error: "That phone number seems incomplete. Please provide a standard 10-digit number."
        };
      }
      setNativeValue(el, phoneVal);
      el.focus({ preventScroll: true });
      return { success: true, value: phoneVal, fieldId };
    }

    // --- PIN CODE ---
    if (item.semanticType === "pincode") {
      const pinDigits = Core.parseSpokenDigits(value, 6, 6) || value.replace(/\D/g, "");
      if (pinDigits.length === 6) {
        setNativeValue(el, pinDigits);
        el.focus({ preventScroll: true });
        return { success: true, value: pinDigits, fieldId };
      }
      return { success: false, error: "Please provide a six-digit PIN code." };
    }

    // --- HTML5 PATTERN VALIDATION ---
    if (el.pattern) {
      try {
        if (value && !new RegExp(`^(?:${el.pattern})$`).test(value)) {
          return {
            success: false,
            error: "This value does not match the specific format required by the webpage."
          };
        }
      } catch (_) {}
    }

    // Keep free-form wording, casing, and punctuation as recognized.
    setNativeValue(el, value);
    el.focus({ preventScroll: true });

    return { success: true, value, fieldId };
  }

  // --- MESSAGE LISTENER ---
  chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (!message || typeof message.type !== "string") return false;

    if (message.type === "FORMMATE_SCAN") {
      try {
        const fields = scanFields();
        sendResponse({
          ok: true,
          title: document.title || location.hostname,
          url: location.href,
          hostname: location.hostname,
          fields,
          message: fields.length ? "" : "No visible, editable form fields were found on this page."
        });
      } catch (error) {
        sendResponse({ ok: false, error: error?.message || "Could not scan this page." });
      }
      return true;
    }

    if (message.type === "FORMMATE_FILL_FIELD") {
      try {
        sendResponse(fillField(message.fieldId, message.value));
      } catch (error) {
        sendResponse({ success: false, error: error?.message || "Could not fill this field." });
      }
      return true;
    }

    return false;
  });
})();
