/**
 * Formmate runtime.
 *
 * This file owns the conversation state and the browser integration. The
 * presentation layer in sidepanel.js deliberately remains DOM-only.
 */

const Core = {
  States: Object.freeze({
    IDLE: "idle",
    SCANNING: "scanning",
    READY: "ready",
    SPEAKING: "speaking",
    LISTENING: "listening",
    PROCESSING: "processing",
    VALIDATING: "validating",
    WAITING_FOR_TAP: "waiting_for_tap",
    PAUSED: "paused",
    COMPLETED: "completed",
    ERROR: "error",
  }),
};

const PROFILE_FIELDS = Object.freeze([
  "full_name",
  "first_name",
  "last_name",
  "email",
  "phone",
  "date_of_birth",
  "state",
  "district",
  "city",
  "address",
  "gender",
  "pincode",
  "course",
  "consent",
]);

class ConversationStateMachine {
  constructor(initialState, onTransition) {
    this.current = initialState;
    this.onTransition = onTransition;
  }

  is(state) {
    return this.current === state;
  }

  transition(nextState) {
    this.current = nextState;
    if (typeof this.onTransition === "function") {
      this.onTransition(nextState);
    }
  }
}

const state = {
  fields: [],
  currentFieldId: "",
  history: [],
  profiles: [],
  activeProfileId: "",
  pendingProfileConfirmation: false,
  autoSearchEnabled: true,
  lang: "en-IN",
  speakEnabled: true,
  inactivityTimeoutMs: 8000,
  stateMachine: null,
  sessionInterval: null,
  sessionSeconds: 0,
  captionInterval: null,
  recognition: null,
  recognitionTimer: null,
};

state.stateMachine = new ConversationStateMachine(Core.States.IDLE, (next) => {
  renderStateUI(next);
  renderCurrentFieldCard();
});

const soundFX = {
  playTap() {},
  playListeningStart() {},
  playComplete() {},
};

function isChromeExtension() {
  return typeof chrome !== "undefined" && Boolean(chrome.runtime);
}

function activeProfile() {
  return state.profiles.find((profile) => profile.id === state.activeProfileId);
}

async function loadSavedProfiles() {
  if (!isChromeExtension() || !chrome.storage?.local) return;

  const saved = await chrome.storage.local.get([
    "formmateProfiles",
    "formmateActiveProfileId",
  ]);
  state.profiles = Array.isArray(saved.formmateProfiles)
    ? saved.formmateProfiles.filter(
        (profile) =>
          profile &&
          typeof profile.id === "string" &&
          typeof profile.name === "string" &&
          profile.values &&
          typeof profile.values === "object" &&
          !Array.isArray(profile.values),
      )
    : [];
  state.activeProfileId =
    typeof saved.formmateActiveProfileId === "string" &&
    state.profiles.some((profile) => profile.id === saved.formmateActiveProfileId)
      ? saved.formmateActiveProfileId
      : "";
  renderProfiles();
}

function currentField() {
  return state.fields.find((field) => field.id === state.currentFieldId) || null;
}

function nextUnfilledField() {
  return state.fields.find((field) => !fieldIsFilled(field)) || null;
}

function fieldIsFilled(field) {
  if (!field) return false;
  if (field.kind === "checkbox") return Boolean(field.currentValue);
  return String(field.currentValue || "").trim().length > 0;
}

function addHistory(speaker, text, fieldId = "") {
  state.history.push({ speaker, text, fieldId, timestamp: Date.now() });
}

function setCaption(text) {
  revealAssistantCaption(text, 900);
  addHistory("assistant", text, state.currentFieldId);
}

function matchingProfileFields() {
  const profile = activeProfile();
  if (!profile) return [];
  return state.fields.filter(
    (field) =>
      !fieldIsFilled(field) &&
      String(profile.values[field.semanticType] || profile.values[field.kind] || "").trim(),
  );
}

function showProfileConfirmation() {
  const matches = matchingProfileFields();
  if (!matches.length) return false;
  state.pendingProfileConfirmation = true;
  const actions = $("profileConfirmationActions");
  if (actions) actions.hidden = false;
  state.stateMachine.transition(Core.States.WAITING_FOR_TAP);
  setCaption(
    `I found ${matches.length} saved detail${matches.length === 1 ? "" : "s"} in your ${activeProfile().name} profile. Would you like to use them?`,
  );
  return true;
}

function hideProfileConfirmation() {
  state.pendingProfileConfirmation = false;
  const actions = $("profileConfirmationActions");
  if (actions) actions.hidden = true;
}

