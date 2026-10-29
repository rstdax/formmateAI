/**
 * FORMMATE AI — Production-Quality Side Panel Controller
 * Apple Liquid Glass Theme | Voice-First Autonomous Assistant
 *
 * Implements:
 * - 10-state conversation state machine
 * - Automatic speech-to-listening handoff
 * - Progressive live-caption reveal
 * - 8-second inactivity tap-to-speak timeout
 * - Interactive hero circular voice control
 * - Manual inline/modal field editor
 * - Full transcript drawer & settings modal
 * - Bilingual English (India) and Hindi (India) support
 * - In-page DOM synchronization & event dispatching
 */

const $ = (id) => document.getElementById(id);
const Core = window.FormmateCore;

// --- Ambient Acoustic Feedback Synthesizer (Web Audio API) ---
class FormmateSoundEffects {
  constructor() {
    this.ctx = null;
    this.enabled = true;
  }

  ensureContext() {
    if (!this.ctx && typeof AudioContext !== "undefined") {
      this.ctx = new (window.AudioContext || window.webkitAudioContext)();
    }
    if (this.ctx && this.ctx.state === "suspended") {
      this.ctx.resume();
    }
  }

  // Soft ascending chime when transitioning from Speaking -> Listening
  playListeningStart() {
    if (!this.enabled) return;
    try {
      this.ensureContext();
      if (!this.ctx) return;
      const t = this.ctx.currentTime;

      // Note 1: E5 (659.25 Hz)
      const osc1 = this.ctx.createOscillator();
      const gain1 = this.ctx.createGain();
      osc1.type = "sine";
      osc1.frequency.setValueAtTime(659.25, t);
      gain1.gain.setValueAtTime(0, t);
      gain1.gain.linearRampToValueAtTime(0.12, t + 0.02);
      gain1.gain.exponentialRampToValueAtTime(0.001, t + 0.28);
      osc1.connect(gain1);
      gain1.connect(this.ctx.destination);
      osc1.start(t);
      osc1.stop(t + 0.28);

      // Note 2: A5 (880.00 Hz) with gentle shimmer
      const osc2 = this.ctx.createOscillator();
      const gain2 = this.ctx.createGain();
      osc2.type = "sine";
      osc2.frequency.setValueAtTime(880.00, t + 0.08);
      gain2.gain.setValueAtTime(0, t + 0.08);
      gain2.gain.linearRampToValueAtTime(0.15, t + 0.10);
      gain2.gain.exponentialRampToValueAtTime(0.001, t + 0.38);
      osc2.connect(gain2);
      gain2.connect(this.ctx.destination);
      osc2.start(t + 0.08);
      osc2.stop(t + 0.38);
    } catch (_) {}
  }

  // Pleasant double confirmation chime when field is filled
  playSuccess() {
    if (!this.enabled) return;
    try {
      this.ensureContext();
      if (!this.ctx) return;
      const t = this.ctx.currentTime;

      // Note 1: C5 (523.25 Hz)
      const osc1 = this.ctx.createOscillator();
      const gain1 = this.ctx.createGain();
      osc1.type = "sine";
      osc1.frequency.setValueAtTime(523.25, t);
      gain1.gain.setValueAtTime(0, t);
      gain1.gain.linearRampToValueAtTime(0.12, t + 0.02);
      gain1.gain.exponentialRampToValueAtTime(0.001, t + 0.22);
      osc1.connect(gain1);
      gain1.connect(this.ctx.destination);
      osc1.start(t);
      osc1.stop(t + 0.22);

      // Note 2: G5 (783.99 Hz)
      const osc2 = this.ctx.createOscillator();
      const gain2 = this.ctx.createGain();
      osc2.type = "sine";
      osc2.frequency.setValueAtTime(783.99, t + 0.09);
      gain2.gain.setValueAtTime(0, t + 0.09);
      gain2.gain.linearRampToValueAtTime(0.14, t + 0.11);
      gain2.gain.exponentialRampToValueAtTime(0.001, t + 0.35);
      osc2.connect(gain2);
      gain2.connect(this.ctx.destination);
      osc2.start(t + 0.09);
      osc2.stop(t + 0.35);
    } catch (_) {}
  }

  // Soft subtle droplet tap on button click
  playTap() {
    if (!this.enabled) return;
    try {
      this.ensureContext();
      if (!this.ctx) return;
      const t = this.ctx.currentTime;
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      osc.type = "sine";
      osc.frequency.setValueAtTime(440, t);
      osc.frequency.exponentialRampToValueAtTime(280, t + 0.06);
      gain.gain.setValueAtTime(0.08, t);
      gain.gain.exponentialRampToValueAtTime(0.001, t + 0.06);
      osc.connect(gain);
      gain.connect(this.ctx.destination);
      osc.start(t);
      osc.stop(t + 0.06);
    } catch (_) {}
  }

  // Celebratory 4-note chord when form is completed
  playComplete() {
    if (!this.enabled) return;
    try {
      this.ensureContext();
      if (!this.ctx) return;
      const t = this.ctx.currentTime;
      const notes = [523.25, 659.25, 783.99, 1046.5]; // C5, E5, G5, C6
      notes.forEach((freq, i) => {
        const osc = this.ctx.createOscillator();
        const gain = this.ctx.createGain();
        const start = t + i * 0.08;
        osc.type = "sine";
        osc.frequency.setValueAtTime(freq, start);
        gain.gain.setValueAtTime(0, start);
        gain.gain.linearRampToValueAtTime(0.12, start + 0.02);
        gain.gain.exponentialRampToValueAtTime(0.001, start + 0.45);
        osc.connect(gain);
        gain.connect(this.ctx.destination);
        osc.start(start);
        osc.stop(start + 0.45);
      });
    } catch (_) {}
  }
}
const soundFX = new FormmateSoundEffects();

// Field icon SVGs by semantic category
const FIELD_ICONS = {
  full_name: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`,
  first_name: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`,
  last_name: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`,
  email: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>`,
  phone: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/></svg>`,
  date_of_birth: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>`,
  date: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>`,
  state: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>`,
  district: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>`,
  city: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>`,
  address: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>`,
  gender: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="4"/><path d="M12 2v6M12 16v6M4.93 4.93l4.24 4.24M14.83 14.83l4.24 4.24"/></svg>`,
  pincode: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="4" y1="9" x2="20" y2="9"/><line x1="4" y1="15" x2="20" y2="15"/><line x1="10" y1="3" x2="8" y2="21"/><line x1="16" y1="3" x2="14" y2="21"/></svg>`,
  consent: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>`,
  course: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>`,
  general: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>`
};

const state = {
  tabId: null,
  fields: [],
  profiles: [],
  activeProfileId: "",
  pendingProfileConfirmation: false,
  pendingNameCorrection: null,
  currentFieldId: null,
  currentTurnId: 0,
  stateMachine: null,
  recognition: null,
  isListening: false,
  isSpeaking: false,
  microphonePermissionState: "unknown",
  isProcessingAnswer: false,
  inactivityTimer: null,
  sessionSeconds: 0,
  sessionInterval: null,
  activeUtterance: null,
  captionInterval: null,
  lang: "en-IN",
  speakEnabled: true,
  autoSearchEnabled: true,
  inactivityTimeoutMs: 8000,
  history: [] // { role: 'assistant'|'user', text, timestamp, fieldId, fieldLabel }
};

const PROFILE_STORAGE_KEYS = ["formmateProfiles", "formmateActiveProfileId"];
let startRequestInProgress = null;
const PROFILE_FIELDS = [
  { key: "full_name", label: "Full name", type: "text" },
  { key: "first_name", label: "First name", type: "text" },
  { key: "last_name", label: "Last name", type: "text" },
  { key: "email", label: "Email", type: "email" },
  { key: "phone", label: "Phone", type: "tel" },
  { key: "date_of_birth", label: "Date of birth", type: "date" },
  { key: "gender", label: "Gender", type: "text" },
  { key: "guardian_name", label: "Parent / guardian name", type: "text" },
  { key: "address", label: "Street address", type: "text", full: true },
  { key: "city", label: "City / town", type: "text" },
  { key: "district", label: "District", type: "text" },
  { key: "state", label: "State / province", type: "text" },
  { key: "country", label: "Country", type: "text" },
  { key: "pincode", label: "Postal / PIN code", type: "text" },
  { key: "age", label: "Age", type: "number" },
  { key: "occupation", label: "Occupation", type: "text" },
  { key: "course", label: "Course / stream", type: "text" },
  { key: "education", label: "Education / qualification", type: "text" },
  { key: "institution", label: "School / institution", type: "text" }
];

function activeProfile() {
  return state.profiles.find((profile) => profile.id === state.activeProfileId) || null;
}

