FormmateAI’s technology stack, it is:

Platform: Chrome Extension, Manifest V3; minimum Chrome 116 (manifest.json).
Frontend: HTML, CSS, and vanilla JavaScript—no React, Vue, or other UI framework.
Voice: Browser-provided Web Speech APIs for speech recognition and speech synthesis. It does not include a separate speech-to-text model.
Extension APIs: Chrome Side Panel, scripting, active-tab access, and local extension storage.
Form understanding: Local JavaScript parsing and validation in formmate-core.js, with page interaction in content.js.
Backend/database: None configured; profiles use Chrome local storage.
Tests: Node.js tests, run with npm test (package.json).
Fonts: Inter is loaded from Google Fonts in the HTML pages.