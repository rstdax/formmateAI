/**
 * FORMMATE AI — Side Panel UI Controller
 * Apple Liquid Glass Theme
 *
 * Contains all frontend/DOM code extracted from sidepanel.js:
 * - DOM selector shorthand
 * - Field icon SVG map
 * - State machine → UI synchronizer (renderStateUI)
 * - Session timer display
 * - Progress indicator renderer
 * - Current field card renderer
 * - Live caption progressive reveal
 * - Toast feedback helper
 * - Transcript display synchronizer
 * - Profile dropdown renderer
 * - Pause button label updater
 * - All modal open/close functions (edit, transcript, settings, profiles)
 * - HTML escape utility
 * - All event listener bindings (init)
 */

// NOTE: This file depends on the following globals being available from sidepanel.js:
//   state, Core, soundFX, PROFILE_FIELDS (unused here but referenced in sidepanel.js),
//   startGuidedFill, scanCurrentPage, togglePause, executeCommand, applyManualEdit,
//   loadSavedProfiles, respondToProfileConfirmation, showProfileConfirmation,
//   activeProfile, matchingProfileFields, fieldIsFilled, currentField, nextUnfilledField,
//   askQuestionForField, endSessionSafely

// ---------------------------------------------------------------------------
// DOM Selector Shorthand
// ---------------------------------------------------------------------------
const $ = (id) => document.getElementById(id);

// ---------------------------------------------------------------------------
// Field Icon SVGs by Semantic Category
// ---------------------------------------------------------------------------
const FIELD_ICONS = {
  full_name: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`,
  first_name: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`,
  last_name: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`,
  email: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>`,
  phone: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.21 15.8a19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/></svg>`,
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
  general: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>`,
};

// ---------------------------------------------------------------------------
// State Machine → UI Synchronizer
// ---------------------------------------------------------------------------

/**
 * Called by the ConversationStateMachine callback on every state transition.
 * Updates all visual elements (circle, labels, waveform, status bars) to
 * reflect the new conversation state.
 * @param {string} s - One of the Core.States values
 */
function renderStateUI(s) {
  const circle = $("heroVoiceControl");
  const mainLabel = $("circleMainLabel");
  const subLabel = $("circleSubLabel");
  const bottomStatus = $("bottomBarStatusText");
  const sessionStatusText = $("sessionStatusText");
  const shell = document.querySelector(".glass-shell");
  const wave = $("waveform");
  const listeningStatus = $("listeningStatus");

  // Synchronize waveform animation class
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
      if (sessionStatusText)
        sessionStatusText.textContent = "Assistant Speaking";
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
      const readyTitle = state.fields.length
        ? "Start Conversation"
        : "Scan Page";
      if (mainLabel) mainLabel.textContent = readyTitle;
      if (subLabel)
        subLabel.textContent = state.fields.length
          ? "Tap to talk"
          : "Open a form";
      if (listeningStatus) listeningStatus.textContent = readyTitle;
      if (bottomStatus)
        bottomStatus.textContent = state.fields.length
          ? "Ready"
          : "Waiting for scan";
      if (sessionStatusText) sessionStatusText.textContent = "Ready";
      break;
  }

  updateProgressIndicator();
  updatePauseButtonLabel();
}

// ---------------------------------------------------------------------------
// Session Timer Display (00:00 format)
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Progress Indicator
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Current Field Card Renderer
// ---------------------------------------------------------------------------

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

  const iconSvg =
    FIELD_ICONS[field.semanticType] ||
    FIELD_ICONS[field.kind] ||
    FIELD_ICONS.general;
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

// ---------------------------------------------------------------------------
// Live Caption Progressive Reveal
// ---------------------------------------------------------------------------