function renderProfiles() {
  const select = $("profileSelect");
  if (!select) return;
  select.replaceChildren();

  if (!state.profiles.length) {
    const emptyOption = document.createElement("option");
    emptyOption.value = "";
    emptyOption.textContent = "No saved profiles";
    select.append(emptyOption);
  } else {
    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.textContent = "Select a profile";
    select.append(placeholder);
    state.profiles.forEach((profile) => {
      const option = document.createElement("option");
      option.value = profile.id;
      option.textContent = profile.name;
      select.append(option);
    });
  }
  select.value = state.activeProfileId;
  const profile = activeProfile();
  $("profilesBtn").title = profile ? `Selected profile: ${profile.name}` : "Choose a saved profile";
  $("profilesBtn").setAttribute("aria-label", profile ? `Choose profile. Selected: ${profile.name}` : "Choose a saved profile");
  $("profilePickerStatus").textContent = profile
    ? `Selected: ${profile.name}. Formmate will ask before filling any saved details.`
    : "No active profile. Create one from the extension popup.";
}

async function loadSavedProfiles() {
  const saved = await chrome.storage.local.get(PROFILE_STORAGE_KEYS);
  state.profiles = Array.isArray(saved.formmateProfiles)
    ? saved.formmateProfiles.filter((profile) =>
      profile && typeof profile.id === "string" && typeof profile.name === "string" &&
      profile.values && typeof profile.values === "object" && !Array.isArray(profile.values))
    : [];
  state.activeProfileId = typeof saved.formmateActiveProfileId === "string" &&
    state.profiles.some((profile) => profile.id === saved.formmateActiveProfileId)
    ? saved.formmateActiveProfileId
    : "";
  renderProfiles();
}

async function openProfilesModal() {
  try {
    const saved = await chrome.storage.local.get(PROFILE_STORAGE_KEYS);
    state.profiles = Array.isArray(saved.formmateProfiles)
      ? saved.formmateProfiles.filter((profile) =>
        profile && typeof profile.id === "string" && typeof profile.name === "string" &&
        profile.values && typeof profile.values === "object" && !Array.isArray(profile.values))
      : [];
    state.activeProfileId = typeof saved.formmateActiveProfileId === "string" &&
      state.profiles.some((profile) => profile.id === saved.formmateActiveProfileId)
      ? saved.formmateActiveProfileId
      : "";
    renderProfiles();
    $("profilesModalOverlay").hidden = false;
  } catch (error) {
    console.error("Could not load saved profiles:", error);
    showToast("Could not load saved profiles");
  }
}

function matchingProfileFields(profile = activeProfile()) {
  if (!profile) return [];
  return state.fields.filter((field) =>
    field.kind !== "checkbox" && field.semanticType !== "consent" &&
    field.semanticType !== "general" && !fieldIsFilled(field) &&
    typeof profile.values[field.semanticType] === "string" &&
    profile.values[field.semanticType].trim()
  );
}

function showProfileConfirmation(retry = false) {
  const profile = activeProfile();
  const matchingFields = matchingProfileFields(profile);
  if (!profile || !matchingFields.length) {
    const wasPending = state.pendingProfileConfirmation;
    state.pendingProfileConfirmation = false;
    $("profileConfirmationActions").hidden = true;
    if (wasPending) {
      window.speechSynthesis.cancel();
      state.isSpeaking = false;
      state.currentTurnId++;
      state.stateMachine.transition(Core.States.READY);
    }
    return false;
  }

  if (state.isListening) stopListeningSafely();
  if (state.isSpeaking) {
    window.speechSynthesis.cancel();
    state.isSpeaking = false;
  }
  state.currentTurnId++;
  state.pendingProfileConfirmation = true;
  $("profileConfirmationActions").hidden = false;
  $("assistantCaption").textContent = `${retry ? "Please answer yes or no. " : ""}` +
    `I already have ${matchingFields.length} matching detail${matchingFields.length === 1 ? "" : "s"} saved in "${profile.name}". Would you like me to fill them? Say yes or no, or choose a button.`;
  $("listeningStatus").textContent = "Use saved profile?";
  $("waveformHint").textContent = "*tap to answer yes or no";
  const turnId = ++state.currentTurnId;
  speakQuestion($("assistantCaption").textContent, turnId, () => {
    if (state.pendingProfileConfirmation && state.currentTurnId === turnId) {
      soundFX.playListeningStart();
      startListening();
    }
  });
  return true;
}

async function respondToProfileConfirmation(accepted) {
  if (!state.pendingProfileConfirmation) return;
  stopListeningSafely();
  state.pendingProfileConfirmation = false;
  $("profileConfirmationActions").hidden = true;
  if (state.isSpeaking) {
    window.speechSynthesis.cancel();
    state.isSpeaking = false;
    state.currentTurnId++;
  }
  $("waveformHint").textContent = "*tap to speak or pause";
  if (!accepted) {
    const message = "Okay, I won't use the saved profile. Let's fill out the form together.";
    $("assistantCaption").textContent = message;
    const turnId = ++state.currentTurnId;
    speakQuestion(message, turnId, () => {
      if (state.currentTurnId === turnId && !state.stateMachine.is(Core.States.PAUSED)) {
        startGuidedFill();
      }
    });
    return;
  }
  await fillMatchingProfile();
}

async function fillMatchingProfile() {
  const profile = activeProfile();
  if (!profile || !state.tabId) {
    showToast("Select a profile and open a webpage form first");
    state.stateMachine.transition(Core.States.READY);
    return;
  }

  const fillableFields = matchingProfileFields(profile);
  if (!fillableFields.length) {
    showToast("No empty fields match the selected profile");
    $("assistantCaption").textContent = "No empty form fields match the saved profile.";
    state.stateMachine.transition(Core.States.READY);
    return;
  }

  let filled = 0;
  let failed = 0;
  for (const field of fillableFields) {
    try {
      const response = await chrome.tabs.sendMessage(state.tabId, {
        type: "FORMMATE_FILL_FIELD",
        fieldId: field.id,
        value: profile.values[field.semanticType]
      });
      if (response?.success) {
        field.currentValue = response.value;
        field.filled = true;
        filled++;
      } else {
        failed++;
        console.warn(`Profile value was not applied to "${field.label}":`, response?.error || "No details returned");
      }
    } catch (error) {
      failed++;
      console.error(`Could not apply profile value to "${field.label}":`, error);
    }
  }

  renderCurrentFieldCard();
  const summary = `Filled ${filled} matching field${filled === 1 ? "" : "s"}${failed ? `; ${failed} need review` : ""}. Review the form before submitting.`;
  $("listeningStatus").textContent = "Start Conversation";
  $("assistantCaption").textContent = summary;
  state.stateMachine.transition(Core.States.READY);
  showToast(summary);

  state.history.push({
    role: "assistant",
    text: summary,
    timestamp: new Date().toLocaleTimeString(),
    fieldId: null,
    fieldLabel: "Saved profile"
  });

  const next = nextUnfilledField();
  const summaryTurn = ++state.currentTurnId;
  speakQuestion(summary, summaryTurn, () => {
    if (state.currentTurnId !== summaryTurn) return;
    if (next) {
      startSessionTimer();
      askQuestionForField(next.id);
    } else {
      announceCompletion();
    }
  });
}

// --- Toast Feedback Helper ---
let toastTimer = null;
function showToast(message) {
  const toast = $("toast");
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove("show"), 2200);
}

// --- Transcript Display Synchronizer ---
function setTranscript(text) {
  const y = $("youSaidTranscript");
  const t = $("transcriptText");
  if (y) y.textContent = text;
  if (t) t.textContent = text;
}

// Initialize State Machine with UI callback
state.stateMachine = new Core.ConversationStateMachine((newState, oldState, meta) => {
  renderStateUI(newState, oldState, meta);
});

// --- State Machine UI Synchronizer ---

