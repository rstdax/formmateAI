# FORMMATE AI — Privacy & Safety Policy (v0.2.0)

> **Practice Prototype Boundary:** Maintained strictly for learning, architectural refinement, and local experimentation. It is strictly separate from and ineligible as pre-developed material for the ECCLISIA 4.0 hackathon.

---

## 1. Local-First Architecture

FORMMATE AI is engineered from the ground up as a **strictly local** extension prototype:
- **No Remote AI APIs:** All conversational extraction, state machine transitions, option matching, and validation are performed locally in-browser via deterministic code (`formmate-core.js`).
- **No Cloud Backend or Database:** No form data, transcripts, or profile information is sent to any remote server or stored in a server-side database.
- **No Analytics or Telemetry:** There are zero trackers, logging endpoints, or external metrics scripts.
- **Local Saved Profiles:** Profiles are stored persistently in `chrome.storage.local` on this browser to support reuse across sessions. They are not encrypted by FORMMATE, synced, or sent to a server. Do not save passwords or highly sensitive identifiers. Page fields and conversation history remain in memory and are discarded when the panel closes.

---

## 2. Safety Invariants & Guardrails

1. **No Automatic Form Submission:**
   FORMMATE will NEVER locate, click, or dispatch submit events on a form's submit button. The human user must review all filled fields on the webpage and make the deliberate decision to submit.
2. **No Automatic Consent Acceptance:**
   Consent checkboxes, terms declarations, and certification fields are never checked automatically. If the user declines consent (e.g. saying *"No, I do not agree"*), the checkbox remains unchecked, and the assistant respects that choice.
3. **Sensitive Field Exclusion:**
   The DOM scanner intentionally excludes:
   - Password fields (`type="password"`).
   - File upload controls (`type="file"`).
   - Hidden fields (`type="hidden"`).
   - Disabled and read-only inputs.
   - Non-visible or aria-hidden elements.
4. **Restricted Chrome Permissions:**
   The extension requests only minimal Manifest V3 permissions:
   - `activeTab`: Scans the current page only upon explicit user action.
   - `scripting`: Injects the local bridge scripts into the inspected page frame.
   - `sidePanel`: Displays the conversational companion interface.
   - `storage`: Stores user-created profiles locally in this browser.
   No broad host permissions (`<all_urls>`) or persistent background worker permissions are requested.

---

## 3. Web Speech API Disclosure

- FORMMATE utilizes the browser's native Web Speech APIs (`webkitSpeechRecognition` / `SpeechRecognition` and `SpeechSynthesis`).
- **Browser Service Notice:** In Google Chrome, the built-in speech recognition service may route audio snippets through Google's speech recognition servers, depending on your operating system configuration and browser settings. This processing is managed directly by the browser vendor, not by FORMMATE AI.
- **Full Typed Fallback:** If you prefer not to use speech recognition or are entering private details, speech input can remain disabled, and all fields can be filled using the typed input fallback.
