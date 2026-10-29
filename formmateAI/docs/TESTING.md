# FORMMATE AI — Test Plan & Test Suite (v0.2.0)

> **Practice Prototype Boundary:** Maintained strictly for learning, architectural refinement, and local experimentation. It is strictly separate from and ineligible as pre-developed material for the ECCLISIA 4.0 competition.

This document details the automated regression test suite, bilingual test suites, and manual testing verification procedures for FORMMATE AI.

---

## 1. Automated Testing Suite

The project features **74 automated, deterministic tests** executing with native Node.js and zero external package dependencies.

### Running the Automated Tests
From the root directory:
```bash
npm test
```
Or directly:
```bash
node tests/test-core.js && node tests/test-regression.js
```

### Actual Verification Results
```text
=== 1. DROP-DOWN MATCHING TESTS (BUG A REGRESSION) ===
  ✓ Exact lower-case matches label: 'assam'
  ✓ Exact TitleCase matches label: 'Assam'
  ✓ Exact value match: 'AS'
  ✓ Conversational framing: 'My state is Assam'
  ✓ Conversational framing with polite suffix: 'Assam, please'
  ✓ Conversational framing: 'I live in Assam'
  ✓ Ordinal selection: 'First option' (skips placeholder, maps to Assam)
  ✓ Ordinal selection: 'Option two' (maps to Meghalaya)
  ✓ Ordinal selection: 'the second one' (maps to Meghalaya)
  ✓ Ordinal selection: 'last option' (maps to Other)
  ✓ Alias match: 'WB' maps to West Bengal
  ✓ Invalid option returns friendly error and does not mutate
  ✓ Out-of-range ordinal returns helpful error

=== 2. CHECKBOX INTERPRETATION TESTS (BUG D REGRESSION) ===
  ✓ Affirmative: 'Yes, I agree'
  ✓ Affirmative: 'I agree'
  ✓ Affirmative: 'yes'
  ✓ Affirmative: 'checked'
  ✓ Negation with agree keyword: 'No, I do not agree' must be FALSE
  ✓ Negation: 'I do not agree' must be FALSE
  ✓ Negation: 'I disagree' must be FALSE
  ✓ Negation: 'no' must be FALSE
  ✓ Unrelated command: 'Repeat the question' must be NULL (no change)
  ✓ Unrelated text: 'What does this mean' must be NULL

=== 3. SPOKEN DATE PARSING TESTS ===
  ✓ Spoken: 'Fourth October two thousand three'
  ✓ Spoken: 'Four October two thousand three'
  ✓ Spoken: 'Fourth of October, 2003'
  ✓ Numeric DMY: '04/10/2003'
  ✓ ISO already: '2003-10-04'
  ✓ Spoken: '15th August 1997'

=== 4. SPOKEN EMAIL & PHONE TESTS ===
  ✓ Spoken email: 'rohan at gmail dot com'
  ✓ Spoken email with name: 'rohan.das at outlook dot com'
  ✓ Spoken phone digits: 'nine eight seven six five four three two one zero'
  ✓ Formatted phone: '+91 98765 43210'

=== 5. CONVERSATIONAL COMMAND RECOGNITION (BUG C) ===
  ✓ Command: 'Repeat the question'
  ✓ Command: 'Go back'
  ✓ Command: 'Skip this'
  ✓ Command: 'Start again'
  ✓ Command: 'Change my answer'
  ✓ Command: 'Stop listening'
  ✓ Command: 'Read my answers'
  ✓ Command: 'Next question'
  ✓ Regular answer is NOT a command: 'Rohan Ranjan Das'

=== 6. CONVERSATIONAL FRAMING STRIPPER ===
  ✓ Strip: 'My name is Rohan Ranjan Das' -> 'Rohan Ranjan Das'
  ✓ Strip: 'I am a male' -> 'male'
  ✓ Strip: 'My state is Assam' -> 'assam'

=== 7. BILINGUAL HINDI SUPPORT & STATE MACHINE ===
  ✓ Hindi affirmation: 'हाँ' -> true
  ✓ Hindi affirmation: 'हाँ, मैं सहमत हूँ' -> true
  ✓ Hindi negation: 'नहीं' -> false
  ✓ Hindi negation: 'सहमति नहीं' -> false
  ✓ Hindi command: 'दोहराएं' -> REPEAT
  ✓ Hindi command: 'वापस जाएं' -> GO_BACK
  ✓ Hindi command: 'छोड़ें' -> SKIP
  ✓ Hindi command: 'रुकें' -> STOP_LISTENING
  ✓ Hindi prompt generation includes Hindi keywords
  ✓ Hindi confirmation message includes Hindi keywords
  ✓ State Machine defines all 10 required states

=== REGRESSION SUITE: CRITICAL PROMPT DEFECTS ===
  ✓ BUG A: State dropdown accepts 'Assam'
  ✓ BUG A: State dropdown accepts 'assam'
  ✓ BUG A: State dropdown accepts 'My state is Assam'
  ✓ BUG A: State dropdown accepts 'First option' (skips placeholder, maps to Assam)
  ✓ BUG A: State dropdown accepts 'Option two' (maps to Meghalaya)
  ✓ BUG A: Invalid state 'California' does NOT modify field
  ✓ BUG B: Answering Gender with 'I am a male' updates Gender to Male
  ✓ BUG B: Answering Gender with 'I am a male' NEVER modifies Full Name
  ✓ BUG B: Answering State with 'My state is Assam' NEVER modifies Full Name
  ✓ BUG B: Answering Full Name with 'My name is Rohan Ranjan Das' updates Full Name properly
  ✓ BUG D: Checkbox 'No, I do not agree' leaves checkbox unchecked and reports required decline
  ✓ BUG D: Checkbox 'Yes, I agree' checks checkbox successfully
  ✓ BUG D: Non-boolean text 'Repeat the question' does NOT alter checkbox
  ✓ Safety: Disconnected DOM element returns safe error without crashing
  ✓ Commands: 'repeat', 'skip', 'go back', 'next' are handled in context
  ✓ Lifecycle: Inactivity timeout triggers WAITING_FOR_TAP state
  ✓ Manual Edit: Modifying a field value updates DOM and dispatches events
  ✓ Progress: Correctly calculates completion percentage as fields are filled

Total tests: 74 | Passed: 74 | Failed: 0
```