function respondToProfileConfirmation(accepted) {
  if (accepted) {
    const profile = activeProfile();
    matchingProfileFields().forEach((field) => {
      const value = profile.values[field.semanticType] || profile.values[field.kind];
      if (value) field.currentValue = String(value);
    });
    setCaption("Saved details applied. I’ll continue with the remaining fields.");
    hideProfileConfirmation();
    state.stateMachine.transition(Core.States.READY);
    askQuestionForField(nextUnfilledField()?.id);
  } else {
    hideProfileConfirmation();
    setCaption("No problem. We’ll fill this form together.");
    state.stateMachine.transition(Core.States.READY);
    askQuestionForField(nextUnfilledField()?.id);
  }
}

async function scanCurrentPage(isRetry = false) {
  if (!isRetry) stopListeningSafely();
  state.stateMachine.transition(Core.States.SCANNING);
  setCaption("Scanning the current webpage for form fields…");

  if (!isChromeExtension() || !chrome.tabs?.query) {
    state.fields = [];
    state.stateMachine.transition(Core.States.ERROR);
    setCaption("Open Formmate from a browser extension side panel to scan a webpage.");
    return;
  }

  try {
    const tabs = await chrome.tabs.query({ active: true, lastFocusedWindow: true });
    const tab = tabs[0];
    if (!tab?.id) throw new Error("No active browser tab was found.");
    const response = await sendMessageToActiveTab(
      tab.id,
      { type: "FORMMATE_SCAN_PAGE" },
    );
    const fields = Array.isArray(response?.fields) ? response.fields : [];
    state.fields = fields.map((field, index) => ({
      id: String(field.id || `field-${index + 1}`),
      label: String(field.label || field.name || `Field ${index + 1}`),
      semanticType: String(field.semanticType || field.type || "general"),
      kind: String(field.kind || field.inputType || "text"),
      inputType: String(field.inputType || field.type || "text"),
      options: Array.isArray(field.options) ? field.options : [],
      required: Boolean(field.required),
      currentValue: String(field.currentValue || field.value || ""),
    }));
    state.currentFieldId = nextUnfilledField()?.id || "";
    renderCurrentFieldCard();
    if (!state.fields.length) {
      state.stateMachine.transition(Core.States.ERROR);
      setCaption("I couldn’t find any fillable fields on this webpage.");
      return;
    }

    async function sendMessageToActiveTab(tabId, message) {
      try {
        return await chrome.tabs.sendMessage(tabId, message);
      } catch (firstError) {
        if (!chrome.scripting?.executeScript) throw firstError;
        await chrome.scripting.executeScript({
          target: { tabId },
          files: ["content.js"],
        });
        return chrome.tabs.sendMessage(tabId, message);
      }
    }
    state.stateMachine.transition(Core.States.READY);
    setCaption(`I found ${state.fields.length} form field${state.fields.length === 1 ? "" : "s"}.`);
    if (!showProfileConfirmation()) askQuestionForField(state.currentFieldId);
  } catch (error) {
    console.error("Could not scan the current webpage:", error);
    state.stateMachine.transition(Core.States.ERROR);
    setCaption(
      "I couldn’t access this webpage. Reload it, then make sure Formmate has permission to run on the site.",
    );
  }
}

function startGuidedFill() {
  if (!state.fields.length) {
    scanCurrentPage(false);
    return;
  }
  startSessionTimer();
  state.stateMachine.transition(Core.States.READY);
  askQuestionForField(nextUnfilledField()?.id || state.currentFieldId);
}

function askQuestionForField(fieldId) {
  const field = state.fields.find((candidate) => candidate.id === fieldId);
  if (!field) {
    state.stateMachine.transition(Core.States.COMPLETED);
    setCaption("All fields are complete.");
    return;
  }
  state.currentFieldId = field.id;
  renderCurrentFieldCard();
  const question = `What is your ${field.label.toLowerCase()}?`;
  setCaption(question);
  speak(question);
  state.stateMachine.transition(Core.States.WAITING_FOR_TAP);
}

function speak(text) {
  if (!state.speakEnabled || typeof window.speechSynthesis === "undefined") return;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = state.lang;
  utterance.onend = () => {
    if (state.stateMachine.is(Core.States.SPEAKING)) {
      state.stateMachine.transition(Core.States.WAITING_FOR_TAP);
    }
  };
  state.stateMachine.transition(Core.States.SPEAKING);
  window.speechSynthesis.speak(utterance);
}

