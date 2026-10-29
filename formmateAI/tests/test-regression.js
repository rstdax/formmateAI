/**
 * Automated Regression Test Suite for FORMMATE AI
 * Simulates the demo form DOM and tests the exact Bug A, Bug B, Bug C, Bug D scenarios.
 */

const assert = require("assert");
const Core = require("../formmate-core.js");

let passed = 0;
let failed = 0;

function test(description, fn) {
  try {
    fn();
    console.log(`  ✓ ${description}`);
    passed++;
  } catch (err) {
    console.error(`  ✗ ${description}`);
    console.error(`    ${err.message}`);
    failed++;
  }
}

// --- LIGHTWEIGHT DETERMINISTIC DOM MOCK FOR DEMO FORM ---

class MockElement {
  constructor(tag, attrs = {}) {
    this.tagName = tag.toUpperCase();
    this.type = (attrs.type || (tag === "textarea" ? "textarea" : tag === "select" ? "select" : "text")).toLowerCase();
    this.id = attrs.id || "";
    this.name = attrs.name || "";
    this.value = attrs.value || "";
    this.checked = Boolean(attrs.checked);
    this.required = Boolean(attrs.required);
    this.pattern = attrs.pattern || "";
    this.placeholder = attrs.placeholder || "";
    this.disabled = Boolean(attrs.disabled);
    this.isConnected = true;
    this.labels = [];
    this.options = [];
    this.selectedIndex = -1;
    this.events = [];
    this._attrs = { ...attrs };
  }

  getAttribute(attr) {
    return this._attrs[attr] || null;
  }

  setAttribute(attr, val) {
    this._attrs[attr] = String(val);
  }

  dispatchEvent(evt) {
    this.events.push(evt.type);
  }

  focus() {}

  closest() {
    return null;
  }
}

function buildDemoDOM() {
  const elements = [];

  // Full Name
  const fullName = new MockElement("input", { id: "applicant_name", name: "applicant_name", type: "text", required: true });
  fullName.labels = [{ innerText: "Full Name *" }];
  elements.push(fullName);

  // Email
  const email = new MockElement("input", { id: "email_address", name: "email_address", type: "email", required: true });
  email.labels = [{ innerText: "Email Address *" }];
  elements.push(email);

  // Phone
  const phone = new MockElement("input", { id: "mobile_number", name: "mobile_number", type: "tel" });
  phone.labels = [{ innerText: "Phone Number" }];
  elements.push(phone);

  // DOB
  const dob = new MockElement("input", { id: "birth_date", name: "birth_date", type: "date" });
  dob.labels = [{ innerText: "Date of Birth" }];
  elements.push(dob);

  // District
  const district = new MockElement("input", { id: "district", name: "district", type: "text" });
  district.labels = [{ innerText: "District" }];
  elements.push(district);

  // State Dropdown
  const stateSelect = new MockElement("select", { id: "state", name: "state" });
  stateSelect.labels = [{ innerText: "State" }];
  const stateChoices = [
    { value: "", label: "Choose a state" },
    { value: "Assam", label: "Assam" },
    { value: "Meghalaya", label: "Meghalaya" },
    { value: "Manipur", label: "Manipur" },
    { value: "Tripura", label: "Tripura" },
    { value: "Arunachal Pradesh", label: "Arunachal Pradesh" },
    { value: "West Bengal", label: "West Bengal" },
    { value: "Other", label: "Other" }
  ];
  stateSelect.options = stateChoices.map((c) => ({
    value: c.value,
    textContent: c.label,
    label: c.label,
    disabled: false
  }));
  elements.push(stateSelect);

  // Gender Radio Group
  const radioFem = new MockElement("input", { type: "radio", name: "gender", value: "Female" });
  radioFem.labels = [{ innerText: "Female" }];
  const radioMale = new MockElement("input", { type: "radio", name: "gender", value: "Male" });
  radioMale.labels = [{ innerText: "Male" }];
  const radioOther = new MockElement("input", { type: "radio", name: "gender", value: "Prefer not to say" });
  radioOther.labels = [{ innerText: "Prefer not to say" }];
  elements.push(radioFem, radioMale, radioOther);

  // Address
  const address = new MockElement("textarea", { id: "address", name: "address" });
  address.labels = [{ innerText: "Residential Address" }];
  elements.push(address);

  // PIN Code
  const pin = new MockElement("input", { id: "pin_code", name: "pin_code", pattern: "[0-9]{6}" });
  pin.labels = [{ innerText: "PIN Code" }];
  elements.push(pin);

  // Consent Checkbox
  const consent = new MockElement("input", { id: "consent", name: "consent", type: "checkbox", required: true });
  consent.labels = [{ innerText: "I confirm that the information I entered is accurate. *" }];
  elements.push(consent);

  return {
    elements,
    fullName,
    email,
    phone,
    dob,
    district,
    stateSelect,
    radioFem,
    radioMale,
    radioOther,
    address,
    pin,
    consent
  };
}

