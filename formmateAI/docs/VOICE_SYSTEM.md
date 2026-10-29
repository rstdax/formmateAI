# FORMMATE AI — Voice System Architecture & Specification

> **Practice Prototype Boundary:** Maintained strictly for learning, architectural refinement, and local experimentation. It is strictly separate from and ineligible as pre-developed material for the ECCLISIA 4.0 competition.

---

## 1. Overview & Voice-First Philosophy

FORMMATE AI is engineered from the ground up as a **voice-first** browser assistant. Unlike conventional conversational chatbots that rely on a text input box and periodic speech input, FORMMATE creates an autonomous audio-visual loop:

1. Assistant initiates the conversation warmly like a helpful human companion: *"Hello! I'm FORMMATE, your form companion. Let's fill out this form together! First, what is your full name?"*
2. Spoken text streams synchronously into the assistant conversation bubble with a **progressive caption reveal** effect.
3. The central hero visualizer glows in **Radiant Amethyst and Luminous Violet** (`SPEAKING` state) with pulsing voice soundwaves.
4. As soon as speech synthesis completes:
   - An **acoustic transition chime** (ascending dual-tone chime E5 -> A5) rings via the native Web Audio API.
   - The visual theme dynamically transitions to **Electric Cyan & Neon Emerald** (`LISTENING` state).
   - An **automatic handoff** directly activates Speech Recognition without requiring user clicks.
5. When the user speaks, their answer is transcribed, displayed in the "You Said" card, parsed in context, and populated into the active webpage input.
6. A **satisfying confirmation chime** (C5 -> G5) rings, the field card turns bright green, the assistant confirms aloud, and automatically advances to the next question.
7. If no speech is detected after 8 seconds, an **inactivity timeout** transitions the UI safely to `WAITING_FOR_TAP` ("Tap to speak", "Take your time. Tap when you're ready").

---

## 2. Web Audio Acoustic Feedback Engine (`soundFX`)

FORMMATE includes a built-in, zero-dependency Web Audio API acoustic synthesizer (`FormmateSoundEffects`). It generates pristine, ultra-low-latency chimes synthesized directly via mathematical oscillator envelopes:

### Acoustic Chimes:
1. **Speaking-to-Listening Transition Chime (`playListeningStart`):**
   - **Notes:** E5 (659.25 Hz) ascending to A5 (880.00 Hz).
   - **Envelope:** Dual sine oscillators with soft 20ms linear attack and exponential bell decay (380ms).
   - **Trigger:** Plays at the exact moment speech synthesis ends and the microphone opens.
2. **Field Auto-Fill Confirmation (`playSuccess`):**
   - **Notes:** C5 (523.25 Hz) ascending to G5 (783.99 Hz).
   - **Envelope:** Cheerful harmonic chime with 350ms decay.
   - **Trigger:** Plays when user speech is parsed and populated into the webpage DOM.
3. **Interactive Control Tap (`playTap`):**
   - **Notes:** 440 Hz -> 280 Hz crystal droplet tone (60ms).
   - **Trigger:** Plays on hero button, control pill, or modal click.
4. **Form Completion Celebration (`playComplete`):**
   - **Notes:** Ascending C-major arpeggio (C5 -> E5 -> G5 -> C6).
   - **Trigger:** Plays when all detected fields are filled and ready for review.

---

## 3. Web Speech API Integration & Engine Stability

FORMMATE utilizes standard W3C Web Speech APIs native to Google Chrome:
- **Speech Recognition (`webkitSpeechRecognition` / `SpeechRecognition`)**: Converts spoken user audio into real-time transcripts.
- **Speech Synthesis (`window.speechSynthesis`, `SpeechSynthesisUtterance`)**: Speaks natural prompts, confirmations, and guidance aloud.

### Transcript Normalization
After browser recognition, FORMMATE applies local, field-aware normalization before filling a field. It handles spoken email symbols and common provider-name variants, digit homophones and repeated digits for phone numbers, and spoken digits for PIN codes. For select/radio fields, it compares recognition alternatives with the live options and accepts a close spelling only when there is a unique best match; ambiguous matches still request clarification. Address fields use a curated list of common recognition variants for address terms such as house number, apartment, road/street, colony, sector, locality, landmarks, and nearby places (for example, “rode” to “road” and “neer” to “near”). Name fields use a local dictionary of 1,000+ Indian names and generated phonetic spelling variants. A unique likely spelling is offered to the user for confirmation; FORMMATE never changes a name unless the user accepts. Ambiguous or unlisted names are kept as spoken.