---

## 2. Test Suite Breakdown

### Unit Suite (`tests/test-core.js` — 56 tests)
1. **Dropdown & Option Resolution (13 tests):**
   - Exact lowercase matching (`"assam"` → `"Assam"`).
   - Exact TitleCase matching (`"Assam"` → `"Assam"`).
   - Exact HTML value matching (`"AS"` → `"Assam"`).
   - Conversational framing stripping (`"My state is Assam"`, `"Assam, please"`, `"I live in Assam"`).
   - Explicit ordinal mapping (`"First option"` skipping empty placeholder, `"Option two"`, `"the second one"`, `"last option"`).
   - Alias mapping (`"WB"` → `"West Bengal"`).
   - Ambiguity & out-of-range ordinal error handling.
2. **Checkbox Affirmation & Negation-First (10 tests):**
   - Affirmative phrases (`"Yes, I agree"`, `"I agree"`, `"yes"`, `"checked"`).
   - Negation-first precedence (`"No, I do not agree"` → `false`, `"I do not agree"` → `false`, `"I disagree"` → `false`, `"no"` → `false`).
   - Non-boolean speech rejection without field corruption (`"Repeat the question"` → `null`).
3. **Spoken Date Parsing (6 tests):**
   - Full spoken dates (`"Fourth October two thousand three"` → `"2003-10-04"`).
   - Written digits with suffixes (`"4th oct 2003"`, `"15th August 1997"`).
   - DMY numeric strings (`"04/10/2003"` → `"2003-10-04"`).
   - Direct ISO pass-through.
4. **Spoken Email & Phone Normalization (4 tests):**
   - Spoken tokens (`"rohan at gmail dot com"` → `"rohan@gmail.com"`).
   - Spoken digit words (`"nine eight seven six five four three two one zero"` → `"9876543210"`).
   - Standard formatted phone preservation (`"+91 98765 43210"`).