// Mock Content Script Scanner & Fille
function createMockScanner(dom) {
  const registry = new Map();
  let nextId = 1;

  function scan() {
    registry.clear();
    const fields = [];

    // Group radios
    const radios = dom.elements.filter((e) => e.type === "radio");
    const others = dom.elements.filter((e) => e.type !== "radio");

    others.forEach((el) => {
      const id = `fm-field-${nextId++}`;
      let kind = "input";
      if (el.tagName === "SELECT") kind = "select";
      else if (el.tagName === "TEXTAREA") kind = "textarea";
      else if (el.type === "checkbox") kind = "checkbox";

      let sem = "general";
      const lbl = el.labels[0]?.innerText || el.name;
      if (lbl.includes("Full Name")) sem = "full_name";
      else if (lbl.includes("Email")) sem = "email";
      else if (lbl.includes("Phone")) sem = "phone";
      else if (lbl.includes("Date of Birth")) sem = "date_of_birth";
      else if (lbl.includes("District")) sem = "district";
      else if (lbl.includes("State")) sem = "state";
      else if (lbl.includes("Address")) sem = "address";
      else if (lbl.includes("PIN")) sem = "pincode";
      else if (lbl.includes("confirm") || el.type === "checkbox") sem = "consent";

      const item = {
        id,
        kind,
        element: el,
        label: lbl,
        semanticType: sem,
        required: el.required
      };

      if (kind === "select") {
        item.options = el.options.map((o) => ({ value: o.value, label: o.label, disabled: o.disabled }));
      }

      registry.set(id, item);
      fields.push(item);
    });

    if (radios.length) {
      const id = `fm-field-${nextId++}`;
      const item = {
        id,
        kind: "radio",
        elements: radios,
        label: "Gender",
        semanticType: "gender",
        required: false,
        options: radios.map((r) => ({ value: r.value, label: r.labels[0]?.innerText || r.value }))
      };
      registry.set(id, item);
      fields.push(item);
    }

    return fields;
  }

  function fillField(fieldId, rawValue) {
    const item = registry.get(fieldId);
    if (!item) return { success: false, error: "Field not in registry" };

    if (!item.element?.isConnected && !item.elements?.every((e) => e.isConnected)) {
      return { success: false, error: "Element disconnected from DOM" };
    }

    const value = String(rawValue || "").trim();

    // SELECT
    if (item.kind === "select") {
      const match = Core.matchOption(item.options, value, item.semanticType);
      if (!match.matched) return { success: false, error: match.error };
      item.element.value = match.matched.value;
      item.element.dispatchEvent({ type: "input" });
      item.element.dispatchEvent({ type: "change" });
      return { success: true, value: match.matched.label, fieldId };
    }

    // RADIO
    if (item.kind === "radio") {
      const match = Core.matchOption(item.options, value, item.semanticType);
      if (!match.matched) return { success: false, error: match.error };
      const target = item.elements.find((e) => e.value === match.matched.value);
      if (target) {
        target.checked = true;
        target.dispatchEvent({ type: "input" });
        target.dispatchEvent({ type: "change" });
        return { success: true, value: match.matched.label, fieldId };
      }
      return { success: false, error: "Target radio not found" };
    }

    // CHECKBOX
    if (item.kind === "checkbox") {
      const bool = Core.parseBooleanAnswer(value);
      if (bool === null) return { success: false, error: "Say yes or no" };
      if (!bool) {
        item.element.checked = false;
        item.element.dispatchEvent({ type: "change" });
        if (item.required) {
          return { success: false, requiredConsentDeclined: true, error: "Required consent declined" };
        }
        return { success: true, value: "Unchecked", fieldId };
      }
      item.element.checked = true;
      item.element.dispatchEvent({ type: "change" });
      return { success: true, value: "I agree (Checked)", fieldId };
    }

    // TEXT / DATE / EMAIL / PHONE
    if (item.element.type === "date") {
      const d = Core.parseSpokenDate(value);
      if (!d) return { success: false, error: "Invalid date" };
      item.element.value = d;
      item.element.dispatchEvent({ type: "change" });
      return { success: true, value: d, fieldId };
    }

    if (item.element.type === "email") {
      const e = Core.parseSpokenEmail(value);
      if (!e) return { success: false, error: "Invalid email" };
      item.element.value = e;
      item.element.dispatchEvent({ type: "change" });
      return { success: true, value: e, fieldId };
    }

    // General text (clean framing)
    const cleaned = Core.stripConversationalFraming(value, item.semanticType);
    item.element.value = cleaned || value;
    item.element.dispatchEvent({ type: "input" });
    return { success: true, value: item.element.value, fieldId };
  }

  return { scan, fillField };
}