/**
 * Reveals `fullText` word-by-word into #assistantCaption over `durationMs`.
 * Falls back to instant display for very short text or durations.
 * @param {string} fullText
 * @param {number} durationMs
 */
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

  const intervalTime = Math.max(
    45,
    Math.floor(durationMs / (words.length + 1)),
  );

  state.captionInterval = setInterval(() => {
    if (index < words.length) {
      captionEl.textContent =
        (captionEl.textContent ? captionEl.textContent + " " : "") +
        words[index];
      index++;
    } else {
      clearInterval(state.captionInterval);
      state.captionInterval = null;
      captionEl.textContent = fullText;
    }
  }, intervalTime);
}

// ---------------------------------------------------------------------------
// Toast Feedback Helper
// ---------------------------------------------------------------------------

let toastTimer = null;

/**
 * Briefly displays a feedback message in the #toast element.
 * @param {string} message
 */
function showToast(message) {
  const toast = $("toast");
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove("show"), 2200);
}

// ---------------------------------------------------------------------------
// Transcript Display Synchronizer
// ---------------------------------------------------------------------------

/**
 * Updates both the inline transcript and the modal transcript text elements.
 * @param {string} text
 */
function setTranscript(text) {
  const y = $("youSaidTranscript");
  const t = $("transcriptText");
  if (y) y.textContent = text;
  if (t) t.textContent = text;
}

// ---------------------------------------------------------------------------
// Profile Dropdown Renderer
// ---------------------------------------------------------------------------

/**
 * Repopulates #profileSelect with current profiles from state,
 * and updates the profile button title and status text.
 */
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
  $("profilesBtn").title = profile
    ? `Selected profile: ${profile.name}`
    : "Choose a saved profile";
  $("profilesBtn").setAttribute(
    "aria-label",
    profile
      ? `Choose profile. Selected: ${profile.name}`
      : "Choose a saved profile",
  );
  $("profilePickerStatus").textContent = profile
    ? `Selected: ${profile.name}. Formmate will ask before filling any saved details.`
    : "No active profile. Create one from the extension popup.";
}

// ---------------------------------------------------------------------------
// Pause Button Label Updater
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Edit Modal
// ---------------------------------------------------------------------------

/**
 * Opens the manual field editor modal for the currently active field.
 * Builds a context-appropriate input control (select, radio, checkbox, date, textarea, text).
 */