### Overcoming Known Chromium SpeechSynthesis Bugs:
1. **Garbage Collection Cut-off Bug:**
   In Chrome V8, `SpeechSynthesisUtterance` instances are frequently garbage-collected mid-speech if stored only in local variables. FORMMATE anchors active utterances into a global set (`window._activeSpeechUtterances = new Set()`) until the `utterance.onend` event completes.
2. **Audio Engine Stuck / Paused State Bug:**
   Chrome's synthesis engine frequently enters a paused state after `cancel()` or when tabs switch. FORMMATE calls `window.speechSynthesis.resume()` before speaking and maintains a 3-second watchdog timer during speech playback.
3. **Cancellation Race Conditions:**
   Calling `cancel()` followed immediately by `speak()` in the same event tick causes Chrome to cancel the newly queued utterance. FORMMATE uses a 50ms dispatch buffer to allow the browser audio thread to clear cleanly.
4. **Natural Human Voice Ranking:**
   Prioritizes high-fidelity natural voices:
   - *English (India):* Google Indian English -> Indian English voices (Rishi, Veena, Neerja) -> Google UK English Female / Google US English -> Apple Samantha/Siri -> system default.
   - *Hindi (India):* Google हिन्दी -> Swara / Madhur -> system Hindi.

```text
[ Assistant Speaks (Radiant Violet) ]
               │
               ▼
      TTS utterance.onend
               │
               ▼
[ Web Audio Chime Rings (E5 -> A5) ]
               │
               ▼
[ Dynamic Visual Shift (Electric Cyan) ]
               │
               ▼
       State -> LISTENING
               │
               ▼ (Microphone opens)
[ User Speaks (e.g. "My name is Rohan Ranjan Das") ]
               │
               ▼
      recognition.onresult (final)
               │
               ▼
       State -> PROCESSING
               │
               ▼
[ Web Audio Confirmation Chime (C5 -> G5) ]
               │
               ▼
[ Webpage DOM Populates & Dispatches Events ]
               │
               ▼
[ Assistant Confirms Aloud & Advances ]
```

---

## 4. Distinct Visual States: Speaking vs Listening

The interface provides an instantaneous, unmistakable visual difference between speaking and listening:

| Visual Element | SPEAKING State (Assistant Talking) | LISTENING State (User Talking) |
| :--- | :--- | :--- |
| **Color Palette** | Royal Amethyst, Radiant Violet, Lavender (`#7c3aed`, `#a855f7`) | Electric Cyan, Neon Emerald Teal, Azure (`#06b6d4`, `#10b981`) |
| **Hero Circle Surface** | Pearlescent lilac/lavender sheen (`#ffffff` -> `#f5f3ff` -> `#ddd6fe`) | Crystalline aquamarine/cyan sheen (`#ffffff` -> `#ecfeff` -> `#a5f3fc`) |
| **Concentric Rings** | Ultraviolet breathing ripples with violet glow (`#a855f7`) | Concentric radar/sonar pulses in neon cyan (`#06b6d4`) & teal (`#10b981`) |
| **Hero Icon** | Radiant violet voice/mic icon with gentle float bounce | Electric cyan mic icon with animated throb pulse & drop glow |
| **Hero Main Label** | **"Speaking..."** (bold purple) | **"Listening..."** (bold electric cyan) |
| **Hero Sub Label** | *"Listening starts next"* | *"Speak naturally now"* |
| **Active Card Focus** | Assistant Bubble card glows with purple border and halo | "You Said" card glows with luminous cyan border and halo |
| **Session Status Pill** | Purple background with pulsing violet indicator dot | Cyan background with pulsing emerald indicator dot |

---

## 5. Inactivity Timeout & Tap-to-Speak

- **8-Second Inactivity Guard:**
  When entering `LISTENING`, an 8,000ms timer starts. If the user pauses to think, read, or consult documents, microphone listening terminates safely after 8 seconds without audio.
- **State Transition to `WAITING_FOR_TAP`:**
  - Assistant bubble: *"Take your time. Tap when you're ready."*
  - Hero circle: **"Tap to speak"** with gentle breathing border.
- **One-Tap Resumption:**
  Tapping the circle plays the transition chime and resumes listening immediately.

---

## 6. Bilingual Localization

- **English (India) `en-IN`:**
  Prompts, confirmations, ordinals, and commands tuned to Indian English idioms.
- **Hindi (India) `hi-IN`:**
  Full spoken Devanagari prompts, confirmations, affirmative/negative parsing (`हाँ`, `नहीं`, `सहमति नहीं`), and control commands (`दोहराएं`, `वापस जाएं`, `छोड़ें`, `रुकें`).