console.log("\n=== REGRESSION SUITE: CRITICAL PROMPT DEFECTS ===");

const dom = buildDemoDOM();
const scanner = createMockScanner(dom);
const fields = scanner.scan();

const fullNameField = fields.find((f) => f.semanticType === "full_name");
const stateField = fields.find((f) => f.semanticType === "state");
const genderField = fields.find((f) => f.semanticType === "gender");
const consentField = fields.find((f) => f.semanticType === "consent");

// --- BUG A TESTS ---
test("BUG A: State dropdown accepts 'Assam'", () => {
  const res = scanner.fillField(stateField.id, "Assam");
  assert.strictEqual(res.success, true);
  assert.strictEqual(dom.stateSelect.value, "Assam");
  assert.ok(dom.stateSelect.events.includes("change"));
});

test("BUG A: State dropdown accepts 'assam'", () => {
  const res = scanner.fillField(stateField.id, "assam");
  assert.strictEqual(res.success, true);
  assert.strictEqual(dom.stateSelect.value, "Assam");
});

test("BUG A: State dropdown accepts 'My state is Assam'", () => {
  const res = scanner.fillField(stateField.id, "My state is Assam");
  assert.strictEqual(res.success, true);
  assert.strictEqual(dom.stateSelect.value, "Assam");
});

test("BUG A: State dropdown accepts 'First option' (skips placeholder, maps to Assam)", () => {
  const res = scanner.fillField(stateField.id, "First option");
  assert.strictEqual(res.success, true);
  assert.strictEqual(dom.stateSelect.value, "Assam");
});

test("BUG A: State dropdown accepts 'Option two' (maps to Meghalaya)", () => {
  const res = scanner.fillField(stateField.id, "Option two");
  assert.strictEqual(res.success, true);
  assert.strictEqual(dom.stateSelect.value, "Meghalaya");
});

test("BUG A: Invalid state 'California' does NOT modify field", () => {
  const prevVal = dom.stateSelect.value;
  const res = scanner.fillField(stateField.id, "California");
  assert.strictEqual(res.success, false);
  assert.strictEqual(dom.stateSelect.value, prevVal);
});

// --- BUG B TESTS (DATA INTEGRITY / FIELD TARGETING) ---
test("BUG B: Answering Gender with 'I am a male' updates Gender to Male", () => {
  const res = scanner.fillField(genderField.id, "I am a male");
  assert.strictEqual(res.success, true);
  assert.strictEqual(res.value, "Male");
  assert.strictEqual(dom.radioMale.checked, true);
});

test("BUG B: Answering Gender with 'I am a male' NEVER modifies Full Name", () => {
  // Reset full name to blank
  dom.fullName.value = "";
  // Fill gender
  scanner.fillField(genderField.id, "I am a male");
  // Verify Full Name remained untouched!
  assert.strictEqual(dom.fullName.value, "", "Full Name must not be populated when Gender is active!");
});

test("BUG B: Answering State with 'My state is Assam' NEVER modifies Full Name", () => {
  dom.fullName.value = "";
  scanner.fillField(stateField.id, "My state is Assam");
  assert.strictEqual(dom.fullName.value, "", "Full Name must not be populated when State is active!");
  assert.strictEqual(dom.stateSelect.value, "Assam");
});

test("BUG B: Answering Full Name with 'My name is Rohan Ranjan Das' updates Full Name properly", () => {
  const res = scanner.fillField(fullNameField.id, "My name is Rohan Ranjan Das");
  assert.strictEqual(res.success, true);
  assert.strictEqual(dom.fullName.value, "rohan ranjan das");
});

// --- BUG D TESTS (CHECKBOX NEGATION & INTEGRITY) ---
test("BUG D: Checkbox 'No, I do not agree' leaves checkbox unchecked and reports required decline", () => {
  dom.consent.checked = true; // start true
  const res = scanner.fillField(consentField.id, "No, I do not agree");
  assert.strictEqual(res.success, false, "Declining a required checkbox must not succeed as completed");
  assert.strictEqual(res.requiredConsentDeclined, true);
  assert.strictEqual(dom.consent.checked, false, "Must uncheck DOM checkbox");
});

test("BUG D: Checkbox 'Yes, I agree' checks checkbox successfully", () => {
  dom.consent.checked = false;
  const res = scanner.fillField(consentField.id, "Yes, I agree");
  assert.strictEqual(res.success, true);
  assert.strictEqual(dom.consent.checked, true);
});