function renderStateUI(s) {
  const circle = $("heroVoiceControl");
  const mainLabel = $("circleMainLabel");
  const subLabel = $("circleSubLabel");
  const bottomStatus = $("bottomBarStatusText");
  const sessionStatusText = $("sessionStatusText");
  const shell = document.querySelector(".glass-shell");
  const wave = $("waveform");
  const listeningStatus = $("listeningStatus");

  // Synchronize new Waveform and Listening Status
  if (wave) {
    if (s === Core.States.SPEAKING) {
      wave.classList.remove("is-user-speaking");
      wave.classList.add("is-ai-speaking");
    } else if (s === Core.States.LISTENING) {
      wave.classList.remove("is-ai-speaking");
      wave.classList.add("is-user-speaking");
    } else {
      wave.classList.remove("is-ai-speaking", "is-user-speaking");
    }
  }

  if (circle) circle.className = "liquid-glass-circle";
  if (shell) shell.classList.remove("speaking", "listening");

  switch (s) {
    case Core.States.SCANNING:
      if (circle) circle.classList.add("state-processing");
      if (mainLabel) mainLabel.textContent = "Scanning...";
      if (subLabel) subLabel.textContent = "Reading webpage form";
      if (listeningStatus) listeningStatus.textContent = "Scanning...";
      if (bottomStatus) bottomStatus.textContent = "Scanning page...";
      if (sessionStatusText) sessionStatusText.textContent = "Scanning";
      break;

    case Core.States.SPEAKING:
      if (circle) circle.classList.add("state-speaking");
      if (shell) shell.classList.add("speaking");
      if (mainLabel) mainLabel.textContent = "Speaking...";
      if (subLabel) subLabel.textContent = "Listening starts next";
      if (listeningStatus) listeningStatus.textContent = "Speaking...";
      if (bottomStatus) bottomStatus.textContent = "Speaking...";
      if (sessionStatusText) sessionStatusText.textContent = "Assistant Speaking";
      break;

    case Core.States.LISTENING:
      if (circle) circle.classList.add("state-listening");
      if (shell) shell.classList.add("listening");
      if (mainLabel) mainLabel.textContent = "Listening...";
      if (subLabel) subLabel.textContent = "Speak naturally now";
      if (listeningStatus) listeningStatus.textContent = "Listening...";
      if (bottomStatus) bottomStatus.textContent = "Listening to you...";
      if (sessionStatusText) sessionStatusText.textContent = "Listening to you";
      break;

    case Core.States.PROCESSING:
    case Core.States.VALIDATING:
      if (circle) circle.classList.add("state-processing");
      if (mainLabel) mainLabel.textContent = "Understanding...";
      if (subLabel) subLabel.textContent = "Updating form";
      if (listeningStatus) listeningStatus.textContent = "Understanding...";
      if (bottomStatus) bottomStatus.textContent = "Processing...";
      if (sessionStatusText) sessionStatusText.textContent = "Active Session";
      break;

    case Core.States.WAITING_FOR_TAP:
      if (circle) circle.classList.add("state-waiting-for-tap");
      if (mainLabel) mainLabel.textContent = "Tap to speak";
      if (subLabel) subLabel.textContent = "Take your time";
      if (listeningStatus) listeningStatus.textContent = "Tap to speak";
      if (bottomStatus) bottomStatus.textContent = "Waiting for tap";
      if (sessionStatusText) sessionStatusText.textContent = "Waiting for you";
      break;

    case Core.States.PAUSED:
      if (circle) circle.classList.add("state-paused");
      if (mainLabel) mainLabel.textContent = "Paused";
      if (subLabel) subLabel.textContent = "Tap to resume";
      if (listeningStatus) listeningStatus.textContent = "Paused";
      if (bottomStatus) bottomStatus.textContent = "Paused";
      if (sessionStatusText) sessionStatusText.textContent = "Paused";
      break;

    case Core.States.COMPLETED:
      if (circle) circle.classList.add("state-ready");
      if (mainLabel) mainLabel.textContent = "All Done!";
      if (subLabel) subLabel.textContent = "Form ready for review";
      if (listeningStatus) listeningStatus.textContent = "All Done!";
      if (bottomStatus) bottomStatus.textContent = "Ready to submit";
      if (sessionStatusText) sessionStatusText.textContent = "Completed";
      break;

    case Core.States.ERROR:
      if (circle) circle.classList.add("state-error");
      if (mainLabel) mainLabel.textContent = "Tap to retry";
      if (subLabel) subLabel.textContent = "Click circle to resume";
      if (listeningStatus) listeningStatus.textContent = "Tap to retry";
      if (bottomStatus) bottomStatus.textContent = "Needs attention";
      if (sessionStatusText) sessionStatusText.textContent = "Attention";
      break;

    case Core.States.READY:
      if (circle) circle.classList.add("state-ready");
      if (mainLabel) mainLabel.textContent = "Start Conversation";
      if (subLabel) subLabel.textContent = "Tap to talk";
      if (listeningStatus) listeningStatus.textContent = "Start Conversation";
      if (bottomStatus) bottomStatus.textContent = "Form ready";
      if (sessionStatusText) sessionStatusText.textContent = "Ready";
      break;

    case Core.States.IDLE:
    default:
      if (circle) circle.classList.add("state-ready");
      const readyTitle = state.fields.length ? "Start Conversation" : "Scan Page";
      if (mainLabel) mainLabel.textContent = readyTitle;
      if (subLabel) subLabel.textContent = state.fields.length ? "Tap to talk" : "Open a form";
      if (listeningStatus) listeningStatus.textContent = readyTitle;
      if (bottomStatus) bottomStatus.textContent = state.fields.length ? "Ready" : "Waiting for scan";
      if (sessionStatusText) sessionStatusText.textContent = "Ready";
      break;
  }

  updateProgressIndicator();
  updatePauseButtonLabel();
}

// --- Session Timer (00:06 format) ---

function startSessionTimer() {
  if (state.sessionInterval) clearInterval(state.sessionInterval);
  state.sessionSeconds = 0;
  state.sessionInterval = setInterval(() => {
    state.sessionSeconds++;
    const mins = String(Math.floor(state.sessionSeconds / 60)).padStart(2, "0");
    const secs = String(state.sessionSeconds % 60).padStart(2, "0");
    const timerEl = $("turnTimerText");
    if (timerEl) timerEl.textContent = `${mins}:${secs}`;
  }, 1000);
}

function stopSessionTimer() {
  if (state.sessionInterval) {
    clearInterval(state.sessionInterval);
    state.sessionInterval = null;
  }
}

// --- Dynamic Progress Indicator ---

function updateProgressIndicator() {
  const progressText = $("progressText");
  const trackBar = $("progressTrackBar");
  if (!progressText) return;

  if (!state.fields.length) {
    progressText.textContent = "0 of 0 completed (0%)";
    if (trackBar) trackBar.style.width = "0%";
    return;
  }

  const filledCount = state.fields.filter(fieldIsFilled).length;
  const pct = Math.round((filledCount / state.fields.length) * 100);
  progressText.textContent = `${filledCount} of ${state.fields.length} completed (${pct}%)`;
  if (trackBar) trackBar.style.width = `${pct}%`;
}

function fieldIsFilled(field) {
  return Boolean(String(field.currentValue || "").trim());
}

function currentField() {
  if (!state.currentFieldId) return null;
  return state.fields.find((f) => f.id === state.currentFieldId) || null;
}

function nextUnfilledField() {
  return state.fields.find((field) => !fieldIsFilled(field)) || null;
}

// --- Current Field Card Renderer ---

function renderCurrentFieldCard() {
  const card = $("currentFieldCard");
  const title = $("currentFieldTitle");
  const valueDisplay = $("currentFieldValue");
  const iconBadge = $("fieldIconBadge");
  const valBadge = $("fieldValidationBadge");
  const valText = $("fieldValidationText");

  if (!card) return;

  const field = currentField();
  if (!field) {
    if (title) title.textContent = "No field selected";
    if (valueDisplay) {
      valueDisplay.textContent = "Awaiting question…";
      valueDisplay.className = "field-value-text placeholder";
    }
    if (iconBadge) iconBadge.innerHTML = FIELD_ICONS.general;
    if (valBadge) valBadge.className = "validation-status-badge neutral";
    if (valText) valText.textContent = "Scan page to begin";
    return;
  }

  if (title) {
    title.textContent = `${field.label}${field.required ? " *" : ""}`;
  }

  const iconSvg = FIELD_ICONS[field.semanticType] || FIELD_ICONS[field.kind] || FIELD_ICONS.general;
  if (iconBadge) iconBadge.innerHTML = iconSvg;

  const val = String(field.currentValue || "").trim();
  if (valueDisplay) {
    if (val) {
      valueDisplay.textContent = val;
      valueDisplay.className = "field-value-text";
    } else {
      valueDisplay.textContent = "Awaiting your answer…";
      valueDisplay.className = "field-value-text placeholder";
    }
  }

  if (valBadge && valText) {
    if (val) {
      valBadge.className = "validation-status-badge success";
      valText.textContent = "Auto-formatting success";
    } else if (field.required) {
      valBadge.className = "validation-status-badge neutral";
      valText.textContent = "Required field";
    } else {
      valBadge.className = "validation-status-badge neutral";
      valText.textContent = "Optional field";
    }
  }

  updateProgressIndicator();
}

// --- Live Caption Progressive Reveal ---

function revealAssistantCaption(fullText, durationMs = 2500) {
  const captionEl = $("assistantCaption");
  if (!captionEl) return;

  if (state.captionInterval) {
    clearInterval(state.captionInterval);
    state.captionInterval = null;
  }

  const words = fullText.split(/\s+/);
  if (words.length <= 3 || durationMs < 500) {
    captionEl.textContent = fullText;
    return;
  }

  let index = 0;
  captionEl.textContent = "";

  const intervalTime = Math.max(45, Math.floor(durationMs / (words.length + 1)));

  state.captionInterval = setInterval(() => {
    if (index < words.length) {
      captionEl.textContent = (captionEl.textContent ? captionEl.textContent + " " : "") + words[index];
      index++;
    } else {
      clearInterval(state.captionInterval);
      state.captionInterval = null;
      captionEl.textContent = fullText;
    }
  }, intervalTime);
}

// --- Speech Synthesis (TTS) Engine ---

// Retain references globally to prevent Chrome V8 Garbage Collector cutting off speech
window._activeSpeechUtterances = window._activeSpeechUtterances || new Set();

let cachedSpeechVoices = [];
function updateSpeechVoices() {
  if (typeof window !== "undefined" && "speechSynthesis" in window) {
    const v = window.speechSynthesis.getVoices();
    if (v && v.length > 0) {
      cachedSpeechVoices = v;
    }
  }
}
updateSpeechVoices();
if (typeof window !== "undefined" && "speechSynthesis" in window) {
  window.speechSynthesis.onvoiceschanged = updateSpeechVoices;
}

