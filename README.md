# FORMMATE AI / Hey RST

FORMMATE AI and Hey RST are two companion prototypes for helping people complete web forms through guided voice interaction. The Chrome extension assists on desktop; the Android app uses an accessibility service and a floating assistant while Chrome is open. Both can use a locally saved profile, ask before applying matching details, and leave review and submission to the user. The Chrome and Android profiles are stored separately; they do not sync with each other.

> **Prototype:** The included demo pages are mock forms for practice. They are not official government portals and do not send form submissions to a server.

## Project layout

The repository contains two separate deliverables:

```text
Formate-Ultimate-HeyRST/
├── formmateAI/                 # Chrome extension, demo pages, docs, and JS tests
│   ├── manifest.json
│   ├── popup.html / popup.js
│   ├── sidepanel.html / sidepanel.js
│   ├── content.js
│   ├── formmate-core.js
│   ├── demo/
│   ├── docs/
│   └── tests/
└── HeyRST/                     # Android Studio / Gradle project
    └── app/src/main/
```

## What it does

- Scans form controls available on the current page or Android screen and asks about them one at a time.
- Accepts spoken answers, interprets common conversational phrasing, and normalizes values such as dates, phone numbers, email addresses, and dropdown choices.
- Lets a user create a profile with common details such as name, contact information, date of birth, address, education, and occupation. The user can edit or delete profiles and choose which profile is active.
- Offers to fill matching empty fields from the active profile. The user must approve that offer; saved profiles are not used to choose consent checkboxes.
- Provides progress, repeat/back/next controls, transcript or history views, and manual editing for review.
- Never submits a form automatically. Review the values in the webpage and submit there yourself.

The form interpretation logic is deterministic application code. The current projects do not include a custom AI model, hosted AI API, backend, or server-side database.

## Technology stack

| Area | Chrome extension | Android app |
|---|---|---|
| Platform | Chrome Extension Manifest V3; Chrome 116 or newer | Native Android application; minimum Android 9 / API 28 |
| UI | HTML, CSS, and vanilla JavaScript | Kotlin and Jetpack Compose with Material 3 |
| Form inspection | `chrome.scripting` injects a local DOM scanner into the active tab | Android `AccessibilityService` reads accessible controls in the active window |
| Voice input/output | Browser `SpeechRecognition` / `webkitSpeechRecognition` and `speechSynthesis` | Android `SpeechRecognizer` and `TextToSpeech` |
| Wake phrase | Start the conversation from the extension side panel | Foreground microphone service listens for **“Hey RST”** and opens the floating assistant |
| Local logic | `formmate-core.js`: field-aware parsing, validation, choice matching, and conversation state | `FormmateEngine.kt`: Kotlin implementation of the form interpretation and conversation logic |
| Profile storage | `chrome.storage.local` in the current Chrome profile | App-private `SharedPreferences` on the device |
| Build / tests | No build step for loading the extension; Node.js is used for the optional JS test command | Gradle Kotlin DSL; Android Gradle Plugin 9.3.3, Kotlin 2.2.10, Gradle wrapper 9.8.2, JDK 21, compile SDK 37 |

The extension requests `activeTab`, `scripting`, `sidePanel`, and `storage`. The Android app also requests microphone, overlay, accessibility-service, foreground microphone service, notification, wake-lock, and Internet capabilities for its operating-system integrations.

## Try the Chrome extension

### 1. Load the unpacked extension

1. Install Chrome 116 or newer and open `chrome://extensions`.
2. Turn on **Developer mode**.
3. Select **Load unpacked** and choose the `formmateAI/` folder containing `manifest.json`. Do not select the `formmateAI/demo/` folder.
4. Leave FORMMATE enabled in the extensions list.

### 2. Create a profile

1. Open the FORMMATE toolbar button.
2. In **Saved profiles**, choose **Create**.
3. Give the profile a name, enter whichever details you want to save, and choose **Save Profile**. The profile becomes active in this Chrome profile.

Profile fields are optional. You can create more than one profile and select the one to use from the extension popup or the side panel.

### 3. Open a demo form

Open one of these local files in Chrome:

- `formmateAI/demo/index.html` — student registration demo.
- `formmateAI/demo/form-1.html` — student registration demo (alternate copy).
- `formmateAI/demo/form-2.html` — mock resident-certificate application.

For a `file://` page, open `chrome://extensions`, select FORMMATE **Details**, and enable **Allow access to file URLs**. Alternatively, serve the repository folder over HTTP and open the demo page from that local address.

### 4. Start the guided conversation

1. With the demo form active in Chrome, click the FORMMATE toolbar button and choose **Open Assistant** (or open its side panel).
2. Allow microphone access if Chrome asks. The side panel scans the current page and shows the detected fields.
3. If a saved profile has matching details, answer the profile-use question or choose **Yes, use this profile** / **No, thanks**.
4. Tap the microphone control or **Start Conversation** and answer the assistant's questions. The assistant fills the active field and advances when it can interpret the answer. You can repeat, go back, go to the next field, or edit a value manually.
5. Review the completed values in the webpage. Click the webpage's own submit control only if you intend to submit. The side-panel **Submit Form** control only reports readiness; it does not submit the webpage.

If the form changes after a step is revealed, use **Rescan Current Webpage** to refresh the detected fields. The demo's submit handler only displays a practice message; it does not transmit the entered form data.

