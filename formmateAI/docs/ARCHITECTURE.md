# FORMMATE AI — Architecture Specification (v0.2.0)

> **Practice Prototype Boundary:** Maintained strictly for learning, architectural refinement, and local experimentation. It is strictly separate from and ineligible as pre-developed material for the ECCLISIA 4.0 competition.

---

## 1. High-Level System Architecture

```text
User opens target webpage (e.g. demo form)
             │
             ▼
User clicks FORMMATE extension icon -> Opens Chrome Side Panel (sidepanel.html)
             │
             ▼
Side Panel executes auto-scan via chrome.scripting API
             │
             ▼
Injected into Active Tab:
  ├── formmate-core.js (Universal deterministic domain engine)
  └── content.js (DOM scanner, element registry & mutation bridge)
             │
             ▼
Content Script scans visible, editable controls:
  - Validates .isConnected, types, labels, required attributes, select options
  - Builds registry of unique field IDs (fm-field-N)
  - Returns metadata array to Side Panel
             │
             ▼
Side Panel initializes ConversationStateMachine:
  - Establishes authoritative active field
  - Generates natural localized prompt
             │
             ▼
Speech Synthesis speaks question aloud:
  - Progressive caption reveal in Apple Liquid Glass assistant bubble
  - Central hero circle pulses with iridescent glow
             │
             ▼
Automatic Speech Handoff:
  - 250ms quiet guard interval after speech finishes
  - Starts Speech Recognition (LISTENING state)
  - 8-second inactivity watchdog initiates
             │
             ▼
User speaks answer:
  - Interim transcript streams into "You Said" card
  - Final transcript sent to FormmateCore for contextual interpretation:
    * Conversational command detection
    * 6-tier select/radio option matcher
    * Negation-first boolean interpreter
    * Spoken date, email, and phone normalizer
             │
             ▼
Side Panel sends FORMMATE_FILL_FIELD message to content script:
  - Dispatches native property setter & bubbling input/change events
  - Synchronizes Current Field card & validation status badge
             │
             ▼
Assistant confirms answer aloud and auto-advances to next unfilled field
             │
             ▼
User reviews all fields, performs manual edits if desired, and manually submits on webpage
(FORMMATE strictly never auto-submits)
```

Saved profiles are created and edited in the extension popup and persisted locally with `chrome.storage.local`. The Side Panel profile icon selects the active profile. When scanned fields match saved details, the assistant asks for explicit yes/no confirmation before sending matching empty, non-consent fields through the content-script validation bridge.

---

## 2. Core Modules & Responsibilities

### 1. `formmate-core.js` (Universal Module Definition)
Pure, deterministic, zero-dependency engine shared identically across:
- Side Panel (`sidepanel.js`)
- In-page Content Script (`content.js`)
- Node.js Automated Test Suites (`tests/test-core.js`, `tests/test-regression.js`)

**Key Capabilities:**
- **Unicode-Aware Normalization (`normalizeText`):**
  Uses `NFD` decomposition for Latin diacritic stripping, followed by `NFC` recomposition while preserving Unicode marks (`\p{M}`) for Indic scripts (Devanagari matras, candrabindu).
- **Conversational Framing Stripping (`stripConversationalFraming`):**
  Identifies and removes natural conversational preambles (*"My name is..."*, *"I live in..."*, *"My state is..."*, *"I am a..."*) while retaining the core value.
- **6-Tier Dropdown & Option Matcher (`matchOption`):**
  1. *Tier 1: Exact normalized option label.*
  2. *Tier 2: Exact normalized option value.*
  3. *Tier 3: Stripped conversational framing match.*
  4. *Tier 4: Unambiguous aliases (`WB` → West Bengal, `AP` → Arunachal Pradesh).*
  5. *Tier 5: Explicit ordinal selection (`first option`, `option two`, `last`), skipping placeholders.*
  6. *Tier 6: Substring / keyword match; triggers short clarification if ambiguous.*
- **Negation-First Boolean Interpreter (`parseBooleanAnswer`):**
  Evaluates negative phrases (*"no"*, *"do not agree"*, *"disagree"*, *"nahi"*, *"nahin"*, *"नहीं"*, *"सहमति नहीं"*) *before* checking affirmative tokens. Compound phrases like *"No, I do not agree"* are deterministically evaluated as `false`.
- **Spoken Date Normalizer (`parseSpokenDate`):**
  Interprets written and spoken dates (*"Fourth October two thousand three"*, *"4th oct 2003"*, *"15/08/1997"*) into ISO `YYYY-MM-DD`.