function getPreferredVoice(lang) {
  if (!("speechSynthesis" in window)) return null;
  const voices = cachedSpeechVoices.length > 0 ? cachedSpeechVoices : window.speechSynthesis.getVoices();
  if (!voices || voices.length === 0) return null;

  const isHindi = String(lang).startsWith("hi");
  if (isHindi) {
    return (
      voices.find((v) => v.name.includes("Google") && (v.lang === "hi-IN" || v.lang === "hi_IN" || v.name.toLowerCase().includes("hindi"))) ||
      voices.find((v) => v.lang.startsWith("hi") || v.name.toLowerCase().includes("hindi")) ||
      voices[0]
    );
  }

  const isAssamese = String(lang).startsWith("as");
  if (isAssamese) {
    return (
      voices.find((v) => v.lang.startsWith("as") || v.name.toLowerCase().includes("assamese")) ||
      voices.find((v) => v.name.includes("Google") && (v.lang === "hi-IN" || v.lang === "en-IN")) ||
      voices[0]
    );
  }

  // English (India) preference, followed by premium natural voices
  return (
    // 1. Google Indian English
    voices.find((v) => v.name.includes("Google") && (v.lang === "en-IN" || v.lang === "en_IN" || v.name.toLowerCase().includes("india"))) ||
    // 2. Any Indian English voice (Rishi, Veena, Neerja, etc.)
    voices.find((v) => v.lang === "en-IN" || v.lang === "en_IN" || v.name.toLowerCase().includes("india")) ||
    // 3. Google UK English Female / Google US English (super clear, natural)
    voices.find((v) => v.name.includes("Google UK English Female") || v.name.includes("Google US English")) ||
    // 4. Natural / Enhanced / Samantha / Siri / Jenny / Karen
    voices.find((v) => (v.name.includes("Natural") || v.name.includes("Enhanced") || v.name.includes("Samantha") || v.name.includes("Jenny")) && v.lang.startsWith("en")) ||
    // 5. Any English voice
    voices.find((v) => v.lang.startsWith("en")) ||
    voices.find((v) => v.default) ||
    voices[0] ||
    null
  );
}

function speakQuestion(text, turnId, onComplete, speechText = text) {
  // If muted or TTS not available
  if (!state.speakEnabled || !("speechSynthesis" in window)) {
    revealAssistantCaption(text, 600);
    setTimeout(() => {
      if (state.currentTurnId === turnId && typeof onComplete === "function") {
        onComplete();
      }
    }, 700);
    return;
  }

  // Cancel any previous stuck utterance and resume audio engine if stuck
  try {
    if (window.speechSynthesis.speaking || window.speechSynthesis.pending) {
      window.speechSynthesis.cancel();
    }
    window.speechSynthesis.resume();
  } catch (_) {}

  const utterance = new SpeechSynthesisUtterance(speechText);
  window._activeSpeechUtterances.add(utterance);

  utterance.lang = state.lang;
  utterance.rate = 0.95;
  utterance.pitch = 1.0;

  const voice = getPreferredVoice(state.lang);
  if (voice) utterance.voice = voice;

  state.activeUtterance = utterance;
  state.isSpeaking = true;
  state.stateMachine.transition(Core.States.SPEAKING);

  // Estimate speech duration for progressive caption reveal
  const wordCount = speechText.split(/\s+/).length;
  const estimatedDurationMs = Math.max(1400, Math.min(8500, wordCount * 330));
  revealAssistantCaption(text, estimatedDurationMs);

  let finished = false;
  let speechStarted = false;

  const handleDone = () => {
    if (finished) return;
    finished = true;
    state.isSpeaking = false;
    state.activeUtterance = null;
    window._activeSpeechUtterances.delete(utterance);

    if (state.captionInterval) {
      clearInterval(state.captionInterval);
      state.captionInterval = null;
      $("assistantCaption").textContent = text;
    }

    if (state.currentTurnId === turnId && typeof onComplete === "function") {
      onComplete();
    }
  };

  utterance.onstart = () => {
    speechStarted = true;
    state.isSpeaking = true;
  };

  utterance.onend = () => {
    handleDone();
  };

  utterance.onerror = (evt) => {
    // Ignore intentional cancel/interrupt events
    if (evt.error === "interrupted" || evt.error === "canceled") {
      window._activeSpeechUtterances.delete(utterance);
      return;
    }
    console.warn("SpeechSynthesis error:", evt.error);
    handleDone();
  };

  // Watchdog: Chrome periodically pauses speech synthesis on long utterances
  const keepAlive = setInterval(() => {
    if (!finished && state.isSpeaking && window.speechSynthesis.speaking) {
      window.speechSynthesis.resume();
    } else {
      clearInterval(keepAlive);
    }
  }, 3000);

  // Safety fallback: only triggers if speech never started after 8 seconds
  setTimeout(() => {
    if (!finished && !speechStarted && state.isSpeaking && state.currentTurnId === turnId) {
      console.warn("SpeechSynthesis startup timeout triggered.");
      clearInterval(keepAlive);
      handleDone();
    }
  }, 8000);

  // 50ms delay before speak() allows Chrome audio thread to process cancel() cleanly
  setTimeout(() => {
    try {
      window.speechSynthesis.speak(utterance);
      window.speechSynthesis.resume();
    } catch (e) {
      console.error("SpeechSynthesis speak exception:", e);
      handleDone();
    }
  }, 50);
}

// --- Speech Recognition (Turn-based, Deduplicated) ---

function setupSpeechRecognition() {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) return null;

  const recognition = new SpeechRecognition();
  recognition.lang = state.lang;
  recognition.continuous = false;
  recognition.interimResults = true;
  recognition.maxAlternatives = 5;
  return recognition;
}

function clearInactivityTimer() {
  if (state.inactivityTimer) {
    clearTimeout(state.inactivityTimer);
    state.inactivityTimer = null;
  }
}

function showMicrophonePermissionPrompt(permissionState = "prompt", helpText = "") {
  state.microphonePermissionState = permissionState;
  const overlay = $("microphonePermissionOverlay");
  const message = $("microphonePermissionMessage");
  const help = $("microphonePermissionHelp");
  const requestButton = $("requestMicrophonePermissionBtn");
  const settingsButton = $("openMicrophoneSettingsBtn");
  if (!overlay) return;

  message.textContent = helpText && permissionState === "denied"
    ? "Chrome or your operating system blocked microphone access. You can try again or allow it in microphone settings."
    : "Formmate needs microphone access for voice input. Click Turn on microphone and Chrome will ask you to allow it. The microphone is only used while you are speaking with Formmate.";
  requestButton.textContent = "Turn on microphone";
  settingsButton.hidden = !(helpText && permissionState === "denied");
  help.textContent = helpText;
  help.hidden = !helpText;
  overlay.hidden = false;
}

function hideMicrophonePermissionPrompt() {
  $("microphonePermissionOverlay").hidden = true;
}

async function initializeMicrophonePermission() {
  try {
    if (!navigator.permissions?.query) throw new Error("Permissions API unavailable");
    const permissionStatus = await navigator.permissions.query({ name: "microphone" });
    const updatePrompt = () => {
      if (permissionStatus.state === "granted") {
        state.microphonePermissionState = "granted";
        hideMicrophonePermissionPrompt();
      } else if (permissionStatus.state === "denied" &&
          !$("microphonePermissionOverlay").hidden &&
          !$("microphonePermissionHelp").hidden) {
        state.microphonePermissionState = "denied";
      } else {
        showMicrophonePermissionPrompt(permissionStatus.state);
      }
    };
    permissionStatus.onchange = updatePrompt;
    updatePrompt();
  } catch (_) {
    showMicrophonePermissionPrompt("prompt");
  }
}

function requestMicrophonePermission() {
  const button = $("requestMicrophonePermissionBtn");
  button.disabled = true;
  button.textContent = "Opening permission page…";
  chrome.tabs.create({ url: chrome.runtime.getURL("microphone-permission.html") }, () => {
    if (chrome.runtime.lastError) {
      showMicrophonePermissionPrompt("prompt", "Chrome could not open the microphone permission page. Try opening the extension in a normal tab, or check Chrome microphone settings.");
    } else {
      $("microphonePermissionMessage").textContent = "A new tab is open. Allow microphone access there, then return to Formmate. Choose the persistent Allow option if Chrome offers one.";
    }
    button.disabled = false;
    button.textContent = "Open permission page";
  });
}

function openMicrophoneSettings() {
  const settingsUrl = `chrome://settings/content/siteDetails?site=${encodeURIComponent(location.origin)}`;
  chrome.tabs.create({ url: settingsUrl }, () => {
    if (chrome.runtime.lastError) {
      chrome.tabs.create({ url: "chrome://settings/content/microphone" });
    }
  });
}

function resetInactivityTimer(turnId = state.currentTurnId) {
  clearInactivityTimer();
  state.inactivityTimer = setTimeout(() => {
    if (state.currentTurnId === turnId &&
        state.isListening &&
        state.stateMachine.is(Core.States.LISTENING)) {
      repeatCurrentQuestion(turnId);
    }
  }, state.inactivityTimeoutMs);
}

