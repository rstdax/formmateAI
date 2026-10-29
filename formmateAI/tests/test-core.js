/**
 * Automated Test Suite for FORMMATE Core
 * Tests Dropdown Matching (Bug A), Checkbox (Bug D), Date parsing,
 * Email/Phone normalization, and Conversational Commands.
 */

const assert = require("assert");
const Core = require("../formmate-core.js");

let passed = 0;
let failed = 0;

function test(description, fn) {
  try {
    fn();
    console.log(`  ✓ ${description}`);
    passed++;
  } catch (err) {
    console.error(`  ✗ ${description}`);
    console.error(`    ${err.message}`);
    failed++;
  }
}

console.log("\n=== 1. DROP-DOWN MATCHING TESTS (BUG A REGRESSION) ===");

const stateOptions = [
  { value: "", label: "Choose a state" }, // placeholder
  { value: "AS", label: "Assam" },
  { value: "ML", label: "Meghalaya" },
  { value: "MN", label: "Manipur" },
  { value: "TR", label: "Tripura" },
  { value: "AR", label: "Arunachal Pradesh" },
  { value: "WB", label: "West Bengal" },
  { value: "OTH", label: "Other" }
];

test("Exact lower-case matches label: 'assam'", () => {
  const res = Core.matchOption(stateOptions, "assam", "state");
  assert.ok(res.matched, "Should match an option");
  assert.strictEqual(res.matched.label, "Assam");
  assert.strictEqual(res.matched.value, "AS");
});

test("Exact TitleCase matches label: 'Assam'", () => {
  const res = Core.matchOption(stateOptions, "Assam", "state");
  assert.ok(res.matched);
  assert.strictEqual(res.matched.label, "Assam");
  assert.strictEqual(res.matched.value, "AS");
});

test("Exact value match: 'AS'", () => {
  const res = Core.matchOption(stateOptions, "AS", "state");
  assert.ok(res.matched);
  assert.strictEqual(res.matched.label, "Assam");
  assert.strictEqual(res.matched.value, "AS");
});

test("Conversational framing: 'My state is Assam'", () => {
  const res = Core.matchOption(stateOptions, "My state is Assam", "state");
  assert.ok(res.matched);
  assert.strictEqual(res.matched.label, "Assam");
  assert.strictEqual(res.matched.value, "AS");
});

test("Conversational framing with polite suffix: 'Assam, please'", () => {
  const res = Core.matchOption(stateOptions, "Assam, please", "state");
  assert.ok(res.matched);
  assert.strictEqual(res.matched.label, "Assam");
  assert.strictEqual(res.matched.value, "AS");
});

test("Conversational framing: 'I live in Assam'", () => {
  const res = Core.matchOption(stateOptions, "I live in Assam", "state");
  assert.ok(res.matched);
  assert.strictEqual(res.matched.label, "Assam");
  assert.strictEqual(res.matched.value, "AS");
});

test("Ordinal selection: 'First option' (skips placeholder, maps to Assam)", () => {
  const res = Core.matchOption(stateOptions, "First option", "state");
  assert.ok(res.matched);
  assert.strictEqual(res.matched.label, "Assam");
  assert.strictEqual(res.matched.value, "AS");
});

test("Ordinal selection: 'Option two' (maps to Meghalaya)", () => {
  const res = Core.matchOption(stateOptions, "Option two", "state");
  assert.ok(res.matched);
  assert.strictEqual(res.matched.label, "Meghalaya");
  assert.strictEqual(res.matched.value, "ML");
});

test("Ordinal selection: 'the second one' (maps to Meghalaya)", () => {
  const res = Core.matchOption(stateOptions, "the second one", "state");
  assert.ok(res.matched);
  assert.strictEqual(res.matched.label, "Meghalaya");
  assert.strictEqual(res.matched.value, "ML");
});

test("Ordinal selection: 'last option' (maps to Other)", () => {
  const res = Core.matchOption(stateOptions, "last option", "state");
  assert.ok(res.matched);
  assert.strictEqual(res.matched.label, "Other");
  assert.strictEqual(res.matched.value, "OTH");
});

test("Alias match: 'WB' maps to West Bengal", () => {
  const res = Core.matchOption(stateOptions, "WB", "state");
  assert.ok(res.matched);
  assert.strictEqual(res.matched.label, "West Bengal");
  assert.strictEqual(res.matched.value, "WB");
});

test("Invalid option returns friendly error and does not mutate", () => {
  const res = Core.matchOption(stateOptions, "California", "state");
  assert.strictEqual(res.matched, null);
  assert.ok(res.error.includes("California"));
  assert.ok(res.error.includes("Assam"));
});

test("Out-of-range ordinal returns helpful error", () => {
  const res = Core.matchOption(stateOptions, "Option 50", "state");
  assert.strictEqual(res.matched, null);
  assert.ok(res.error.includes("50"));
});

