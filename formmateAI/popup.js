const PROFILE_STORAGE_KEYS = ["formmateProfiles", "formmateActiveProfileId"];
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

const $ = (id) => document.getElementById(id);
const state = { profiles: [], activeProfileId: "" };

function renderProfiles() {
  const select = $("profileSelect");
  select.disabled = false;
  $("newProfileBtn").disabled = false;
  select.replaceChildren();

  if (!state.profiles.length) {
    const option = document.createElement("option");
    option.value = "";
    option.textContent = "No saved profiles — create one";
    select.append(option);
  } else {
    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.textContent = "Choose a profile";
    select.append(placeholder);
    state.profiles.forEach((profile) => {
      const option = document.createElement("option");
      option.value = profile.id;
      option.textContent = profile.name;
      select.append(option);
    });
  }

  select.value = state.activeProfileId;
  const hasSelection = state.profiles.some((profile) => profile.id === state.activeProfileId);
  $("editProfileBtn").disabled = !hasSelection;
  $("deleteProfileBtn").disabled = !hasSelection;
  if (!hasSelection && state.profiles.length) {
    $("profileStatus").textContent = "Select a profile to make it active in the side panel.";
  } else if (!state.profiles.length) {
    $("profileStatus").textContent = "Create a profile here, then choose it from the side panel.";
  } else {
    $("profileStatus").textContent = "The selected profile will be offered when a form is detected.";
  }
}

async function persistProfiles() {
  await chrome.storage.local.set({
    formmateProfiles: state.profiles,
    formmateActiveProfileId: state.activeProfileId
  });
  renderProfiles();
}

async function loadProfiles() {
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

function openEditor(profile = null) {
  $("profileEditor").hidden = false;
  $("profileEditor").dataset.profileId = profile?.id || "";
  $("profileName").value = profile?.name || "";
  $("profileError").textContent = "";
  $("profileFields").replaceChildren();

  PROFILE_FIELDS.forEach((field) => {
    const wrapper = document.createElement("div");
    wrapper.className = `profile-field${field.full ? " full" : ""}`;
    const label = document.createElement("label");
    const input = document.createElement("input");
    input.id = `profile-${field.key}`;
    input.type = field.type;
    input.autocomplete = "off";
    input.value = profile?.values?.[field.key] || "";
    label.htmlFor = input.id;
    label.textContent = field.label;
    wrapper.append(label, input);
    $("profileFields").append(wrapper);
  });

  $("profileName").focus();
}

$("profileEditor").addEventListener("keydown", (event) => {
  if (event.key !== "Enter" || !(event.target instanceof HTMLInputElement)) return;

  event.preventDefault();
  const inputs = [
    $("profileName"),
    ...$("profileFields").querySelectorAll("input")
  ];
  const currentIndex = inputs.indexOf(event.target);
  const nextInput = inputs[currentIndex + 1];

  if (nextInput) {
    nextInput.focus();
  } else if (currentIndex >= 0) {
    $("saveProfileBtn").focus();
  }
});

function closeEditor() {
  $("profileEditor").hidden = true;
  $("profileEditor").dataset.profileId = "";
  $("profileError").textContent = "";
}

async function saveProfile() {
  const button = $("saveProfileBtn");
  const originalLabel = button.textContent;
  const previousProfiles = state.profiles.slice();
  const previousActiveId = state.activeProfileId;
  try {
    const name = $("profileName").value.trim();
    if (!name) {
      $("profileError").textContent = "Enter a name for this profile.";
      $("profileName").focus();
      return;
    }

    const profileId = $("profileEditor").dataset.profileId ||
      globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    const values = {};
    PROFILE_FIELDS.forEach(({ key }) => {
      const value = $(`profile-${key}`).value.trim();
      if (value) values[key] = value;
    });
    const profile = { id: profileId, name, values };
    const index = state.profiles.findIndex((item) => item.id === profileId);
    if (index >= 0) state.profiles[index] = profile;
    else state.profiles.push(profile);
    state.activeProfileId = profileId;

    button.disabled = true;
    button.textContent = "Saving…";
    $("profileError").textContent = "";
    await persistProfiles();
    closeEditor();
    $("popupStatus").textContent = `Profile "${name}" saved on this browser.`;
  } catch (error) {
    state.profiles = previousProfiles;
    state.activeProfileId = previousActiveId;
    renderProfiles();
    console.error("Could not save profile:", error);
    $("profileError").textContent = `Could not save profile: ${error.message || "Unknown storage error"}`;
  } finally {
    button.disabled = false;
    button.textContent = originalLabel;
  }
}

$("profileSelect").addEventListener("change", async (event) => {
  const previousActiveId = state.activeProfileId;
  state.activeProfileId = event.target.value;
  try {
    await persistProfiles();
  } catch (error) {
    state.activeProfileId = previousActiveId;
    renderProfiles();
    console.error("Could not select profile:", error);
    $("profileStatus").textContent = `Could not select profile: ${error.message || "Storage error"}`;
  }
});

$("newProfileBtn").addEventListener("click", () => openEditor());
$("editProfileBtn").addEventListener("click", () => {
  const profile = state.profiles.find((item) => item.id === state.activeProfileId);
  if (profile) openEditor(profile);
});
$("cancelProfileBtn").addEventListener("click", closeEditor);
$("saveProfileBtn").addEventListener("click", saveProfile);
$("deleteProfileBtn").addEventListener("click", async () => {
  const profile = state.profiles.find((item) => item.id === state.activeProfileId);
  if (!profile || !window.confirm(`Delete the "${profile.name}" profile from this browser?`)) return;
  const previousProfiles = state.profiles;
  const previousActiveId = state.activeProfileId;
  state.profiles = state.profiles.filter((item) => item.id !== profile.id);
  state.activeProfileId = "";
  try {
    await persistProfiles();
    $("popupStatus").textContent = `Profile "${profile.name}" deleted.`;
  } catch (error) {
    state.profiles = previousProfiles;
    state.activeProfileId = previousActiveId;
    renderProfiles();
    console.error("Could not delete profile:", error);
    $("profileStatus").textContent = `Could not delete profile: ${error.message || "Storage error"}`;
  }
});

const openButton = $("openAssistant");
const statusLine = $("popupStatus");

openButton.addEventListener("click", async () => {
  openButton.disabled = true;
  statusLine.textContent = "Opening assistant…";
  try {
    const [tab] = await chrome.tabs.query({ active: true, lastFocusedWindow: true });
    if (!tab || typeof tab.id !== "number") {
      throw new Error("No active browser tab was found.");
    }
    await chrome.sidePanel.setOptions({
      tabId: tab.id,
      path: "sidepanel.html",
      enabled: true
    });
    await chrome.storage.local.set({ formmateStartAssistantRequest: Date.now() });
    await chrome.sidePanel.open({ tabId: tab.id });
    window.close();
  } catch (error) {
    await chrome.storage.local.remove("formmateStartAssistantRequest").catch((storageError) => {
      console.error("Could not clear assistant start request:", storageError);
    });
    statusLine.textContent = error?.message || "Could not open the side panel. Try again on a normal webpage.";
    openButton.disabled = false;
  }
});

loadProfiles().catch((error) => {
  console.error("Could not load saved profiles:", error);
  $("profileStatus").textContent = `Could not load saved profiles: ${error.message || "Storage error"}`;
});