function repeatCurrentQuestion(turnId) {
  if (state.currentTurnId !== turnId ||
      state.isProcessingAnswer ||
      !state.currentFieldId ||
      state.stateMachine.is(Core.States.PAUSED) ||
      state.stateMachine.is(Core.States.COMPLETED)) {
    return;
  }

  stopListeningSafely();
  const field = currentField();
  if (!field) return;

  const lastQuestion = [...state.history].reverse().find(
    (entry) => entry.role === "assistant" && entry.fieldId === field.id
  );
  const promptText = lastQuestion?.text || Core.generateHumanPrompt(field, state.lang);
  const retryTurnId = ++state.currentTurnId;

  state.history.push({
    role: "assistant",
    text: promptText,
    timestamp: new Date().toLocaleTimeString(),
    fieldId: field.id,
    fieldLabel: field.label
  });

  speakQuestion(promptText, retryTurnId, () => {
    if (state.currentTurnId === retryTurnId &&
        !state.stateMachine.is(Core.States.PAUSED)) {
      soundFX.playListeningStart();
      startListening();
    }
  });
}

function stopListeningSafely() {
  clearInactivityTimer();
  state.isListening = false;
  if (state.recognition) {
    try {
      state.recognition.abort();
    } catch (_) {}
  }
}

function startListening() {
  if (state.microphonePermissionState !== "granted") {
    showMicrophonePermissionPrompt(state.microphonePermissionState === "denied" ? "denied" : "prompt");
    return;
  }

  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) {
    state.stateMachine.transition(Core.States.ERROR);
    return;
  }

  // Cancel any active TTS utterance synchronously
  window.speechSynthesis.cancel();
  state.isSpeaking = false;

  stopListeningSafely();

  const recognition = setupSpeechRecognition();
  if (!recognition) return;
  state.recognition = recognition;

  const turnId = state.currentTurnId;
  let finalDelivered = false;

  recognition.onstart = () => {
    state.isListening = true;
    state.stateMachine.transition(Core.States.LISTENING);
    resetInactivityTimer(turnId);
  };

  recognition.onresult = (event) => {
    let interim = "";
    const finalSegments = [];
    const finalAlternatives = [];

    for (let i = event.resultIndex; i < event.results.length; ++i) {
      if (event.results[i].isFinal) {
        const result = event.results[i];
        finalSegments.push(result[0].transcript);
        finalAlternatives.push(
          Array.from({ length: result.length }, (_, alternativeIndex) =>
            result[alternativeIndex].transcript
          )
        );
      } else {
        interim += event.results[i][0].transcript;
      }
    }
    const final = finalSegments.join("");

    // Reset inactivity timer when speech activity is detected
    resetInactivityTimer(turnId);

    if (interim) {
      setTranscript(`“${interim}”`);
    }

    if (final.trim() && !finalDelivered) {
      finalDelivered = true;
      clearInactivityTimer();
      stopListeningSafely();

      const field = currentField();
      const alternativesCount = Math.max(
        1,
        ...finalAlternatives.map((alternatives) => alternatives.length)
      );
      const transcriptCandidates = Array.from(
        { length: alternativesCount },
        (_, alternativeIndex) =>
          finalAlternatives
            .map((alternatives) => alternatives[alternativeIndex] || alternatives[0])
            .join("")
      );
      const optionAnswer = field?.options?.length
        ? transcriptCandidates
          .map((candidate) => ({
            candidate,
            match: Core.matchOption(field.options, candidate, field.semanticType)
          }))
          .find(({ match }) => match.matched)?.candidate
        : null;
      const phoneAnswer = field?.semanticType === "phone"
        ? transcriptCandidates.find((candidate) => Core.parseSpokenPhone(candidate))
        : null;
      const emailAnswer = field?.semanticType === "email"
        ? transcriptCandidates.map((candidate) => Core.parseSpokenEmail(candidate)).find(Boolean)
        : null;
      const answer = (optionAnswer || phoneAnswer || emailAnswer || final).trim();
      setTranscript(`“${answer}”`);

      // Log user turn
      state.history.push({
        role: "user",
        text: answer,
        timestamp: new Date().toLocaleTimeString(),
        fieldId: state.currentFieldId,
        fieldLabel: currentField()?.label || "General"
      });

      handleUserAnswer(answer, turnId);
    }
  };

  recognition.onerror = (event) => {
    if (event.error === "no-speech") {
      repeatCurrentQuestion(turnId);
      return;
    }

    if (event.error === "not-allowed" || event.error === "service-not-allowed") {
      stopListeningSafely();
      state.stateMachine.transition(Core.States.ERROR);
      if (event.error === "not-allowed") {
        state.microphonePermissionState = "denied";
        showMicrophonePermissionPrompt("denied", "Use the Open microphone settings button to allow access, then return and click Turn on microphone again.");
      }
      setTranscript(event.error === "not-allowed"
        ? "Microphone access denied in Chrome"
        : "Speech recognition service is unavailable");
      return;
    }

    if (event.error === "audio-capture" || event.error === "network" ||
        event.error === "language-not-supported") {
      stopListeningSafely();
      state.stateMachine.transition(Core.States.ERROR);
      setTranscript(`Speech recognition error: ${event.error}`);
    }
  };

  recognition.onend = () => {
    state.isListening = false;
    clearInactivityTimer();
    if (!finalDelivered && state.stateMachine.is(Core.States.LISTENING)) {
      repeatCurrentQuestion(turnId);
    }
  };

  try {
    recognition.start();
  } catch (error) {
    state.stateMachine.transition(Core.States.WAITING_FOR_TAP);
  }
}

// --- Guided Conversation Orchestrator ---

function askQuestionForField(fieldId, isIntro = false) {
  const field = state.fields.find((f) => f.id === fieldId);
  if (!field) return;

  state.currentTurnId++;
  const turnId = state.currentTurnId;
  state.currentFieldId = field.id;

  renderCurrentFieldCard();

  let promptText = Core.generateHumanPrompt(field, state.lang);

  // If this is the start of the guided conversation, initiate warmly like a human companion!
  if (isIntro) {
    const isHindi = state.lang.startsWith("hi");
    promptText = isHindi
      ? `नमस्ते! मैं FORMMATE हूँ, आपका फॉर्म सहायक। चलिए इस फॉर्म को साथ मिलकर भरते हैं। सबसे पहले, ${promptText}`
      : `Hello! I'm FORMMATE, your form companion. Let's fill out this form together! First, ${promptText.charAt(0).toLowerCase() + promptText.slice(1)}`;
  }

  // Log assistant question
  state.history.push({
    role: "assistant",
    text: promptText,
    timestamp: new Date().toLocaleTimeString(),
    fieldId: field.id,
    fieldLabel: field.label
  });

  // Speak aloud, then play chime and automatically activate microphone when speech ends!
  speakQuestion(promptText, turnId, () => {
    if (state.currentTurnId === turnId && !state.stateMachine.is(Core.States.PAUSED)) {
      soundFX.playListeningStart();
      startListening();
    }
  });
}

function startGuidedFill() {
  soundFX.ensureContext();
  startSessionTimer();

  if (!state.fields.length) {
    scanCurrentPage(true);
    return;
  }

  const next = nextUnfilledField() || state.fields[0];
  if (next) {
    const isIntro = !state.history.some((h) => h.role === "assistant");
    askQuestionForField(next.id, isIntro);
  } else {
    announceCompletion();
  }
}

function announceCompletion() {
  soundFX.playComplete();
  state.currentFieldId = null;
  state.stateMachine.transition(Core.States.COMPLETED);
  renderCurrentFieldCard();

  const isHindi = state.lang.startsWith("hi");
  const completionMsg = isHindi
    ? "बहुत बढ़िया! सभी फ़ील्ड भर दिए गए हैं। कृपया सबमिट करने से पहले अपने उत्तरों की समीक्षा करें।"
    : "All detected fields now have values! Please review your entries, then submit the form directly on the webpage.";

  state.history.push({
    role: "assistant",
    text: completionMsg,
    timestamp: new Date().toLocaleTimeString(),
    fieldId: null,
    fieldLabel: "Completion"
  });

  speakQuestion(completionMsg, ++state.currentTurnId, () => {});
}

// --- Answer Processing & Field Filling ---