function startListening() {
  const Recognition =
    window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!Recognition) {
    showToast("Speech recognition is not available in this browser.");
    state.stateMachine.transition(Core.States.WAITING_FOR_TAP);
    return;
  }
  stopListeningSafely();
  const recognition = new Recognition();
  state.recognition = recognition;
  recognition.lang = state.lang;
  recognition.interimResults = false;
  recognition.maxAlternatives = 1;
  recognition.onstart = () => state.stateMachine.transition(Core.States.LISTENING);
  recognition.onresult = (event) => {
    const text = event.results[0][0].transcript.trim();
    setTranscript(`“${text}”`);
    addHistory("user", text, state.currentFieldId);
    applyFieldValue(text);
  };
  recognition.onerror = (event) => {
    console.error("Speech recognition failed:", event.error);
    showToast("I couldn’t hear that. Tap the microphone and try again.");
    state.stateMachine.transition(Core.States.WAITING_FOR_TAP);
  };
  recognition.onend = () => {
    state.recognition = null;
    if (state.stateMachine.is(Core.States.LISTENING)) {
      state.stateMachine.transition(Core.States.WAITING_FOR_TAP);
    }
  };
  recognition.start();
  state.recognitionTimer = setTimeout(() => {
    if (state.recognition) stopListeningSafely();
  }, state.inactivityTimeoutMs);
}

function stopListeningSafely() {
  if (state.recognition) {
    state.recognition.onend = null;
    state.recognition.stop();
    state.recognition = null;
  }
  if (state.recognitionTimer) {
    clearTimeout(state.recognitionTimer);
    state.recognitionTimer = null;
  }
}

async function sendFieldValue(field) {
  if (!isChromeExtension() || !chrome.tabs?.query) return;
  const tabs = await chrome.tabs.query({ active: true, lastFocusedWindow: true });
  if (tabs[0]?.id) {
    await sendMessageToActiveTab(tabs[0].id, {
      type: "FORMMATE_FILL_FIELD",
      fieldId: field.id,
      value: field.currentValue,
    });
  }
}

function applyFieldValue(value) {
  const field = currentField();
  if (!field) return;
  field.currentValue = String(value).trim();
  sendFieldValue(field).catch((error) => {
    console.error("Could not update the webpage field:", error);
    showToast("The answer was saved here, but the webpage could not be updated.");
  });
  renderCurrentFieldCard();
  state.stateMachine.transition(Core.States.VALIDATING);
  const next = nextUnfilledField();
  if (next) {
    state.stateMachine.transition(Core.States.READY);
    askQuestionForField(next.id);
  } else {
    state.stateMachine.transition(Core.States.COMPLETED);
    setCaption("All fields are complete. Review the webpage before submitting.");
  }
}

function applyManualEdit() {
  const field = currentField();
  const input = $("modalEditInput");
  if (!field || !input) return;
  field.currentValue =
    input.type === "checkbox" ? (input.checked ? "Yes" : "") : input.value.trim();
  sendFieldValue(field).catch((error) => console.error("Could not apply manual edit:", error));
  closeEditModal();
  renderCurrentFieldCard();
  setCaption("Updated. Your answer has been applied to the webpage.");
}

function executeCommand(command) {
  if (command === "REPEAT") {
    askQuestionForField(state.currentFieldId);
    return;
  }
  if (command === "GO_BACK") {
    const index = state.fields.findIndex((field) => field.id === state.currentFieldId);
    askQuestionForField(state.fields[Math.max(0, index - 1)]?.id);
    return;
  }
  if (command === "NEXT_QUESTION" || command === "SKIP") {
    askQuestionForField(nextUnfilledField()?.id);
  }
}

function togglePause() {
  if (state.stateMachine.is(Core.States.PAUSED)) {
    state.stateMachine.transition(Core.States.WAITING_FOR_TAP);
  } else {
    stopListeningSafely();
    state.stateMachine.transition(Core.States.PAUSED);
  }
}

function endSessionSafely() {
  stopListeningSafely();
  stopSessionTimer();
  state.stateMachine.transition(Core.States.IDLE);
  setCaption("Session ended. Tap the microphone to start again.");
}

document.addEventListener("DOMContentLoaded", async () => {
  bindUIEvents();
  renderStateUI(state.stateMachine.current);
  renderCurrentFieldCard();
  try {
    await loadSavedProfiles();
    await scanCurrentPage(false);
  } catch (error) {
    console.error("Formmate failed to initialize:", error);
    state.stateMachine.transition(Core.States.ERROR);
    setCaption("Formmate could not initialize. Reload the side panel and try again.");
  }
});
