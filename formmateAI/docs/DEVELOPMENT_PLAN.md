# DEVELOPMENT_PLAN.md — FORMMATE AI Prototype Diagnostic & Execution Plan

> **Note on Project Status:** Practice-only prototype. This package is maintained for learning, architectural refinement, and local experimentation. It is strictly separate from and ineligible as pre-developed material for the ECCLISIA 4.0 competition.

---

## 1. Architectural Diagnosis & Code Inspection

### 1.1 Architecture Overview
FORMMATE AI is structured as a Manifest V3 Chrome Extension utilizing the Chrome Side Panel API:
- `manifest.json`: Minimal MV3 configuration requesting `activeTab`, `scripting`, and `sidePanel` permissions.
- `popup.html` / `popup.js`: Trigger opening `sidepanel.html` in the active browser tab.
- `formmate-core.js`: Universal Module Definition (UMD) core engine providing pure deterministic domain logic, multi-tier matching, date/phone/email parsing, Unicode-aware normalization, and state machine management.
- `sidepanel.html` / `sidepanel.js` / `sidepanel.css`: Host the Apple Liquid Glass conversational assistant interface, voice recognition / synthesis orchestration, progressive caption synchronization, active conversation state machine, current field card, manual edit modal, and transcript review overlay.
- `content.js`: Injected into the target page to scan visible, editable form controls, build an in-memory element registry, classify field types heuristics, and apply DOM updates with bubbling event dispatches.
- `demo/index.html`: A self-contained student registration form including text inputs, email, phone, date, select dropdowns, radio groups, textarea, numeric PIN code, and consent checkbox.

---

## 2. Root Cause Analysis & Resolutions of Critical Bugs

### Bug A: Dropdown Answers Rejected Despite Matching Displayed Options
- **Root Cause 1 (`content.js` registry mismatch):**
  In original `content.js`, `addRegistryItem()` created a local `options` array for `<select>` elements (`item.kind === "select"`), but never stored `item.options = options` on the `item` in the registry. When `fillField(fieldId, rawValue)` called `matchOption(item.options || [], value)`, `item.options` was `undefined`, passing an empty array `[]`. Consequently, every dropdown selection attempt immediately failed.
- **Root Cause 2 (Brittle Matching & Conversational Framing):**
  `matchOption()` used simplistic substring checks that failed when the user used conversational framing (e.g., *"My state is Assam"*, *"Assam, please"*, *"I live in Assam"*).
- **Resolution:**
  1. Stored `options` directly on registry items and dynamically query live `select.options` at fill time.
  2. Implemented 6-tier matching priority:
     - Tier 1: Exact normalized option label.
     - Tier 2: Exact normalized option value.
     - Tier 3: Conversational framing stripped match (on label or value).
     - Tier 4: Unambiguous aliases (e.g. `WB` → West Bengal, `AP` → Arunachal Pradesh).
     - Tier 5: Explicit ordinal selection ("first option", "option two", "last"), skipping placeholder options.
     - Tier 6: Substring / keyword match; triggers short clarification if multiple candidates match.
  3. Proper event dispatch: Native value setter + bubbling `input` and `change` events.

### Bug B: Answers Assigned to the Wrong Fields
- **Root Cause 1 (Global Heuristic Extraction Overriding Active Target):**
  In original `sidepanel.js`, `handleAnswer(text)` iterated over *all* scanned fields on the page with `extractKnownValue(field, text)`. For `full_name`, the regex matched `/(?:my full name is|...|i am)\s+(.+?)/i`. When the user was asked about Gender and said *"I am a male"*, the full-name extractor matched *"a male"*. Because `extracted.length > 0`, `handleAnswer()` immediately updated `Full Name` with *"a male"* and confirmed *"I've updated Full Name"*, completely ignoring that the active question was Gender.
- **Resolution:**
  1. Eliminated global opportunistic multi-field extraction.
  2. Implemented single authoritative active-field state: incoming answers are evaluated strictly against the target field of the question currently awaiting an answer.
  3. Every incoming answer is processed specifically for the active question: answering Gender with *"I am a male"* sets Gender to Male and never modifies Full Name.

### Bug C: Conversation Gets Stuck on One Field
- **Root Cause:**
  Lack of formal conversation state machine and missing conversational control commands.
- **Resolution:**
  1. Built formal 10-state conversation state machine (`IDLE`, `SCANNING`, `SPEAKING`, `LISTENING`, `PROCESSING`, `VALIDATING`, `WAITING_FOR_TAP`, `PAUSED`, `COMPLETED`, `ERROR`).
  2. Implemented conversational control commands:
     - `"Repeat the question"` (`REPEAT`)
     - `"Go back"` (`GO_BACK`)
     - `"Skip this"` (`SKIP`)
     - `"Start again"` (`START_AGAIN`)
     - `"Change my answer"` (`CHANGE_ANSWER`)
     - `"Stop listening"` (`STOP_LISTENING`)
     - `"Read my answers"` (`READ_ANSWERS`)
     - `"Next question"` (`NEXT_QUESTION`)
  3. Automatic progression: advances to next unfilled question upon confirmation.

### Bug D: Checkbox Answers Handled Inconsistently
- **Root Cause:**
  Naïve token matching did not check negation before affirmative words. Phrases like *"No, I do not agree"* were misclassified because they contained the word *"agree"*.
- **Resolution:**
  1. Built negation-first boolean interpreter: negative phrases (*"no"*, *"do not agree"*, *"disagree"*, *"reject"*, *"nahi"*, *"नहीं"*, *"सहमति नहीं"*) are checked before affirmative phrases.
  2. If a required checkbox is declined, it remains unchecked on the webpage and is not falsely declared as completed.

---

## 3. Implementation Status & Roadmap

| Phase | Description | Status |
| :--- | :--- | :--- |
| **Phase 1 — Inspect** | Architectural mapping, source review, root-cause diagnosis. | **Complete** |
| **Phase 2 — Core Logic** | `formmate-core.js`, `content.js`, `sidepanel.js` rebuild; Bug A, B, C, D resolution. | **Complete** |
| **Phase 3 — Experience** | Active-field banner, quick actions, review modal direct edits, turn-based speech deduplication. | **Complete** |
| **Phase 4 — Testing** | Node syntax validation, 45 unit tests, 15 regression tests (60/60 passing). | **Complete** |
| **Phase 5 — Apple Liquid Glass Redesign** | Translucent frosted-glass panels, circular hero visualizer, progressive captions, no chat box. | **Complete** |
| **Phase 6 — Voice Flow & Bilingual Engine** | Auto speech-to-listening handoff, 8s inactivity timeout, English (India) & Hindi (India). | **Complete** |
| **Phase 7 — Verification & Documentation** | 74 automated tests (100% passing), documentation suite, clean ZIP archive generation. | **Complete** |
