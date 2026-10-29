const $ = (id) => document.getElementById(id);

function reportPermission(granted, blocked = false, helpText = "") {
  chrome.runtime.sendMessage({
    type: "FORMMATE_MIC_PERMISSION_RESULT",
    granted,
    blocked,
    helpText
  }, () => void chrome.runtime.lastError);
}

function openMicrophoneSettings() {
  const url = `chrome://settings/content/siteDetails?site=${encodeURIComponent(location.origin)}`;
  chrome.tabs.create({ url }, () => {
    if (chrome.runtime.lastError) chrome.tabs.create({ url: "chrome://settings/content/microphone" });
  });
}

async function requestMicrophoneAccess() {
  const message = $("permissionMessage");
  const retryButton = $("retryPermissionBtn");
  const settingsButton = $("openSettingsBtn");
  const closeButton = $("closePermissionPageBtn");
  retryButton.hidden = true;
  settingsButton.hidden = true;
  closeButton.hidden = true;
  message.textContent = "Waiting for Chrome's microphone permission…";

  if (!navigator.mediaDevices?.getUserMedia) {
    const text = "Microphone access is unavailable in this browser context. Check Chrome and operating system microphone settings.";
    message.textContent = text;
    $("permissionHelp").textContent = text;
    $("permissionHelp").hidden = false;
    settingsButton.hidden = false;
    reportPermission(false, true, text);
    return;
  }

  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
    stream.getTracks().forEach((track) => track.stop());
    message.textContent = "Microphone access is ready. Return to Formmate and start voice input.";
    $("permissionHelp").textContent = "If Chrome offered a one-time and a persistent option, choose the persistent Allow option so voice remains available in the side panel.";
    $("permissionHelp").hidden = false;
    closeButton.hidden = false;
    reportPermission(true);
  } catch (error) {
    const blocked = error?.name === "NotAllowedError" || error?.name === "PermissionDeniedError";
    const text = blocked
      ? "Chrome blocked microphone access. Open the extension's microphone site settings and set Microphone to Allow, then try again."
      : error?.name === "NotFoundError"
        ? "No microphone was found. Connect or enable a microphone, then try again."
        : `Chrome could not access the microphone (${error?.name || "unknown error"}). Check your microphone and try again.`;
    message.textContent = text;
    $("permissionHelp").textContent = blocked
      ? "If Chrome's microphone setting is already Allow, check that microphone access is enabled in your operating system's privacy settings."
      : "This permission check does not record or send audio.";
    $("permissionHelp").hidden = false;
    retryButton.hidden = false;
    settingsButton.hidden = !blocked;
    reportPermission(false, blocked, text);
  }
}

$("retryPermissionBtn").addEventListener("click", requestMicrophoneAccess);
$("openSettingsBtn").addEventListener("click", openMicrophoneSettings);
$("closePermissionPageBtn").addEventListener("click", () => window.close());

requestMicrophoneAccess();