### Optional JavaScript checks

From `formmateAI/`, run:

```bash
npm test
```

This runs the Node.js tests for the shared JavaScript form logic. Node.js is not needed to load or use the extension.

## Try the Android app

### Build and install

1. Install Android Studio with JDK 21 support and Android SDK platform 37.
2. Open the sibling `HeyRST/` folder as the Android Studio project and let Gradle sync.
3. Connect an Android device running Android 9 / API 28 or newer, then select **Run** in Android Studio.

You can also build a debug APK from the repository root with:

```bash
cd HeyRST
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat :app:assembleDebug`. The debug APK is written to `HeyRST/app/build/outputs/apk/debug/app-debug.apk`.

### Configure and use Hey RST

1. Launch **Hey RST**. On first launch, create and save a profile before continuing.
2. In the app's Settings screen, grant microphone access and **Display over other apps** permission. Enable the Hey RST accessibility service in Android's Accessibility settings so it can inspect and interact with the active webpage. Android 13 and newer may also ask for notification permission for the foreground service.
3. Turn on the Hey RST listener. Its ongoing notification indicates that the microphone service is active.
4. Open a demo form in **Chrome on the Android device**. To serve the included files from your computer, run a static server from the `formmateAI/` folder:

   ```bash
   python3 -m http.server 8000
   ```

   Connect the phone and computer to the same network, then open `http://<computer-local-IP>:8000/demo/index.html` on the phone. Replace `<computer-local-IP>` with the computer's local network address. A hosted copy of the static demo can also be used.
5. With the form visible in Chrome, say **“Hey RST”**. The floating assistant scans the active screen, asks whether to use matching saved profile details, and then guides you through remaining fields by voice. Answer naturally; review each value on the webpage before submitting it yourself.
6. Turn off the listener in Hey RST when you are finished.

Chrome's **Developer mode** and **Load unpacked** steps apply only to the desktop extension. The Android app is a separate native application and does not install the extension.

## Demo pages

| File | Contents |
|---|---|
| `formmateAI/demo/index.html` | Student registration form with text fields, date, select menus, radio buttons, and consent checkbox |
| `formmateAI/demo/form-1.html` | Alternate student registration demo with the same kinds of controls |
| `formmateAI/demo/form-2.html` | Mock resident-certificate form with address, personal details, select menus, radio buttons, and consent |

These pages are static practice examples. Their submit handlers prevent network submission and display a local demo message.

## Data, privacy, and permissions

- **No project backend:** Profiles and form processing are handled locally by the extension or Android app; there is no Formmate server or database configured in this repository.
- **Chrome profiles:** Saved in `chrome.storage.local` for that browser profile. They are not encrypted by the extension. The extension code does not upload profile values to a Formmate server. The popup and side panel load the Inter font from Google Fonts, and Chrome's speech recognition provider may process audio outside the device depending on browser and system settings.
- **Android profiles and history:** Profiles use app-private `SharedPreferences`; they are not encrypted by the app. The Android app also keeps local form-history entries, including webpage details and a screenshot thumbnail when available. Treat those entries and profile fields as personal data.
- **Speech services:** Voice recognition and speech synthesis are provided by Chrome or the Android device's speech services, not by a custom model bundled here. Recognition may require a working speech service or network connection and may send audio to its provider. Check the privacy settings of the browser/device speech service you use.
- **Use demo data:** Do not enter passwords, government ID numbers, financial data, or other sensitive real information in the prototype. Review the page before submitting any real form.
- **User-controlled submission:** Neither assistant clicks a form's submit button. Consent and declaration choices should be reviewed carefully by the user.

## Scope and limitations

- The extension scans visible, editable native `input`, `textarea`, `select`, radio, and checkbox controls in the active page. It skips hidden, disabled, read-only, password, file-upload, and button controls.
- The Android app depends on Android's accessibility tree. Some custom widgets, inaccessible pages, or browser-specific controls may not expose enough information to scan or fill reliably.
- Neither implementation can guarantee support for every website. Cross-origin embedded frames, closed shadow roots, custom canvas-based controls, and dynamically loaded form steps can limit detection. Rescan after a page reveals or replaces fields.
- Speech recognition accuracy depends on the selected browser/device speech service, microphone permissions, language settings, accent, and background noise. Use the manual edit and navigation controls when recognition needs correction.
- The Android wake phrase is implemented using the Android speech-recognition service in a foreground microphone service; it is not a dedicated low-power offline wake-word chip.

## Project notes for reviewers

The extension's existing internal documentation identifies it as a practice prototype and says it is not eligible as pre-developed material for **ECCLISIA 4.0**. If today's hackathon is ECCLISIA 4.0, confirm the event's eligibility rules before submitting. See [formmateAI/docs/ARCHITECTURE.md](formmateAI/docs/ARCHITECTURE.md), [formmateAI/docs/PRIVACY.md](formmateAI/docs/PRIVACY.md), and [formmateAI/docs/KNOWN_LIMITATIONS.md](formmateAI/docs/KNOWN_LIMITATIONS.md).

## Further documentation

- [Voice system](formmateAI/docs/VOICE_SYSTEM.md)
- [Privacy and safety](formmateAI/docs/PRIVACY.md)
- [Testing guide](formmateAI/docs/TESTING.md)