async function handleUserAnswer(rawText, turnId, skipNameCorrection = false) {
  if (turnId !== state.currentTurnId) {
    return; // Discard stale turn results
  }

  if (state.isProcessingAnswer) return;
  state.isProcessingAnswer = true;
  state.stateMachine.transition(Core.States.PROCESSING);

  if (state.pendingProfileConfirmation) {
    const accepted = Core.parseBooleanAnswer(rawText);
    if (accepted === null) {
      state.isProcessingAnswer = false;
      showProfileConfirmation(true);
      return;
    }
    await respondToProfileConfirmation(accepted);
    state.isProcessingAnswer = false;
    return;
  }

  if (state.pendingNameCorrection) {
    const pending = state.pendingNameCorrection;
    state.pendingNameCorrection = null;
    const decision = Core.parseBooleanAnswer(rawText);
    const answer = decision === true ? pending.suggestion
      : decision === false ? pending.rawText
        : rawText;
    state.isProcessingAnswer = false;
    await handleUserAnswer(answer, turnId, true);
    return;
  }

  // 1. Check for Conversational Control Commands
  const command = Core.recognizeCommand(rawText);
  if (command) {
    await executeCommand(command);
    state.isProcessingAnswer = false;
    return;
  }

  // 2. Authoritative Active Field Check
  const field = currentField();
  if (!field) {
    state.stateMachine.transition(Core.States.READY);
    state.isProcessingAnswer = false;
    return;
  }

  if (!skipNameCorrection) {
    const suggestion = Core.suggestNameCorrection(rawText, field.semanticType);
    if (suggestion && Core.normalizeText(suggestion) !== Core.normalizeText(rawText)) {
      state.pendingNameCorrection = { rawText, suggestion };
      state.isProcessingAnswer = false;
      const prompt = state.lang.startsWith("hi")
        ? `मैंने “${rawText}” सुना। इससे मिलता-जुलता नाम “${suggestion}” है। क्या मैं यही वर्तनी इस्तेमाल करूँ? हाँ कहें, या नहीं कहें ताकि आपका मूल नाम रखा जाए।`
        : `I heard “${rawText}”. A name that sounds similar is “${suggestion}”. Should I use that spelling? Say yes to use it, or no to keep your original.`;
      state.history.push({
        role: "assistant",
        text: prompt,
        timestamp: new Date().toLocaleTimeString(),
        fieldId: field.id,
        fieldLabel: field.label
      });
      $("listeningStatus").textContent = "Confirm name spelling";
      $("waveformHint").textContent = "*say yes to use suggestion, no to keep yours";
      const confirmTurn = ++state.currentTurnId;
      speakQuestion(prompt, confirmTurn, () => {
        if (state.pendingNameCorrection && state.currentTurnId === confirmTurn &&
            !state.stateMachine.is(Core.States.PAUSED)) {
          startListening();
        }
      });
      return;
    }
  }

  state.stateMachine.transition(Core.States.VALIDATING);

  // 3. Dispatch to Content Script (Field-Specific Validation & DOM Update)
  let response;
  try {
    response = await chrome.tabs.sendMessage(state.tabId, {
      type: "FORMMATE_FILL_FIELD",
      fieldId: field.id,
      value: state.lang.startsWith("hi")
        ? Core.transliterateHindiToLatin(rawText)
        : rawText
    });
  } catch (error) {
    response = { success: false, error: "Connection lost. Please scan page again." };
  }

  if (response?.success) {
    soundFX.playSuccess(); // Confirmation chime!
    field.currentValue = response.value;
    field.filled = true;
    renderCurrentFieldCard();

    const next = nextUnfilledField();
    const confirmation = Core.generateConfirmationMessage(field, response.value, next, state.lang);

    state.history.push({
      role: "assistant",
      text: confirmation,
      timestamp: new Date().toLocaleTimeString(),
      fieldId: field.id,
      fieldLabel: field.label
    });

    state.isProcessingAnswer = false;

    // Speak confirmation, then automatically advance to next field!
    const confirmTurn = ++state.currentTurnId;
    const spokenConfirmation = field.semanticType === "phone"
      ? confirmation.replace(
          response.value,
          Core.formatPhoneForSpeech(response.value, state.lang)
        )
      : confirmation;
    speakQuestion(confirmation, confirmTurn, () => {
      if (next && state.currentTurnId === confirmTurn && !state.stateMachine.is(Core.States.PAUSED)) {
        askQuestionForField(next.id);
      } else if (!next) {
        announceCompletion();
      }
    }, spokenConfirmation);
    return;
  }

  // 4. Handle Clarification / Ambiguity
  if (response?.ambiguous) {
    state.stateMachine.transition(Core.States.WAITING_FOR_TAP);
    const clarification = Core.generateClarificationMessage(field, rawText, response.candidates, state.lang);
    speakQuestion(clarification, ++state.currentTurnId, () => {
      startListening();
    });
    state.isProcessingAnswer = false;
    return;
  }

  // 5. Handle Required Checkbox Declined
  if (response?.requiredConsentDeclined) {
    field.currentValue = "";
    field.filled = false;
    renderCurrentFieldCard();
    state.stateMachine.transition(Core.States.WAITING_FOR_TAP);

    const isHindi = state.lang.startsWith("hi");
    const declineMsg = isHindi
      ? "सहमति बॉक्स खाली रखा गया है। यह घोषणा आवश्यक है, लेकिन आपका निर्णय मान्य है। आगे बढ़ने के लिए टैप करें।"
      : "The checkbox was left unchecked. This declaration is required to register, but your choice is respected. Tap when ready.";

    speakQuestion(declineMsg, ++state.currentTurnId, () => {});
    state.isProcessingAnswer = false;
    return;
  }

  // 6. General Validation Error
  state.stateMachine.transition(Core.States.WAITING_FOR_TAP);
  const isHindi = state.lang.startsWith("hi");
  const errorMsg = response?.error || (isHindi
    ? `मैं “${field.label}” के लिए वह समझ नहीं पाया। कृपया फिर से बोलें या एडिट पर टैप करें।`
    : `I couldn't fill “${field.label}” with that. Please say it again or tap Edit.`);

  speakQuestion(errorMsg, ++state.currentTurnId, () => {
    // Wait for user tap to retry
  });

  state.isProcessingAnswer = false;
}

// --- Conversational Commands Implementation ---

async function executeCommand(cmd) {
  const field = currentField();
  const isHindi = state.lang.startsWith("hi");

  switch (cmd) {
    case "REPEAT":
      if (field) {
        askQuestionForField(field.id);
      } else {
        startGuidedFill();
      }
      break;

    case "GO_BACK": {
      if (!field) {
        startGuidedFill();
        break;
      }
      const idx = state.fields.findIndex((f) => f.id === field.id);
      if (idx > 0) {
        const prev = state.fields[idx - 1];
        askQuestionForField(prev.id);
      } else {
        const firstNotice = isHindi ? "हम पहले फ़ील्ड पर हैं।" : "We are already at the first field.";
        speakQuestion(firstNotice, ++state.currentTurnId, () => startListening());
      }
      break;
    }

    case "SKIP":
      if (field) {
        const skipNotice = isHindi ? `“${field.label}” को छोड़ दिया गया है।` : `Skipping ${field.label}.`;
        const next = nextUnfilledField();
        speakQuestion(skipNotice, ++state.currentTurnId, () => {
          if (next) askQuestionForField(next.id);
          else announceCompletion();
        });
      } else {
        startGuidedFill();
      }
      break;

    case "START_AGAIN":
      if (state.fields.length) {
        askQuestionForField(state.fields[0].id);
      }
      break;

    case "STOP_LISTENING":
      togglePause();
      break;

    case "READ_ANSWERS":
      openTranscriptModal();
      break;

    case "NEXT_QUESTION": {
      const next = nextUnfilledField();
      if (next) askQuestionForField(next.id);
      else announceCompletion();
      break;
    }

    case "CHANGE_ANSWER":
      openEditModal();
      break;

    default:
      break;
  }
}

// --- Pause & Resume ---

function togglePause() {
  if (state.stateMachine.is(Core.States.PAUSED)) {
    // Resume
    state.stateMachine.transition(Core.States.READY);
    const field = currentField();
    if (field) {
      askQuestionForField(field.id);
    } else {
      startGuidedFill();
    }
  } else {
    // Pause
    stopListeningSafely();
    window.speechSynthesis.cancel();
    state.isSpeaking = false;
    state.stateMachine.transition(Core.States.PAUSED);
  }
  updatePauseButtonLabel();
}

function updatePauseButtonLabel() {
  const btn = $("ctrlPauseBtn");
  const label = $("pauseBtnLabel");
  if (!btn || !label) return;

  if (state.stateMachine.is(Core.States.PAUSED)) {
    label.textContent = "Resume";
    btn.classList.add("paused-active");
  } else {
    label.textContent = "Pause";
    btn.classList.remove("paused-active");
  }
}

// --- End Session ---

function endSessionSafely() {
  stopListeningSafely();
  window.speechSynthesis.cancel();
  stopSessionTimer();
  state.isSpeaking = false;
  state.isProcessingAnswer = false;
  state.currentFieldId = null;
  state.stateMachine.transition(Core.States.IDLE);
  renderCurrentFieldCard();

  setTranscript("Session ended. Form values preserved.");
  $("assistantCaption").textContent = "Session ended safely. All your entries on the webpage have been saved. You can submit the form whenever you are ready.";
}

// --- Manual Field Edit Modal ---

