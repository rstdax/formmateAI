# FORMMATE AI — Known Limitations & System Boundaries

> **Practice Prototype Boundary:** Maintained strictly for learning, architectural refinement, and local experimentation. It is strictly separate from and ineligible as pre-developed material for the ECCLISIA 4.0 competition.

---

## 1. Scope & Design Philosophy

FORMMATE AI is an experimental assistive prototype focused on making standard digital web forms accessible through a natural, voice-first companion. While the architecture is modular and highly deterministic, real-world web environments introduce unique constraints.

---

## 2. Speech Recognition & Synthesis Engines

### Cloud Backend Dependency
Chrome's native `webkitSpeechRecognition` implementation relies on Google cloud speech servers for audio transcription.
- **Offline Limitation:** Speech recognition will fail with a `network` error if the host device loses internet connectivity. (Speech synthesis remains functional offline using local OS voices).
- **Background Noise & Dialect Variance:** While tuned for Indian English (`en-IN`) and Hindi (`hi-IN`), strong regional accents, non-standard slang, or excessive background ambient noise can affect transcript accuracy.

### Operating System Speech Synthesis Differences
- **macOS / Windows / Linux Voice Quality:** Chrome provides high-quality Google natural voices when connected to the internet. On offline Linux installations, Chrome falls back to `espeak` or local speech-dispatcher voices, which lack natural prosody and `onboundary` timestamp events. FORMMATE includes an internal token timer fallback to preserve progressive caption animations across all environments.

---

## 3. Web Page Form Structure Boundaries

### 1. Cross-Origin iFrames
Chrome extension content scripts cannot inspect or access cross-origin `<iframe>` documents without explicit permission configurations due to the browser's Same-Origin Policy.
- **Current Behavior:** Forms embedded within third-party payment gateways, auth portals, or cross-origin embeds (e.g. `iframe.stripe.com`) are not scanned.

### 2. Closed Shadow DOM
Components encapsulated inside `Element.attachShadow({ mode: 'closed' })` cannot be accessed by external content scripts.
- **Current Behavior:** Fields encapsulated in closed shadow roots are invisible to the DOM scanner. Open shadow roots can be traversed, but standard DOM inputs are prioritized.

### 3. Non-Standard Canvas & Div-Based Pseudo-Selects
Some enterprise web applications implement inputs entirely through unsemantic `<div>`, `<span>`, or HTML5 `<canvas>` elements without ARIA attributes (`role="combobox"`, `role="listbox"`).
- **Current Behavior:** FORMMATE scans native `<select>`, `<input>`, `<textarea>`, and radio groups. Custom div-based dropdowns lacking standard form semantic attributes or native value bindings may not receive synthetic input events.

### 4. Dynamic Step Transitions & Lazy Injected Fields
On multi-step wizard forms where Step 2 DOM elements do not exist until Step 1 is submitted:
- **Current Behavior:** FORMMATE scans fields present in the active DOM. When the page navigates or reveals new steps, a "Rescan" operation is required to discover the newly mounted fields.

---

## 4. Security & Safety Exclusions

### 1. CAPTCHAs & Anti-Bot Challenges
- **Strict Prohibition:** FORMMATE deliberately does not attempt to solve, bypass, or interact with reCAPTCHA, hCaptcha, Cloudflare Turnstile, or any bot-detection mechanism. These require human perception and must be completed manually by the user.

### 2. Password & Sensitive Authentication Controls
- **Exclusion by Design:** Inputs with `type="password"`, hidden fields (`type="hidden"`), payment card CVVs, and file upload elements (`type="file"`) are excluded from voice prompts to protect sensitive user credentials from acoustic eavesdropping.

### 3. Zero Auto-Submission Guarantee
- **Invariant:** FORMMATE will **never** click a form's "Submit", "Pay", or "Confirm Transaction" button automatically. The user retains complete authority and must review all populated values and trigger the final form submission themselves.