function openEditModal() {
  const field = currentField();
  if (!field) return;

  // Stop speech/mic while user is editing
  stopListeningSafely();
  window.speechSynthesis.cancel();

  const overlay = $("editModalOverlay");
  const container = $("dynamicEditContainer");
  const title = $("editModalTitle");

  if (title) title.textContent = `Edit "${field.label}"`;

  const currentVal = field.currentValue || "";
  let inputHtml = "";

  if (field.kind === "select" && field.options?.length) {
    inputHtml = `
      <select id="modalEditInput" class="glass-select">
        <option value="">-- Choose an option --</option>
        ${field.options
          .map(
            (o) => `
          <option value="${escapeHtml(o.value)}" ${o.label === currentVal || o.value === currentVal ? "selected" : ""}>
            ${escapeHtml(o.label)}
          </option>
        `,
          )
          .join("")}
      </select>`;
  } else if (field.kind === "radio" && field.options?.length) {
    inputHtml = `
      <div id="modalEditRadioGroup" class="edit-radio-group">
        ${field.options
          .map(
            (o) => `
          <label class="edit-radio-option">
            <input type="radio" name="modalEditRadio" value="${escapeHtml(o.value)}" ${o.label === currentVal || o.value === currentVal ? "checked" : ""}>
            <span>${escapeHtml(o.label)}</span>
          </label>
        `,
          )
          .join("")}
      </div>`;
  } else if (field.kind === "checkbox") {
    const isChecked =
      currentVal.includes("Yes") ||
      currentVal.includes("agree") ||
      currentVal.includes("Checked");
    inputHtml = `
      <label class="edit-checkbox-option">
        <input type="checkbox" id="modalEditInput" ${isChecked ? "checked" : ""}>
        <span>${escapeHtml(field.label)}</span>
      </label>`;
  } else if (
    field.inputType === "date" ||
    field.semanticType === "date" ||
    field.semanticType === "date_of_birth"
  ) {
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

// ---------------------------------------------------------------------------
// Transcript Modal
// ---------------------------------------------------------------------------

function openTranscriptModal() {
  const overlay = $("transcriptModalOverlay");
  const container = $("transcriptScrollContainer");

  if (!state.history.length) {
    container.innerHTML = `<div class="transcript-entry assistant">No conversation turns recorded yet.</div>`;
  } else {
    container.innerHTML = state.history
      .map(
        (h) => `
      <div class="transcript-entry ${h.role}">
        <div class="transcript-entry-meta">
          <span>${h.role === "assistant" ? "FORMMATE" : "YOU"} · ${escapeHtml(h.fieldLabel || "")}</span>
          <span>${escapeHtml(h.timestamp)}</span>
        </div>
        <div>${escapeHtml(h.text)}</div>
      </div>
    `,
      )
      .join("");
  }

  overlay.hidden = false;
}

function closeTranscriptModal() {
  $("transcriptModalOverlay").hidden = true;
}

// ---------------------------------------------------------------------------
// Settings Modal
// ---------------------------------------------------------------------------

function openSettingsModal() {
  stopListeningSafely();
  window.speechSynthesis.cancel();
  $("languageSelect").value = state.lang;
  $("speakToggle").checked = state.speakEnabled;
  $("timeoutSelect").value = String(state.inactivityTimeoutMs);
  $("settingsModalOverlay").hidden = false;
}

/**
 * Reads settings values from the modal inputs back into state, then closes the modal.
 * If the language changed, shows a toast and re-asks the current field question.
 */
function closeSettingsModal() {
  const newLang = $("languageSelect").value;
  const langChanged = newLang !== state.lang;
  state.lang = newLang;
  document.documentElement.lang = newLang;
  state.speakEnabled = $("speakToggle").checked;
  state.inactivityTimeoutMs = parseInt($("timeoutSelect").value, 10) || 8000;
  $("settingsModalOverlay").hidden = true;

  if (langChanged) {
    const langLabels = {
      "en-IN": "English",
      "hi-IN": "हिन्दी (Hindi)",
      "as-IN": "অসমীয়া (Assamese)",
    };
    showToast(`Language set to ${langLabels[newLang] || newLang}`);
    const field = currentField();
    if (
      field &&
      !state.stateMachine.is(Core.States.IDLE) &&
      !state.stateMachine.is(Core.States.COMPLETED)
    ) {
      askQuestionForField(field.id);
    }
  }
}

// ---------------------------------------------------------------------------
// Profiles Modal
// ---------------------------------------------------------------------------

async function openProfilesModal() {
  try {
    const saved = await chrome.storage.local.get([
      "formmateProfiles",
      "formmateActiveProfileId",
    ]);
    state.profiles = Array.isArray(saved.formmateProfiles)
      ? saved.formmateProfiles.filter(
          (p) =>
            p &&
            typeof p.id === "string" &&
            typeof p.name === "string" &&
            p.values &&
            typeof p.values === "object" &&
            !Array.isArray(p.values),
        )
      : [];
    state.activeProfileId =
      typeof saved.formmateActiveProfileId === "string" &&
      state.profiles.some((p) => p.id === saved.formmateActiveProfileId)
        ? saved.formmateActiveProfileId
        : "";
    renderProfiles();
    $("profilesModalOverlay").hidden = false;
  } catch (error) {
    console.error("Could not load saved profiles:", error);
    showToast("Could not load saved profiles");
  }
}

// ---------------------------------------------------------------------------
// HTML Escape Utility
// ---------------------------------------------------------------------------

/**
 * Safely escapes a value for insertion into innerHTML.
 * @param {*} val
 * @returns {string}
 */
function escapeHtml(val) {
  return String(val ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;",
      })[c],
  );
}

// ---------------------------------------------------------------------------
// Event Listener Bindings
// ---------------------------------------------------------------------------