function openEditModal() {
  const field = currentField();
  if (!field) return;

  // Stop speech/mic while user is editing
  stopListeningSafely();
  window.speechSynthesis.cancel();

  const overlay = $("editModalOverlay");
  const container = $("dynamicEditContainer");
  const title = $("editModalTitle");

  if (title) title.textContent = `Edit “${field.label}”`;

  // Build input control tailored to field type
  let inputHtml = "";
  const currentVal = field.currentValue || "";

  if (field.kind === "select" && field.options?.length) {
    inputHtml = `
      <select id="modalEditInput" class="glass-select">
        <option value="">-- Choose an option --</option>
        ${field.options.map((o) => `
          <option value="${escapeHtml(o.value)}" ${o.label === currentVal || o.value === currentVal ? "selected" : ""}>
            ${escapeHtml(o.label)}
          </option>
        `).join("")}
      </select>`;
  } else if (field.kind === "radio" && field.options?.length) {
    inputHtml = `
      <div id="modalEditRadioGroup" class="edit-radio-group">
        ${field.options.map((o, idx) => `
          <label class="edit-radio-option">
            <input type="radio" name="modalEditRadio" value="${escapeHtml(o.value)}" ${o.label === currentVal || o.value === currentVal ? "checked" : ""}>
            <span>${escapeHtml(o.label)}</span>
          </label>
        `).join("")}
      </div>`;
  } else if (field.kind === "checkbox") {
    const isChecked = currentVal.includes("Yes") || currentVal.includes("agree") || currentVal.includes("Checked");
    inputHtml = `
      <label class="edit-checkbox-option">
        <input type="checkbox" id="modalEditInput" ${isChecked ? "checked" : ""}>
        <span>${escapeHtml(field.label)}</span>
      </label>`;
  } else if (field.inputType === "date" || field.semanticType === "date" || field.semanticType === "date_of_birth") {
    inputHtml = `<input type="date" id="modalEditInput" class="glass-input" value="${escapeHtml(currentVal)}">`;
  } else if (field.kind === "textarea") {
    inputHtml = `<textarea id="modalEditInput" class="glass-textarea" rows="3">${escapeHtml(currentVal)}</textarea>`;
  } else {
    inputHtml = `<input type="${escapeHtml(field.inputType || "text")}" id="modalEditInput" class="glass-input" value="${escapeHtml(currentVal)}" placeholder="Type value here…">`;
  }

  container.innerHTML = inputHtml;
  overlay.hidden = false;

  const firstInput = container.querySelector("input, select, textarea");
  if (firstInput) firstInput.focus();
}

function closeEditModal() {
  $("editModalOverlay").hidden = true;
}

async function applyManualEdit() {
  const field = currentField();
  if (!field) return;

  let val = "";
  if (field.kind === "radio") {
    const selected = document.querySelector('input[name="modalEditRadio"]:checked');
    val = selected ? selected.value : "";
  } else if (field.kind === "checkbox") {
    const cb = $("modalEditInput");
    val = cb && cb.checked ? "Yes, I agree" : "No, I do not agree";
  } else {
    const input = $("modalEditInput");
    val = input ? input.value : "";
  }

  closeEditModal();

  // Send update to content script
  try {
    const response = await chrome.tabs.sendMessage(state.tabId, {
      type: "FORMMATE_FILL_FIELD",
      fieldId: field.id,
      value: val
    });

    if (response?.success) {
      field.currentValue = response.value;
      field.filled = Boolean(String(field.currentValue || "").trim());
      renderCurrentFieldCard();
      $("assistantCaption").textContent = `Updated ${field.label} to “${field.currentValue}”.`;
    }
  } catch (error) {
    // Local fallback
    field.currentValue = val;
    field.filled = Boolean(val);
    renderCurrentFieldCard();
  }
}

// --- Transcript Modal ---

function openTranscriptModal() {
  const overlay = $("transcriptModalOverlay");
  const container = $("transcriptScrollContainer");

  if (!state.history.length) {
    container.innerHTML = `<div class="transcript-entry assistant">No conversation turns recorded yet.</div>`;
  } else {
    container.innerHTML = state.history.map((h) => `
      <div class="transcript-entry ${h.role}">
        <div class="transcript-entry-meta">
          <span>${h.role === "assistant" ? "FORMMATE" : "YOU"} · ${escapeHtml(h.fieldLabel || "")}</span>
          <span>${escapeHtml(h.timestamp)}</span>
        </div>
        <div>${escapeHtml(h.text)}</div>
      </div>
    `).join("");
  }

  overlay.hidden = false;
}

function closeTranscriptModal() {
  $("transcriptModalOverlay").hidden = true;
}

// --- Settings Modal ---

function openSettingsModal() {
  stopListeningSafely();
  window.speechSynthesis.cancel();
  $("languageSelect").value = state.lang;
  $("speakToggle").checked = state.speakEnabled;
  $("timeoutSelect").value = String(state.inactivityTimeoutMs);
  $("settingsModalOverlay").hidden = false;
}

function closeSettingsModal() {
  const newLang = $("languageSelect").value;
  const langChanged = newLang !== state.lang;
  state.lang = newLang;
  document.documentElement.lang = newLang;
  state.speakEnabled = $("speakToggle").checked;
  state.inactivityTimeoutMs = parseInt($("timeoutSelect").value, 10) || 8000;
  $("settingsModalOverlay").hidden = true;

  if (langChanged) {
    const langLabels = { "en-IN": "English", "hi-IN": "हिन्दी (Hindi)", "as-IN": "অসমীয়া (Assamese)" };
    showToast(`Language set to ${langLabels[newLang] || newLang}`);
    const field = currentField();
    if (field && !state.stateMachine.is(Core.States.IDLE) && !state.stateMachine.is(Core.States.COMPLETED)) {
      askQuestionForField(field.id);
    }
  }
}

// --- Page Scanning Bridge ---

async function getActiveTab() {
  try {
    const tabs = await chrome.tabs.query({ active: true, currentWindow: true });
    if (tabs && tabs.length > 0) return tabs[0];
  } catch (_) {}
  try {
    const tabs = await chrome.tabs.query({ active: true, lastFocusedWindow: true });
    if (tabs && tabs.length > 0) return tabs[0];
  } catch (_) {}
  return null;
}

async function scanCurrentPage(startImmediately = false) {
  state.pendingProfileConfirmation = false;
  state.pendingNameCorrection = null;
  $("profileConfirmationActions").hidden = true;
  state.stateMachine.transition(Core.States.SCANNING);
  try {
    const tab = await getActiveTab();
    if (!tab || typeof tab.id !== "number") {
      throw new Error("No active browser tab found.");
    }
    state.tabId = tab.id;

    // Check if the active tab is an internal or restricted browser page
    if (tab.url && (tab.url.startsWith("chrome://") || tab.url.startsWith("chrome-extension://") || tab.url.startsWith("edge://") || tab.url.startsWith("about:"))) {
      state.stateMachine.transition(Core.States.IDLE);
      $("assistantCaption").innerHTML = `To test FORMMATE, open an online form or try our local demo: <br><button id="openDemoBtn" class="glass-pill-btn" style="margin-top:8px;padding:4px 12px;font-size:12px;cursor:pointer;">Open Demo Registration Form</button>`;
      $("openDemoBtn")?.addEventListener("click", () => {
        soundFX.playTap();
        if (typeof chrome !== "undefined" && chrome.tabs && chrome.runtime?.getURL) {
          chrome.tabs.create({ url: chrome.runtime.getURL("demo/index.html") });
        } else {
          window.open("demo/index.html", "_blank");
        }
      });
      return;
    }

    // Inject core logic and content script
    await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      files: ["formmate-core.js", "content.js"]
    });

    const response = await chrome.tabs.sendMessage(tab.id, { type: "FORMMATE_SCAN" });
    if (!response?.ok) throw new Error(response?.error || "Could not scan page.");

    state.fields = response.fields || [];
    state.currentFieldId = null;

    if (state.fields.length > 0) {
      state.stateMachine.transition(Core.States.READY);
      $("assistantCaption").textContent = `Found ${state.fields.length} form field${state.fields.length === 1 ? "" : "s"} on this page. Tap 'Start Conversation' to begin!`;
      renderCurrentFieldCard();
      const askedToUseProfile = showProfileConfirmation();
      if (startImmediately && !askedToUseProfile) {
        startGuidedFill();
      }
    } else {
      state.stateMachine.transition(Core.States.IDLE);
      $("assistantCaption").innerHTML = `No editable form fields found on this page. <br><button id="openDemoBtn" class="edit-pill-btn" style="margin-top:8px;padding:6px 14px;cursor:pointer;">Open Demo Registration Form</button>`;
      $("openDemoBtn")?.addEventListener("click", () => {
        soundFX.playTap();
        if (typeof chrome !== "undefined" && chrome.tabs && chrome.runtime?.getURL) {
          chrome.tabs.create({ url: chrome.runtime.getURL("demo/index.html") });
        } else {
          window.open("demo/index.html", "_blank");
        }
      });
    }
  } catch (error) {
    console.warn("Scan warning:", error);
    state.stateMachine.transition(Core.States.IDLE);
    $("assistantCaption").innerHTML = `To test Formmate, open a form or launch our demo: <br><button id="openDemoBtn" class="edit-pill-btn" style="margin-top:10px;padding:6px 14px;cursor:pointer;">Open Demo Registration Form</button>`;
    $("openDemoBtn")?.addEventListener("click", () => {
      soundFX.playTap();
      if (typeof chrome !== "undefined" && chrome.tabs && chrome.runtime?.getURL) {
        chrome.tabs.create({ url: chrome.runtime.getURL("demo/index.html") });
      } else {
        window.open("demo/index.html", "_blank");
      }
    });
  }
}