- **Spoken Contact Normalizer (`parseSpokenEmail`, `parseSpokenPhone`):**
  Converts spoken emails (*"rohan at gmail dot com"*) and spoken digit strings (*"nine eight seven..."*) into standardized contact formats.
- **10-State Conversation Machine (`ConversationStateMachine`):**
  Manages states: `IDLE`, `SCANNING`, `SPEAKING`, `LISTENING`, `PROCESSING`, `VALIDATING`, `WAITING_FOR_TAP`, `PAUSED`, `COMPLETED`, `ERROR`.
- **Bilingual Natural Dialogue Generator (`generateHumanPrompt`, `generateConfirmationMessage`):**
  Generates fluent, natural spoken dialogue in English (India) and Hindi (India).

---

### 2. `content.js` (In-Page Bridge)
Injected into the active tab's DOM.
- **DOM Scanner (`scanPageForms`):**
  Identifies editable, visible controls (`input`, `select`, `textarea`, radio groups). Excludes passwords, hidden fields, disabled/read-only inputs, and file inputs.
- **In-Memory Registry:**
  Maps temporary runtime IDs (`fm-field-N`) to live DOM nodes.
- **Connection Guard:**
  Verifies `element.isConnected` before reads and writes. If an element was removed or rebuilt by the page framework, returns a safe error without crashing.
- **Event Dispatch Bridge:**
  Sets native property descriptors on elements and fires bubbling `input` and `change` events to ensure frameworks like React, Vue, and Angular detect model updates.

---

### 3. `sidepanel.js` & `sidepanel.html` (Apple Liquid Glass Interface)
- **Visual Design:** Apple Liquid Glass design language featuring frosted translucent glass panels, luminous borders, ambient gradient glow blobs, and responsive layout.
- **Central Liquid Glass Voice Hero:**
  Circular animated control with multi-ring iridescent glow. Visually reflects speech states:
  - *Speaking:* Soft breathing pulse with glowing lavender accent.
  - *Listening:* Radiating concentric cyan/blue waves.
  - *Processing:* Fast luminous spin.
  - *Waiting for Tap:* Steady gentle idle glow with prompt *"Tap to speak"*.
- **Progressive Caption Reveal:**
  Synchronizes spoken words with visual text in the assistant bubble, creating a live caption effect.
- **Current Field Card:**
  Displays the active target field with semantic SVG icon, current value capsule, validation badge (*"Auto-formatting success"*), and direct "Edit" trigger.
- **Manual Field Edit Modal:**
  Allows users to review and manually alter any field's value in a modal dialog. Changes immediately update the live webpage DOM and synchronize back into the side panel state.
- **Full Conversation Transcript Modal:**
  Maintains complete turn-by-turn history of assistant questions, user utterances, and system events.
- **Inactivity Timeout:**
  An 8-second watchdog transitions `LISTENING` to `WAITING_FOR_TAP` if no voice input is detected, preventing runaway microphone cycles.

---

## 3. Chrome Extension Messaging Protocol

All inter-script communication uses standard `chrome.tabs.sendMessage` and `chrome.runtime.onMessage`:

| Message Action | Source | Target | Payload | Response |
| :--- | :--- | :--- | :--- | :--- |
| `FORMMATE_SCAN_PAGE` | Side Panel | Content Script | None | `{ success, fields: [...] }` |
| `FORMMATE_FILL_FIELD` | Side Panel | Content Script | `{ fieldId, value }` | `{ success, value, fieldId }` |
| `FORMMATE_HIGHLIGHT_FIELD` | Side Panel | Content Script | `{ fieldId }` | `{ success }` |
| `FORMMATE_PING` | Side Panel | Content Script | None | `{ status: "ready" }` |

---

## 4. Invariants & Safety Guarantees

1. **Strict Target Field Association:** Every answer is parsed strictly against the active question's target field ID. Global opportunistic guessing is eliminated.
2. **Never Auto-Submits:** FORMMATE does not locate, trigger, or click form submission buttons.
3. **Never Auto-Agrees to Consent:** Required checkboxes declined by the user remain unchecked; the assistant announces that required consent was declined.
4. **Sensitive Input Exclusion:** Password fields, file uploads, and credit card CVVs are excluded from voice scanning.
5. **No Blind Fuzzy Matching:** Dropdowns only match verified options or unambiguous aliases; ambiguous phrases trigger a clarification prompt.