5. **Conversational Command Recognition (9 tests):**
   - Detects `REPEAT`, `GO_BACK`, `SKIP`, `START_AGAIN`, `CHANGE_ANSWER`, `STOP_LISTENING`, `READ_ANSWERS`, and `NEXT_QUESTION`.
   - Rejects non-command answers (`"Rohan Ranjan Das"`).
6. **Conversational Framing Stripping (3 tests):**
   - Strips `"My name is..."`, `"I am a..."`, `"My state is..."`.
7. **Bilingual Hindi & State Machine (11 tests):**
   - Hindi affirmations (`हाँ`, `हाँ, मैं सहमत हूँ`).
   - Hindi negations (`नहीं`, `सहमति नहीं`).
   - Hindi control commands (`दोहराएं`, `वापस जाएं`, `छोड़ें`, `रुकें`).
   - Hindi localized prompts & confirmations.
   - Verification of all 10 state machine states.

### Regression Suite (`tests/test-regression.js` — 18 tests)
1. **Bug A (State Dropdown):** Options accept `"Assam"`, `"assam"`, `"My state is Assam"`, `"First option"`, `"Option two"`. Disallows invalid entries without mutating DOM.
2. **Bug B (Active Field Isolation):** Answering Gender with `"I am a male"` updates Gender to Male and strictly NEVER alters Full Name. Answering State never modifies Full Name.
3. **Bug D (Consent Checkbox):** Negative answer unchecks checkbox and flags required consent declined. Affirmative answer checks checkbox.
4. **Safety & DOM Disconnection:** Detached elements fail safely without unhandled exceptions.
5. **Voice Lifecycle & Inactivity:** 8-second inactivity triggers `WAITING_FOR_TAP`; user tap restores `LISTENING`.
6. **Manual Field Edit Synchronization:** Direct edit modal updates DOM element, fires change events, and synchronizes with current field card.
7. **Form Progress Calculation:** Evaluates percentage dynamically from 0% to 100%.

---

## 3. Manual Verification Checklist (Demo Form)

1. Open Chrome and navigate to `chrome://extensions`.
2. Enable **Developer mode**. Click **Load unpacked** and select `FORMMATE-practice-prototype`.
3. Click **Details** on FORMMATE and toggle **Allow access to file URLs** ON.
4. Open `demo/index.html` in Chrome.
5. Click the FORMMATE toolbar icon and select **Open assistant**.
6. Follow the test checklist:
   - **Page Scan:** Side panel reflects 11 form fields and 0% Done.
   - **Assistant Voice & Live Captions:** Assistant asks: *"What is your full name?"* while words appear progressively in the bubble.
   - **Auto-Handoff:** Central hero circle automatically switches from speaking pulse to listening ring with no extra click required.
   - **Speak Answer:** Speak *"My name is Rohan Ranjan Das"*. Webpage updates. Assistant confirms and auto-advances to Email.
   - **State Dropdown (Bug A):** When prompted for State, answer *"Assam"* or *"My state is Assam"*. Dropdown chooses Assam (value `Assam`), dispatches change event.
   - **Gender Radio (Bug B):** When prompted for Gender, answer *"I am a male"*. Male radio checks; Full Name remains untouched.
   - **Consent Checkbox (Bug D):** When prompted for Consent, answer *"No, I do not agree"*. Checkbox unchecks, assistant notes consent declined. Answer *"I agree"*; checkbox checks.
   - **Inactivity Timeout:** Wait 8 seconds without speaking. Hero transitions to *"Tap to speak"*. Tap the circle to resume listening.
   - **Manual Edit Modal:** Click "Edit" on Current Field card, update value, click "Save & Apply". Webpage DOM updates instantly.
   - **Review Transcript:** Click "Transcript" pill. Modal displays full conversation history.
   - **Bilingual Toggle:** Open Settings (`gear` icon), toggle to Hindi. Prompts, confirmations, and commands switch to Hindi.
