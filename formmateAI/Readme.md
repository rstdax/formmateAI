# Formmate

Formmate is a Chrome extension that helps people fill web forms through a guided voice conversation. It scans the current page, asks about the fields it can identify, and fills answers for the user to review.

This is a practice prototype. It does not submit forms for you.

## Try it in Chrome

1. Download or clone this repository and extract jo bhi hai .
2. In Chrome, open `chrome://extensions`.
3. Turn on **Developer mode**.
4. Choose **Load unpacked** and select the `formmateAI` project folder (the folder containing `manifest.json`).
5. Open a form in a browser tab. Click the Formmate extension icon and choose **Open Assistant**.
6. In the side panel, scan the page if prompted, then tap the microphone to start. Allow microphone access if Chrome asks.
7. Answer each question. Formmate shows what it heard and fills the matching field. Review the values on the webpage and submit the form yourself.

To try it without a real form, use the assistant's **Open Demo Registration Form** link. The demo is local and does not send its sample entries anywhere.

You can pause or resume with the microphone button, repeat a question, go back, skip a field, or edit the current answer. Saved profiles are optional and can be created from the extension popup.

## What it uses

- Chrome Extension Manifest V3; Chrome 116 or newer.
- HTML, CSS, and plain JavaScript. No frontend framework or build step.
- Chrome Side Panel and extension APIs for the UI, page scanning, and browser-local profile storage.
- The browser's Web Speech API (`SpeechRecognition` / `webkitSpeechRecognition`) for voice input and `speechSynthesis` for spoken prompts.
- Local JavaScript in `formmate-core.js` for parsing, validation, and speech transcript cleanup; `content.js` reads and fills supported page fields.
- Node.js tests. From the project folder, run `npm test`.

There is no Formmate server, account, or remote AI API. Chrome's speech-recognition service may send audio to Google's service, depending on Chrome and device settings; this is handled by the browser, not Formmate.

## Limitations   we need improvement on it if we use speech to text model api then it will be more  userfirendly . 

- Speech recognition is provided by the browser. Accents, noise, names, and unusual spellings can still be transcribed incorrectly. Name spelling suggestions require confirmation; check important answers yourself.
- Works best with standard, visible HTML inputs, textareas, selects, and radio buttons. Custom controls may not be detected or filled correctly.
- Forms inside cross-origin iframes, closed shadow roots, or later steps that have not loaded may not be detected. Rescan after a form changes.
- Voice recognition generally needs an internet connection and browser microphone permission. Voice availability and quality vary by device and browser.
- Formmate does not solve CAPTCHAs, fill passwords or file uploads, accept consent automatically, or submit forms.
- Saved profiles stay in Chrome local storage on this device. They are not encrypted by Formmate; do not save passwords or highly sensitive identifiers.