function handlePopupStartRequest() {
  if (startRequestInProgress) return startRequestInProgress;

  startRequestInProgress = (async () => {
    const stored = await chrome.storage.local.get("formmateStartAssistantRequest");
    if (!stored.formmateStartAssistantRequest) return false;

    await chrome.storage.local.remove("formmateStartAssistantRequest");
    await scanCurrentPage(true);
    return true;
  })();

  return startRequestInProgress.finally(() => {
    startRequestInProgress = null;
  });
}

function escapeHtml(val) {
  return String(val ?? "").replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  }[c]));
}

// --- Initialization & Event Binding ---

async function init() {
  $("requestMicrophonePermissionBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    requestMicrophonePermission();
  });
  $("openMicrophoneSettingsBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    openMicrophoneSettings();
  });
  $("skipMicrophonePermissionBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    hideMicrophonePermissionPrompt();
  });
  initializeMicrophonePermission();
  chrome.runtime.onMessage.addListener((message) => {
    if (message?.type !== "FORMMATE_MIC_PERMISSION_RESULT") return;
    if (message.granted) {
      state.microphonePermissionState = "granted";
      hideMicrophonePermissionPrompt();
      showToast("Microphone is ready for voice input");
      return;
    }
    showMicrophonePermissionPrompt(
      message.blocked ? "denied" : "prompt",
      message.helpText || "Microphone access was not granted. Choose Allow in Chrome's permission prompt, then try again."
    );
  });

  // Voice control action (used for waveform and hero button)
  const handleVoiceTap = () => {
    soundFX.playTap();
    if (state.pendingProfileConfirmation && state.stateMachine.is(Core.States.WAITING_FOR_TAP)) {
      soundFX.playListeningStart();
      startListening();
    } else if (state.pendingProfileConfirmation) {
      showToast("Wait for the profile question, then tap to answer yes or no");
    } else if (state.stateMachine.is(Core.States.IDLE) || state.stateMachine.is(Core.States.READY)) {
      startGuidedFill();
    } else if (state.stateMachine.is(Core.States.WAITING_FOR_TAP)) {
      soundFX.playListeningStart();
      startListening();
    } else if (state.stateMachine.is(Core.States.LISTENING)) {
      stopListeningSafely();
      state.stateMachine.transition(Core.States.WAITING_FOR_TAP);
    } else if (state.stateMachine.is(Core.States.PAUSED)) {
      togglePause();
    } else if (state.stateMachine.is(Core.States.ERROR)) {
      scanCurrentPage(true);
    } else if (state.stateMachine.is(Core.States.COMPLETED)) {
      showToast("All fields are complete! Review on webpage.");
    }
  };

  $("waveform")?.addEventListener("click", handleVoiceTap);
  $("heroVoiceControl")?.addEventListener("click", handleVoiceTap);

  // New Step Navigation buttons
  $("repeatButton")?.addEventListener("click", () => {
    soundFX.playTap();
    showToast("Repeating current field");
    executeCommand("REPEAT");
  });
  $("backButton")?.addEventListener("click", () => {
    soundFX.playTap();
    executeCommand("GO_BACK");
  });
  $("nextButton")?.addEventListener("click", () => {
    soundFX.playTap();
    executeCommand("NEXT_QUESTION");
  });

  // Submit Form button
  $("submitForm")?.addEventListener("click", () => {
    soundFX.playTap();
    const unfilled = state.fields.filter(f => !fieldIsFilled(f));
    if (unfilled.length === 0 && state.fields.length > 0) {
      soundFX.playComplete();
      showToast("Your form is ready to submit on the webpage");
    } else if (state.fields.length > 0) {
      showToast(`${unfilled.length} field${unfilled.length === 1 ? "" : "s"} remaining before submit`);
      const next = unfilled[0];
      if (next) askQuestionForField(next.id);
    } else {
      showToast("Open a form to begin");
    }
  });

  // Review Submit button on transcript card
  $("reviewSubmit")?.addEventListener("click", () => {
    soundFX.playTap();
    if (currentField()) {
      openEditModal();
    } else {
      showToast("Entry submitted for review");
    }
  });

  // Auto search toggle
  $("autoSearch")?.addEventListener("click", () => {
    soundFX.playTap();
    state.autoSearchEnabled = !state.autoSearchEnabled;
    $("autoSearch").setAttribute("aria-pressed", String(state.autoSearchEnabled));
    const autoState = $("autoState");
    if (autoState) autoState.textContent = state.autoSearchEnabled ? "on" : "off";
    showToast(`Auto search ${state.autoSearchEnabled ? "turned on" : "turned off"}`);
  });

  // Legacy Controls row compatibility
  $("ctrlPauseBtn")?.addEventListener("click", () => { soundFX.playTap(); togglePause(); });
  $("ctrlRepeatBtn")?.addEventListener("click", () => { soundFX.playTap(); executeCommand("REPEAT"); });
  $("ctrlBackBtn")?.addEventListener("click", () => { soundFX.playTap(); executeCommand("GO_BACK"); });
  $("ctrlSkipBtn")?.addEventListener("click", () => { soundFX.playTap(); executeCommand("SKIP"); });

  // Header and Edit buttons
  $("profilesBtn")?.addEventListener("click", () => { soundFX.playTap(); openProfilesModal(); });
  $("settingsBtn")?.addEventListener("click", () => { soundFX.playTap(); openSettingsModal(); });
  $("editCurrentFieldBtn")?.addEventListener("click", () => { soundFX.playTap(); openEditModal(); });
  $("endSessionBtn")?.addEventListener("click", () => { soundFX.playTap(); endSessionSafely(); });

  // Edit modal buttons
  $("closeEditModalBtn")?.addEventListener("click", () => { soundFX.playTap(); closeEditModal(); });
  $("cancelEditBtn")?.addEventListener("click", () => { soundFX.playTap(); closeEditModal(); });
  $("saveEditBtn")?.addEventListener("click", () => { soundFX.playTap(); applyManualEdit(); });

  // Transcript modal buttons
  $("closeTranscriptModalBtn")?.addEventListener("click", () => { soundFX.playTap(); closeTranscriptModal(); });
  $("closeTranscriptBtn")?.addEventListener("click", () => { soundFX.playTap(); closeTranscriptModal(); });

  // Settings modal buttons
  $("closeSettingsModalBtn")?.addEventListener("click", () => { soundFX.playTap(); closeSettingsModal(); });
  $("saveSettingsBtn")?.addEventListener("click", () => { soundFX.playTap(); closeSettingsModal(); });
  $("rescanPageBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    closeSettingsModal();
    scanCurrentPage(false);
  });

  $("closeProfilesModalBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    $("profilesModalOverlay").hidden = true;
  });
  $("closeProfilesBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    $("profilesModalOverlay").hidden = true;
  });
  $("profileSelect")?.addEventListener("change", async (event) => {
    const previousActiveProfileId = state.activeProfileId;
    state.activeProfileId = event.target.value;
    try {
      await chrome.storage.local.set({ formmateActiveProfileId: state.activeProfileId });
      renderProfiles();
      $("profilesModalOverlay").hidden = true;
      if (state.fields.length && !showProfileConfirmation()) {
        $("assistantCaption").textContent = activeProfile()
          ? "This profile has no saved details matching the empty fields in this form."
          : "No profile is selected. You can fill the form manually or start the guided assistant.";
      }
    } catch (error) {
      state.activeProfileId = previousActiveProfileId;
      renderProfiles();
      console.error("Could not save selected profile:", error);
      showToast("Could not save profile selection");
    }
  });
  $("acceptProfileBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    respondToProfileConfirmation(true);
  });
  $("declineProfileBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    respondToProfileConfirmation(false);
  });

  // Initial scan on load
  try {
    await loadSavedProfiles();
  } catch (error) {
    console.error("Could not load saved profiles:", error);
    showToast("Could not load profiles from extension storage");
  }
  chrome.storage.onChanged.addListener((changes, areaName) => {
    if (areaName === "local" && changes.formmateStartAssistantRequest?.newValue) {
      handlePopupStartRequest()
        .then((started) => started || handlePopupStartRequest())
        .catch((error) => {
          console.error("Could not start assistant from popup:", error);
          showToast("Could not start the assistant");
        });
    }

    if (areaName !== "local" ||
        (!changes.formmateProfiles && !changes.formmateActiveProfileId)) return;
    const previousActiveProfileId = state.activeProfileId;
    const previousProfile = activeProfile();
    loadSavedProfiles().then(() => {
      const currentProfile = activeProfile();
      const profileDetailsChanged = JSON.stringify(previousProfile) !== JSON.stringify(currentProfile);
      if (state.fields.length &&
          (state.activeProfileId !== previousActiveProfileId || profileDetailsChanged)) {
        if (!showProfileConfirmation()) {
          $("assistantCaption").textContent = currentProfile
            ? "No matching saved information is available for this form."
            : "No profile is selected. You can fill the form manually or start the guided assistant.";
        }
      }
    }).catch((error) => {
      console.error("Could not refresh saved profiles:", error);
      showToast("Could not refresh saved profiles");
    });
  });
  const startedFromPopup = await handlePopupStartRequest().catch((error) => {
    console.error("Could not check popup assistant start request:", error);
    showToast("Could not start the assistant");
    return false;
  });
  if (!startedFromPopup) scanCurrentPage(false);
}

document.addEventListener("DOMContentLoaded", init);