/**
 * Wires up all UI event listeners. Call this from DOMContentLoaded (or init()).
 * Depends on logic functions from sidepanel.js being in scope.
 */
function bindUIEvents() {
  // --- Hero voice control and waveform tap ---
  const handleVoiceTap = () => {
    soundFX.playTap();
    if (
      state.pendingProfileConfirmation &&
      state.stateMachine.is(Core.States.WAITING_FOR_TAP)
    ) {
      soundFX.playListeningStart();
      startListening();
    } else if (state.pendingProfileConfirmation) {
      showToast("Wait for the profile question, then tap to answer yes or no");
    } else if (
      state.stateMachine.is(Core.States.IDLE) ||
      state.stateMachine.is(Core.States.READY)
    ) {
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

  // --- Step navigation buttons ---
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

  // --- Submit form button ---
  $("submitForm")?.addEventListener("click", () => {
    soundFX.playTap();
    const unfilled = state.fields.filter((f) => !fieldIsFilled(f));
    if (unfilled.length === 0 && state.fields.length > 0) {
      soundFX.playComplete();
      showToast("Your form is ready to submit on the webpage");
    } else if (state.fields.length > 0) {
      showToast(
        `${unfilled.length} field${unfilled.length === 1 ? "" : "s"} remaining before submit`,
      );
      const next = unfilled[0];
      if (next) askQuestionForField(next.id);
    } else {
      showToast("Open a form to begin");
    }
  });

  // --- Review submit button on transcript card ---
  $("reviewSubmit")?.addEventListener("click", () => {
    soundFX.playTap();
    if (currentField()) {
      openEditModal();
    } else {
      showToast("Entry submitted for review");
    }
  });

  // --- Auto search toggle ---
  $("autoSearch")?.addEventListener("click", () => {
    soundFX.playTap();
    state.autoSearchEnabled = !state.autoSearchEnabled;
    $("autoSearch").setAttribute(
      "aria-pressed",
      String(state.autoSearchEnabled),
    );
    const autoState = $("autoState");
    if (autoState)
      autoState.textContent = state.autoSearchEnabled ? "on" : "off";
    showToast(
      `Auto search ${state.autoSearchEnabled ? "turned on" : "turned off"}`,
    );
  });

  // --- Legacy controls row ---
  $("ctrlPauseBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    togglePause();
  });
  $("ctrlRepeatBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    executeCommand("REPEAT");
  });
  $("ctrlBackBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    executeCommand("GO_BACK");
  });
  $("ctrlSkipBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    executeCommand("SKIP");
  });

  // --- Header and card action buttons ---
  $("profilesBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    openProfilesModal();
  });
  $("settingsBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    openSettingsModal();
  });
  $("editCurrentFieldBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    openEditModal();
  });
  $("viewTranscriptBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    openTranscriptModal();
  });
  $("endSessionBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    endSessionSafely();
  });

  // --- Edit modal ---
  $("closeEditModalBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    closeEditModal();
  });
  $("cancelEditBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    closeEditModal();
  });
  $("saveEditBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    applyManualEdit();
  });

  // --- Transcript modal ---
  $("closeTranscriptModalBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    closeTranscriptModal();
  });
  $("closeTranscriptBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    closeTranscriptModal();
  });

  // --- Settings modal ---
  $("closeSettingsModalBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    closeSettingsModal();
  });
  $("saveSettingsBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    closeSettingsModal();
  });
  $("rescanPageBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    closeSettingsModal();
    scanCurrentPage(false);
  });

  // --- Profiles modal ---
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
      await chrome.storage.local.set({
        formmateActiveProfileId: state.activeProfileId,
      });
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

  // --- Profile confirmation buttons ---
  $("acceptProfileBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    respondToProfileConfirmation(true);
  });
  $("declineProfileBtn")?.addEventListener("click", () => {
    soundFX.playTap();
    respondToProfileConfirmation(false);
  });
}