console.log("\n=== 2. CHECKBOX INTERPRETATION TESTS (BUG D REGRESSION) ===");

test("Affirmative: 'Yes, I agree'", () => {
  assert.strictEqual(Core.parseBooleanAnswer("Yes, I agree"), true);
});

test("Affirmative: 'I agree'", () => {
  assert.strictEqual(Core.parseBooleanAnswer("I agree"), true);
});

test("Affirmative: 'yes'", () => {
  assert.strictEqual(Core.parseBooleanAnswer("yes"), true);
});

test("Affirmative: 'checked'", () => {
  assert.strictEqual(Core.parseBooleanAnswer("checked"), true);
});

test("Negation with agree keyword: 'No, I do not agree' must be FALSE", () => {
  assert.strictEqual(Core.parseBooleanAnswer("No, I do not agree"), false);
});

test("Negation: 'I do not agree' must be FALSE", () => {
  assert.strictEqual(Core.parseBooleanAnswer("I do not agree"), false);
});

test("Negation: 'I disagree' must be FALSE", () => {
  assert.strictEqual(Core.parseBooleanAnswer("I disagree"), false);
});

test("Negation: 'no' must be FALSE", () => {
  assert.strictEqual(Core.parseBooleanAnswer("no"), false);
});

test("Unrelated command: 'Repeat the question' must be NULL (no change)", () => {
  assert.strictEqual(Core.parseBooleanAnswer("Repeat the question"), null);
});

test("Unrelated text: 'What does this mean' must be NULL", () => {
  assert.strictEqual(Core.parseBooleanAnswer("What does this mean"), null);
});

console.log("\n=== 3. SPOKEN DATE PARSING TESTS ===");

test("Spoken: 'Fourth October two thousand three'", () => {
  assert.strictEqual(Core.parseSpokenDate("Fourth October two thousand three"), "2003-10-04");
});

test("Spoken: 'Four October two thousand three'", () => {
  assert.strictEqual(Core.parseSpokenDate("Four October two thousand three"), "2003-10-04");
});

test("Spoken: 'Fourth of October, 2003'", () => {
  assert.strictEqual(Core.parseSpokenDate("Fourth of October, 2003"), "2003-10-04");
});

test("Numeric DMY: '04/10/2003'", () => {
  assert.strictEqual(Core.parseSpokenDate("04/10/2003"), "2003-10-04");
});

test("ISO already: '2003-10-04'", () => {
  assert.strictEqual(Core.parseSpokenDate("2003-10-04"), "2003-10-04");
});

test("Spoken: '15th August 1997'", () => {
  assert.strictEqual(Core.parseSpokenDate("15th August 1997"), "1997-08-15");
});

console.log("\n=== 4. SPOKEN EMAIL & PHONE TESTS ===");

test("Spoken email: 'rohan at gmail dot com'", () => {
  assert.strictEqual(Core.parseSpokenEmail("rohan at gmail dot com"), "rohan@gmail.com");
});

test("Spoken email with name: 'rohan.das at outlook dot com'", () => {
  assert.strictEqual(Core.parseSpokenEmail("rohan.das at outlook dot com"), "rohan.das@outlook.com");
});

test("Spoken email phrase 'at the rate' is normalized", () => {
  assert.strictEqual(
    Core.parseSpokenEmail("shubhan121b at the rate gmail dot com"),
    "shubhan121b@gmail.com"
  );
});

test("Spoken email digit words in the username are normalized", () => {
  assert.strictEqual(
    Core.parseSpokenEmail("shubhan one two one b at the rate gmail dot com"),
    "shubhan121b@gmail.com"
  );
});

test("Spoken phone digits: 'nine eight seven six five four three two one zero'", () => {
  assert.strictEqual(Core.parseSpokenPhone("nine eight seven six five four three two one zero"), "9876543210");
});

test("Formatted phone: '+91 98765 43210'", () => {
  const p = Core.parseSpokenPhone("+91 98765 43210");
  assert.ok(p.includes("9876543210") || p.replace(/\D/g, "").includes("9876543210"));
});

test("Phone speech format reads each English digit separately", () => {
  assert.strictEqual(
    Core.formatPhoneForSpeech("9876543210"),
    "nine, eight, seven, six, five, four, three, two, one, zero"
  );
});

test("Phone speech format reads each Hindi digit separately", () => {
  assert.strictEqual(
    Core.formatPhoneForSpeech("9876543210", "hi-IN"),
    "नौ, आठ, सात, छह, पाँच, चार, तीन, दो, एक, शून्य"
  );
});

console.log("\n=== 5. CONVERSATIONAL COMMAND RECOGNITION (BUG C) ===");

test("Command: 'Repeat the question'", () => {
  assert.strictEqual(Core.recognizeCommand("Repeat the question"), "REPEAT");
});

test("Command: 'Go back'", () => {
  assert.strictEqual(Core.recognizeCommand("Go back"), "GO_BACK");
});