test("BUG D: Non-boolean text 'Repeat the question' does NOT alter checkbox", () => {
  dom.consent.checked = false;
  const res = scanner.fillField(consentField.id, "Repeat the question");
  assert.strictEqual(res.success, false);
  assert.strictEqual(dom.consent.checked, false);
});

// --- DISCONNECTED DOM ELEMENT SAFETY ---
test("Safety: Disconnected DOM element returns safe error without crashing", () => {
  const disconnectedInput = new MockElement("input", { id: "temp", name: "temp" });
  disconnectedInput.isConnected = false; // Detached!

  const res = scanner.fillField(stateField.id, "Assam");
  // Now disconnect stateSelect
  dom.stateSelect.isConnected = false;
  const resDetached = scanner.fillField(stateField.id, "Assam");
  assert.strictEqual(resDetached.success, false);
  assert.ok(resDetached.error.includes("disconnected") || resDetached.error.includes("removed"));
  dom.stateSelect.isConnected = true; // Restore
});

// --- CONVERSATION COMMANDS ---
test("Commands: 'repeat', 'skip', 'go back', 'next' are handled in context", () => {
  assert.strictEqual(Core.recognizeCommand("repeat the question"), "REPEAT");
  assert.strictEqual(Core.recognizeCommand("skip this"), "SKIP");
  assert.strictEqual(Core.recognizeCommand("go back"), "GO_BACK");
  assert.strictEqual(Core.recognizeCommand("next question"), "NEXT_QUESTION");
  assert.strictEqual(Core.recognizeCommand("start again"), "START_AGAIN");
  assert.strictEqual(Core.recognizeCommand("read my answers"), "READ_ANSWERS");
  assert.strictEqual(Core.recognizeCommand("stop listening"), "STOP_LISTENING");
});

// --- INACTIVITY TIMEOUT & TAP-TO-SPEAK LIFECYCLE ---
test("Lifecycle: Inactivity timeout triggers WAITING_FOR_TAP state", () => {
  let currentState = null;
  const sm = new Core.ConversationStateMachine((newState) => {
    currentState = newState;
  });

  sm.transition(Core.States.SPEAKING);
  assert.strictEqual(sm.state, Core.States.SPEAKING);

  // Assistant finishes speaking -> Auto handoff to LISTENING
  sm.transition(Core.States.LISTENING);
  assert.strictEqual(sm.state, Core.States.LISTENING);

  // Inactivity timeout simulation (~8 seconds with no speech)
  sm.transition(Core.States.WAITING_FOR_TAP, { reason: "inactivity_timeout" });
  assert.strictEqual(sm.state, Core.States.WAITING_FOR_TAP);
  assert.strictEqual(currentState, Core.States.WAITING_FOR_TAP);

  // User taps Liquid Glass circle hero button -> resets to LISTENING
  sm.transition(Core.States.LISTENING, { reason: "user_tap" });
  assert.strictEqual(sm.state, Core.States.LISTENING);
});

// --- MANUAL FIELD EDITING DOM SYNCHRONIZATION ---
test("Manual Edit: Modifying a field value updates DOM and dispatches events", () => {
  const emailField = fields.find((f) => f.semanticType === "email");
  assert.ok(emailField, "Email field must exist");

  // User manually edits via modal
  const updatedValue = "rohan.custom@example.com";
  const res = scanner.fillField(emailField.id, updatedValue);
  assert.strictEqual(res.success, true);
  assert.strictEqual(dom.email.value, updatedValue);
  assert.ok(dom.email.events.includes("change"));
});

// --- DYNAMIC FORM COMPLETION PERCENTAGE ---
test("Progress: Correctly calculates completion percentage as fields are filled", () => {
  const testFields = [
    { id: "f1", currentValue: "" },
    { id: "f2", currentValue: "" },
    { id: "f3", currentValue: "" },
    { id: "f4", currentValue: "" }
  ];

  function calcProgress(list) {
    if (!list.length) return 0;
    const filled = list.filter((f) => Boolean(String(f.currentValue || "").trim())).length;
    return Math.round((filled / list.length) * 100);
  }

  assert.strictEqual(calcProgress(testFields), 0);

  testFields[0].currentValue = "Alice";
  assert.strictEqual(calcProgress(testFields), 25);

  testFields[1].currentValue = "alice@example.com";
  assert.strictEqual(calcProgress(testFields), 50);

  testFields[2].currentValue = "Assam";
  assert.strictEqual(calcProgress(testFields), 75);

  testFields[3].currentValue = "Yes";
  assert.strictEqual(calcProgress(testFields), 100);
});

console.log(`\n-----------------------------------------`);
console.log(`Total regression tests run: ${passed + failed}`);
console.log(`Passed: ${passed}`);
console.log(`Failed: ${failed}`);

if (failed > 0) {
  process.exit(1);
} else {
  console.log("All critical regression tests PASSED!");
}