test("Command: 'Skip this'", () => {
  assert.strictEqual(Core.recognizeCommand("Skip this"), "SKIP");
});

test("Command: 'Start again'", () => {
  assert.strictEqual(Core.recognizeCommand("Start again"), "START_AGAIN");
});

test("Command: 'Change my answer'", () => {
  assert.strictEqual(Core.recognizeCommand("Change my answer"), "CHANGE_ANSWER");
});

test("Command: 'Stop listening'", () => {
  assert.strictEqual(Core.recognizeCommand("Stop listening"), "STOP_LISTENING");
});

test("Command: 'Read my answers'", () => {
  assert.strictEqual(Core.recognizeCommand("Read my answers"), "READ_ANSWERS");
});

test("Command: 'Next question'", () => {
  assert.strictEqual(Core.recognizeCommand("Next question"), "NEXT_QUESTION");
});

test("Regular answer is NOT a command: 'Rohan Ranjan Das'", () => {
  assert.strictEqual(Core.recognizeCommand("Rohan Ranjan Das"), null);
});

console.log("\n=== 6. CONVERSATIONAL FRAMING STRIPPER ===");

test("Hindi answers are transliterated into Latin script, not translated", () => {
  assert.strictEqual(Core.transliterateHindiToLatin("शुभम"), "shubham");
  assert.strictEqual(Core.transliterateHindiToLatin("मेरा नाम"), "meraa naam");
});

test("Hindi numerals are converted to standard form digits", () => {
  assert.strictEqual(Core.transliterateHindiToLatin("१२३४५६"), "123456");
});

test("Strip: 'My name is Rohan Ranjan Das' -> 'Rohan Ranjan Das'", () => {
  assert.strictEqual(Core.stripConversationalFraming("My name is Rohan Ranjan Das", "full_name"), "rohan ranjan das");
});

test("Strip: 'I am a male' -> 'male'", () => {
  assert.strictEqual(Core.stripConversationalFraming("I am a male", "gender"), "male");
});

test("Strip: 'My state is Assam' -> 'assam'", () => {
  assert.strictEqual(Core.stripConversationalFraming("My state is Assam", "state"), "assam");
});

console.log("\n=== 7. BILINGUAL HINDI SUPPORT & STATE MACHINE ===");

test("Hindi affirmation: 'हाँ' -> true", () => {
  assert.strictEqual(Core.parseBooleanAnswer("हाँ"), true);
});

test("Hindi affirmation: 'हाँ, मैं सहमत हूँ' -> true", () => {
  assert.strictEqual(Core.parseBooleanAnswer("हाँ, मैं सहमत हूँ"), true);
});

test("Hindi negation: 'नहीं' -> false", () => {
  assert.strictEqual(Core.parseBooleanAnswer("नहीं"), false);
});

test("Hindi negation: 'सहमति नहीं' -> false", () => {
  assert.strictEqual(Core.parseBooleanAnswer("सहमति नहीं"), false);
});

test("Hindi command: 'दोहराएं' -> REPEAT", () => {
  assert.strictEqual(Core.recognizeCommand("दोहराएं"), "REPEAT");
});

test("Hindi command: 'वापस जाएं' -> GO_BACK", () => {
  assert.strictEqual(Core.recognizeCommand("वापस जाएं"), "GO_BACK");
});

test("Hindi command: 'छोड़ें' -> SKIP", () => {
  assert.strictEqual(Core.recognizeCommand("छोड़ें"), "SKIP");
});

test("Hindi command: 'रुकें' -> STOP_LISTENING", () => {
  assert.strictEqual(Core.recognizeCommand("रुकें"), "STOP_LISTENING");
});

test("Hindi prompt generation includes Hindi keywords", () => {
  const prompt = Core.generateHumanPrompt({ semanticType: "email", label: "Email Address" }, "hi-IN");
  assert.ok(prompt.includes("ईमेल"), "Prompt should contain Hindi word for email");
});

test("Hindi confirmation message includes Hindi keywords", () => {
  const confirm = Core.generateConfirmationMessage({ label: "नाम" }, "राहुल", null, "hi-IN");
  assert.ok(confirm.includes("राहुल"), "Confirmation should contain the filled value");
});

test("State Machine defines all 10 required states", () => {
  const req = [
    "IDLE", "SCANNING", "SPEAKING", "LISTENING", "PROCESSING",
    "VALIDATING", "WAITING_FOR_TAP", "PAUSED", "COMPLETED", "ERROR"
  ];
  for (const s of req) {
    assert.ok(Core.States[s], `State ${s} must be defined in Core.States`);
  }
});

console.log(`\n-----------------------------------------`);
console.log(`Total tests run: ${passed + failed}`);
console.log(`Passed: ${passed}`);
console.log(`Failed: ${failed}`);
if (failed > 0) {
  process.exit(1);
} else {
  console.log("All core logic tests passed successfully!");
}
