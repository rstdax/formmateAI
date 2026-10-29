/**
 * FORMMATE AI — Core Domain Logic & Conversational Engine
 * Practice Prototype (Strictly local, no external AI dependencies)
 *
 * Implements deterministic parsing, normalization, multi-tier option matching,
 * date interpretation, negation-first boolean parsing, conversation states,
 * and natural human dialogue generation.
 */

(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory();
  } else {
    root.FormmateCore = factory();
  }
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  // --- Normalization Helpers ---

  function normalizeText(value) {
    return String(value || "")
      .toLowerCase()
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "") // strip Latin diacritics
      .normalize("NFC")
      .replace(/['’]/g, "") // strip apostrophes (e.g. don't -> dont)
      .replace(/[^\p{L}\p{M}\p{N}@.+/-]+/gu, " ")
      .replace(/\s+/g, " ")
      .trim();
  }

  function cleanPunctuation(value) {
    return String(value || "")
      .replace(/^[\s,.;:!?"'()\[\]{}]+/, "")
      .replace(/[\s,.;:!?"'()\[\]{}]+$/, "")
      .trim();
  }

  // --- Spoken Numbers & Word Mappings ---

  const SMALL_NUMBERS = {
    zero: 0, one: 1, two: 2, three: 3, four: 4, five: 5,
    six: 6, seven: 7, eight: 8, nine: 9, ten: 10,
    eleven: 11, twelve: 12, thirteen: 13, fourteen: 14, fifteen: 15,
    sixteen: 16, seventeen: 17, eighteen: 18, nineteen: 19,
    twenty: 20, thirty: 30, forty: 40, fifty: 50,
    sixty: 60, seventy: 70, eighty: 80, ninety: 90
  };

  const ORDINALS = {
    first: 1, "1st": 1,
    second: 2, "2nd": 2,
    third: 3, "3rd": 3,
    fourth: 4, "4th": 4,
    fifth: 5, "5th": 5,
    sixth: 6, "6th": 6,
    seventh: 7, "7th": 7,
    eighth: 8, "8th": 8,
    ninth: 9, "9th": 9,
    tenth: 10, "10th": 10,
    eleventh: 11, "11th": 11,
    twelfth: 12, "12th": 12,
    thirteenth: 13, "13th": 13,
    fourteenth: 14, "14th": 14,
    fifteenth: 15, "15th": 15,
    sixteenth: 16, "16th": 16,
    seventeenth: 17, "17th": 17,
    eighteenth: 18, "18th": 18,
    nineteenth: 19, "19th": 19,
    twentieth: 20, "20th": 20,
    "twenty first": 21, "twenty-first": 21, "21st": 21,
    "twenty second": 22, "twenty-second": 22, "22nd": 22,
    "twenty third": 23, "twenty-third": 23, "23rd": 23,
    "twenty fourth": 24, "twenty-fourth": 24, "24th": 24,
    "twenty fifth": 25, "twenty-fifth": 25, "25th": 25,
    "twenty sixth": 26, "twenty-sixth": 26, "26th": 26,
    "twenty seventh": 27, "twenty-seventh": 27, "27th": 27,
    "twenty eighth": 28, "twenty-eighth": 28, "28th": 28,
    "twenty ninth": 29, "twenty-ninth": 29, "29th": 29,
    thirtieth: 30, "30th": 30,
    "thirty first": 31, "thirty-first": 31, "31st": 31,
    // Hindi ordinals
    "पहला": 1, "pehla": 1, "pahla": 1,
    "दूसरा": 2, "doosra": 2, "dusra": 2,
    "तीसरा": 3, "teesra": 3, "tisra": 3,
    "चौथा": 4, "chautha": 4,
    "पांचवा": 5, "paanchwa": 5, "panchwa": 5
  };

  const MONTHS = {
    january: 1, jan: 1,
    february: 2, feb: 2,
    march: 3, mar: 3,
    april: 4, apr: 4,
    may: 5,
    june: 6, jun: 6,
    july: 7, jul: 7,
    august: 8, aug: 8,
    september: 9, sep: 9, sept: 9,
    october: 10, oct: 10,
    november: 11, nov: 11,
    december: 12, dec: 12
  };

  // Common Indian state and territory abbreviations & aliases
  const STATE_ALIASES = {
    as: "assam",
    ml: "meghalaya",
    mn: "manipur",
    tr: "tripura",
    ap: "arunachal pradesh",
    ar: "arunachal pradesh",
    wb: "west bengal",
    bengal: "west bengal",
    assam: "assam",
    meghalaya: "meghalaya",
    manipur: "manipur",
    tripura: "tripura",
    arunachal: "arunachal pradesh"
  };

  // --- Conversational Framing Stripper ---

  function stripConversationalFraming(text, semanticType) {
    let cleaned = normalizeText(text);

    // Strip polite openers and closures
    cleaned = cleaned
      .replace(/^(?:please|kindly|could you please|can you|would you|i want to|let us|lets)\s+/i, "")
      .replace(/\s+(?:please|kindly|thank you|thanks|as well)$/i, "")
      .trim();

    // Field-specific conversational prefixes
    const prefixes = [
      // Full Name & Identity
      /^(?:my\s+full\s+name\s+is|my\s+name\s+is|full\s+name\s+is|name\s+is|this\s+is|i\s+am\s+a|i\s+am|myself|it\s+is)\s+/i,
      // Location / State / City
      /^(?:my\s+state\s+is|the\s+state\s+is|state\s+is|my\s+state\s+of\s+residence\s+is|i\s+live\s+in|i\s+reside\s+in|i\s+belong\s+to|i\s+am\s+from|from|state)\s+/i,
      /^(?:my\s+district\s+is|district\s+is|my\s+city\s+is|city\s+is)\s+/i,
      /^(?:my\s+address\s+is|address\s+is|residential\s+address\s+is)\s+/i,
      // Gender
      /^(?:my\s+gender\s+is|gender\s+is|i\s+am\s+a\s+candidate|i\s+am\s+a|i\s+am|im\s+a|im)\s+/i,
      // Date of Birth
      /^(?:my\s+date\s+of\s+birth\s+is|date\s+of\s+birth\s+is|my\s+dob\s+is|dob\s+is|born\s+on|born)\s+/i,
      // Contact
      /^(?:my\s+email\s+is|my\s+email\s+address\s+is|email\s+is|email\s+address\s+is)\s+/i,
      /^(?:my\s+phone\s+number\s+is|phone\s+is|my\s+mobile\s+number\s+is|mobile\s+is|contact\s+is|number\s+is)\s+/i,
      /^(?:my\s+pin\s+code\s+is|pin\s+code\s+is|pin\s+is|postal\s+code\s+is|zip\s+is)\s+/i,
      // Course / Choice
      /^(?:my\s+preferred\s+course\s+is|preferred\s+course\s+is|course\s+is|i\s+want\s+to\s+study|i\s+choose|choose|select|i\s+prefer)\s+/i,
      // Generic choice / option prefix
      /^(?:i\s+choose|choose|select|option|pick|go\s+with|set\s+it\s+to|make\s+it)\s+/i
    ];

    for (const prefix of prefixes) {
      cleaned = cleaned.replace(prefix, "").trim();
    }

    // Strip standalone leading articles (e.g. "a male" -> "male", "the first" -> "first")
    cleaned = cleaned.replace(/^(?:a|an|the)\s+/i, "").trim();

    // Strip common trailing words
    cleaned = cleaned
      .replace(/\s+(?:state|option|please)$/i, "")
      .trim();

    return cleanPunctuation(cleaned);
  }

  // --- Ordinal Selection Parser ---

  function parseOrdinal(rawInput) {
    const text = normalizeText(rawInput);
    if (!text) return null;

    // Check for "last option" or "the last one"
    if (/^(?:the\s+)?last(?:\s+option|\s+one)?$/i.test(text)) {
      return { isLast: true };
    }

    // Direct ordinals: "first", "second", "third", "1st", "2nd"
    if (ORDINALS[text]) {
      return { index: ORDINALS[text] };
    }

    // Patterns: "option 1", "option one", "option first", "choice 2", "number 3"
    const patternMatch = text.match(/^(?:option|choice|number|item)\s+([a-z0-9-]+)$/i);
    if (patternMatch) {
      const token = patternMatch[1];
      if (/^\d+$/.test(token)) {
        const num = parseInt(token, 10);
        if (num > 0) return { index: num };
      }
      if (SMALL_NUMBERS[token]) return { index: SMALL_NUMBERS[token] };
      if (ORDINALS[token]) return { index: ORDINALS[token] };
    }

    // Standalone ordinal token in phrase: "first option", "the second one", "choose option three"
    const ordinalPhrase = text.match(/\b(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|1st|2nd|3rd|4th|5th|6th|7th|8th|9th|10th)\b/i);
    if (ordinalPhrase && (text.includes("option") || text.includes("choice") || text.includes("one") || text.includes("select"))) {
      const ord = ordinalPhrase[1].toLowerCase();
      if (ORDINALS[ord]) return { index: ORDINALS[ord] };
    }

    return null;
  }

  // --- Select / Radio Option Matching (6-Tier Priority Architecture) ---

  /**
   * Matches raw user input against available select/radio options.
   * Priority:
   * 1. Exact normalized label match.
   * 2. Exact normalized value match.
   * 3. Clear match after removing conversational framing ("my state is Assam").
   * 4. Unambiguous option aliases (e.g. "WB" -> "West Bengal").
   * 5. Explicit ordinal selection ("first option", "option two", "last option"), excluding disabled/placeholders.
   * 6. Unique unambiguous keyword/substring match; if multiple candidates, triggers clarification.
   *
   * @param {Array<{value: string, label: string, disabled?: boolean}>} options
   * @param {string} rawInput
   * @param {string} semanticType
   * @returns {{matched: object|null, ambiguous?: boolean, candidates?: Array, error?: string, tier?: number}}
   */
  function matchOption(options, rawInput, semanticType = "general") {
    if (!Array.isArray(options) || options.length === 0) {
      return { matched: null, error: "No options are available for this field." };
    }

    const raw = String(rawInput || "").trim();
    if (!raw) {
      return { matched: null, error: "The provided answer is empty." };
    }

    const normInput = normalizeText(raw);
    const strippedInput = stripConversationalFraming(raw, semanticType);

    // Filter out non-selectable placeholders (e.g. <option value="">Choose a state</option>)
    const selectableOptions = options.filter((o) => {
      const v = String(o.value ?? "").trim();
      const l = String(o.label ?? "").trim().toLowerCase();
      if (o.disabled) return false;
      if (v === "" && (l.startsWith("choose") || l.startsWith("select") || l.startsWith("--") || l === "")) {
        return false;
      }
      return true;
    });

    const activeList = selectableOptions.length > 0 ? selectableOptions : options;

    // Helper: Normalize option label and value
    const prepared = activeList.map((o) => ({
      original: o,
      normLabel: normalizeText(o.label),
      normValue: normalizeText(o.value)
    }));

    // Tier 1: Exact normalized option label
    const tier1 = prepared.find((p) => p.normLabel === normInput);
    if (tier1) return { matched: tier1.original, tier: 1 };

    // Tier 2: Exact normalized option value
    const tier2 = prepared.find((p) => p.normValue === normInput && p.normValue !== "");
    if (tier2) return { matched: tier2.original, tier: 2 };

    // Tier 3: Clear match after removing conversational framing
    if (strippedInput && strippedInput !== normInput) {
      const tier3Label = prepared.find((p) => p.normLabel === strippedInput);
      if (tier3Label) return { matched: tier3Label.original, tier: 3 };

      const tier3Value = prepared.find((p) => p.normValue === strippedInput && p.normValue !== "");
      if (tier3Value) return { matched: tier3Value.original, tier: 3 };
    }

    // Tier 4: Unambiguous Option Aliases
    const targetAlias = STATE_ALIASES[strippedInput] || STATE_ALIASES[normInput];
    if (targetAlias) {
      const tier4 = prepared.find((p) => p.normLabel === targetAlias || p.normValue === targetAlias);
      if (tier4) return { matched: tier4.original, tier: 4 };
    }

    // Tier 5: Explicit Ordinal Selection (e.g., "first option", "option two", "last")
    const ordinal = parseOrdinal(normInput);
    if (ordinal) {
      if (ordinal.isLast) {
        const lastOption = activeList[activeList.length - 1];
        return { matched: lastOption, tier: 5 };
      }
      const targetIndex = ordinal.index - 1; // 1-based to 0-based
      if (targetIndex >= 0 && targetIndex < activeList.length) {
        return { matched: activeList[targetIndex], tier: 5 };
      }
      return {
        matched: null,
        error: `Option ${ordinal.index} is not available. There are ${activeList.length} selectable option${activeList.length === 1 ? "" : "s"}.`
      };
    }

    // Tier 6: Substring / Word-Boundary Match & Ambiguity Detection
    const query = strippedInput || normInput;
    if (query.length >= 3) {
      const candidates = prepared.filter((p) => {
        // Word boundary match or containment
        const labelWords = p.normLabel.split(/\s+/);
        const queryWords = query.split(/\s+/);
        const wordMatch = queryWords.some((qw) => labelWords.includes(qw) && qw.length >= 3);
        const includes = p.normLabel.includes(query) || query.includes(p.normLabel);
        return wordMatch || includes;
      });

      if (candidates.length === 1) {
        return { matched: candidates[0].original, tier: 6 };
      }

      if (candidates.length > 1) {
        return {
          matched: null,
          ambiguous: true,
          candidates: candidates.map((c) => c.original),
          error: `Multiple options match “${raw}”: ${candidates.map((c) => c.original.label).join(", ")}.`
        };
      }
    }

    // No match found
    const displayList = activeList.map((o) => o.label).slice(0, 7).join(", ");
    const more = activeList.length > 7 ? ` and ${activeList.length - 7} more` : "";
    return {
      matched: null,
      error: `I couldn't match “${raw}” to an available option. Available options include: ${displayList}${more}.`
    };
  }

  // --- Checkbox Interpretation (Negation-First Order) ---

  /**
   * Distinguishes affirmative and negative checkbox responses.
   * Negation MUST be checked prior to affirmative keyword matching.
   * e.g., "No, I do not agree" contains "agree" but is strictly negative.
   *
   * @param {string} rawInput
   * @returns {boolean|null} true = checked, false = unchecked, null = unrecognized
   */
  function parseBooleanAnswer(rawInput) {
    const text = normalizeText(rawInput);
    if (!text) return null;

    // 1. Explicit Negation Patterns (Priority)
    const negativeRegex = /(?:\b(no|nope|not|do\s+not\s+agree|dont\s+agree|disagree|disagreed|reject|declined?|refuse|never|nah|unchecked|uncheck|nahin|nahi|naxoy|na|mat)\b|(?:नहीं|ना|असहमत|अस्वीकार|सहमति\s+नहीं))/iu;
    if (negativeRegex.test(text)) {
      return false;
    }

    // 2. Affirmative Patterns
    const affirmativeRegex = /(?:\b(yes|yeah|yep|agree|agreed|accept|accepted|checked|check|confirm|confirmed|true|haan|correct|sure|proceed|ok|okay|fine|theek|thik|sahi)\b|(?:हाँ|हाँजी|हाँ\s*जी|सहमत|स्वीकार|सही|ठीक|अवश्य))/iu;
    if (affirmativeRegex.test(text)) {
      return true;
    }

    return null;
  }

  // --- Spoken Date Normalizer ---

  /**
   * Parses spoken and numeric dates to standardized ISO "YYYY-MM-DD" format.
   * Examples:
   * - "fourth october two thousand three" -> "2003-10-04"
   * - "4th oct 2003" -> "2003-10-04"
   * - "04/10/2003" -> "2003-10-04" (DD/MM/YYYY)
   * - "2003-10-04" -> "2003-10-04"
   *
   * @param {string} rawInput
   * @returns {string|null} ISO date string or null if unparseable
   */
  function parseSpokenDate(rawInput) {
    const raw = String(rawInput || "").trim();
    if (!raw) return null;

    // Standard ISO already: YYYY-MM-DD
    if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
      const [y, m, d] = raw.split("-").map(Number);
      if (m >= 1 && m <= 12 && d >= 1 && d <= 31) return raw;
    }

    // Explicit DD/MM/YYYY or DD-MM-YYYY or DD.MM.YYYY
    const dmy = raw.match(/^(\d{1,2})[\/.-](\d{1,2})[\/.-](\d{4})$/);
    if (dmy) {
      const day = Number(dmy[1]);
      const month = Number(dmy[2]);
      const year = Number(dmy[3]);
      if (day >= 1 && day <= 31 && month >= 1 && month <= 12 && year >= 1900 && year <= 2100) {
        return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
      }
    }

    let text = normalizeText(raw);

    // Strip common date prefixes: "born on", "my date of birth is", "date is"
    text = text.replace(/^(?:born\s+on|born|my\s+date\s+of\s+birth\s+is|date\s+of\s+birth\s+is|dob\s+is|date\s+is)\s+/i, "");
    text = text.replace(/^(?:the\s+)?/i, "").replace(/\s+(?:please)$/i, "");

    // Convert spoken year words:
    // "two thousand three" -> 2003, "twenty twenty four" -> 2024, "nineteen ninety eight" -> 1998
    let year = null;
    const yearMatch = text.match(/\b(19\d{2}|20\d{2})\b/);
    if (yearMatch) {
      year = Number(yearMatch[1]);
      text = text.replace(yearMatch[0], " ").trim();
    } else if (text.includes("two thousand and") || text.includes("two thousand")) {
      const sub = text.replace("two thousand and", "two thousand").split("two thousand")[1]?.trim();
      if (sub && SMALL_NUMBERS[sub] !== undefined) {
        year = 2000 + SMALL_NUMBERS[sub];
        text = text.replace(/two\s+thousand(?:\s+and)?\s+[a-z]+/, " ").trim();
      } else if (!sub || sub === "") {
        year = 2000;
        text = text.replace(/two\s+thousand/, " ").trim();
      }
    } else {
      // Look for compound spoken years like "nineteen ninety eight" or "twenty twenty three"
      const compoundYear = text.match(/\b(nineteen|twenty)\s+([a-z]+(?:\s+[a-z]+)?)\b/);
      if (compoundYear) {
        const century = compoundYear[1] === "nineteen" ? 1900 : 2000;
        const remainderWords = compoundYear[2].split(/\s+/);
        let remVal = 0;
        for (const w of remainderWords) {
          if (SMALL_NUMBERS[w] !== undefined) remVal += SMALL_NUMBERS[w];
        }
        if (remVal > 0 && remVal < 100) {
          year = century + remVal;
          text = text.replace(compoundYear[0], " ").trim();
        }
      }
    }

    // Find Month
    let month = null;
    for (const [mName, mNum] of Object.entries(MONTHS)) {
      const regex = new RegExp(`\\b${mName}\\b`, "i");
      if (regex.test(text)) {
        month = mNum;
        text = text.replace(regex, " ").trim();
        break;
      }
    }

    // Find Day (could be ordinal or digits)
    let day = null;
    // Check digits first: "4", "04", "4th"
    const digitDay = text.match(/\b([0-3]?\d)(?:st|nd|rd|th)?\b/);
    if (digitDay) {
      const val = Number(digitDay[1]);
      if (val >= 1 && val <= 31) {
        day = val;
        text = text.replace(digitDay[0], " ").trim();
      }
    }

    if (!day) {
      // Check written ordinals: "fourth", "twenty fourth", "thirty first"
      for (const [ordName, ordNum] of Object.entries(ORDINALS)) {
        const regex = new RegExp(`\\b${ordName}\\b`, "i");
        if (regex.test(text)) {
          day = ordNum;
          break;
        }
      }
    }

    if (!day) {
      // Check cardinal numbers: "four", "twenty four", "one"
      // Handle compound like "twenty four" first
      const compound = text.match(/\b(twenty|thirty)\s+([a-z]+)\b/);
      if (compound && SMALL_NUMBERS[compound[1]] && SMALL_NUMBERS[compound[2]]) {
        const val = SMALL_NUMBERS[compound[1]] + SMALL_NUMBERS[compound[2]];
        if (val >= 1 && val <= 31) day = val;
      }
      if (!day) {
        for (const [numName, numVal] of Object.entries(SMALL_NUMBERS)) {
          if (numVal >= 1 && numVal <= 31) {
            const regex = new RegExp(`\\b${numName}\\b`, "i");
            if (regex.test(text)) {
              day = numVal;
              break;
            }
          }
        }
      }
    }

    // If day, month, and year were resolved
    if (day && month && year) {
      return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
    }

    // Fallback: Date.parse
    const ts = Date.parse(raw);
    if (!Number.isNaN(ts)) {
      const d = new Date(ts);
      const y = d.getFullYear();
      const m = d.getMonth() + 1;
      const dayNum = d.getDate();
      if (y >= 1900 && y <= 2100) {
        return `${y}-${String(m).padStart(2, "0")}-${String(dayNum).padStart(2, "0")}`;
      }
    }

    return null;
  }

  // --- Spoken Email Normalizer ---

  function parseSpokenEmail(rawInput) {
    let text = String(rawInput || "").trim();
    if (!text) return null;

    // Convert spoken tokens: "rohan at gmail dot com" -> "rohan@gmail.com"
    let converted = text
      .toLowerCase()
      .replace(/\s+at\s+/g, "@")
      .replace(/\s+dot\s+/g, ".")
      .replace(/\s+underscore\s+/g, "_")
      .replace(/\s+dash\s+/g, "-")
      .replace(/\s+/g, "");

    const match = converted.match(/[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i);
    if (match) {
      return match[0].replace(/[.,;]+$/, "");
    }
    return null;
  }

  // --- Spoken Phone Normalizer ---

  function parseSpokenPhone(rawInput) {
    let text = normalizeText(rawInput);
    if (!text) return null;

    // Convert spoken digit words
    const digitWords = {
      zero: "0", oh: "0", one: "1", two: "2", three: "3", four: "4",
      five: "5", six: "6", seven: "7", eight: "8", nine: "9"
    };

    // Handle "double" / "triple": "double nine" -> "99"
    text = text.replace(/\bdouble\s+([a-z0-9])/gi, (m, d) => `${d} ${d}`);
    text = text.replace(/\btriple\s+([a-z0-9])/gi, (m, d) => `${d} ${d} ${d}`);

    const tokens = text.split(/\s+/);
    let assembled = "";
    for (const t of tokens) {
      if (digitWords[t] !== undefined) {
        assembled += digitWords[t];
      } else if (/^\d+$/.test(t)) {
        assembled += t;
      }
    }

    if (assembled.length >= 7 && assembled.length <= 15) {
      return assembled;
    }

    // Try standard regex match on original
    const rawMatch = String(rawInput || "").match(/(?:\+?\d[\d\s().-]{6,}\d)/);
    if (rawMatch) {
      const digits = rawMatch[0].replace(/\D/g, "");
      if (digits.length >= 7 && digits.length <= 15) {
        return rawMatch[0].trim();
      }
    }

    return null;
  }

  // --- Conversational Commands Recognition ---

  const COMMANDS = {
    REPEAT: [
      "repeat", "repeat the question", "repeat question", "say again", "what was the question", "what is the question", "say that again", "pardon",
      "दोहराएं", "फिर से बोलें", "दोहराओ", "फिर से कहो", "क्या था", "dohrayen", "phir se"
    ],
    GO_BACK: [
      "go back", "previous", "previous question", "back", "backtrack", "return to previous",
      "वापस जाएं", "पीछे जाएं", "पिछला प्रश्न", "पीछे", "wapas", "pichhe"
    ],
    SKIP: [
      "skip", "skip this", "skip this field", "skip this question", "leave blank", "leave empty", "pass", "skip it",
      "छोड़ें", "छोड़ दो", "आगे बढ़ें", "खाली रखें", "chhoden", "aage badho"
    ],
    START_AGAIN: [
      "start again", "restart", "start over", "begin again", "reset conversation",
      "फिर से शुरू करें", "शुरू से", "दोबारा शुरू करें", "shuru se"
    ],
    CHANGE_ANSWER: [
      "change my answer", "change answer", "edit this", "edit answer", "re answer", "re-answer",
      "उत्तर बदलें", "बदलो", "संपादित करें", "badlen"
    ],
    FILL_FIELD: [
      "fill this field", "fill field", "select this field",
      "यह भरें", "यह फ़ील्ड भरें"
    ],
    STOP_LISTENING: [
      "stop listening", "stop", "pause", "cancel", "halt", "stop assistant",
      "रुकें", "रोकें", "बंद करें", "ruko", "pause karo", "rok do"
    ],
    READ_ANSWERS: [
      "read my answers", "review my answers", "review", "show summary", "check answers", "open review",
      "समीक्षा करें", "उत्तर देखें", "समीक्षा", "review karo"
    ],
    NEXT_QUESTION: [
      "next question", "next", "continue", "move on", "proceed",
      "अगला प्रश्न", "आगे", "आगे चलें", "agla"
    ]
  };

  /**
   * Identifies whether the given utterance is a control command.
   * @param {string} rawInput
   * @returns {string|null} Command name or null
   */
  function recognizeCommand(rawInput) {
    const text = normalizeText(rawInput);
    if (!text) return null;

    for (const [cmd, phrases] of Object.entries(COMMANDS)) {
      for (const phrase of phrases) {
        const normPhrase = normalizeText(phrase);
        if (text === normPhrase || text === `assistant ${normPhrase}` || text === `please ${normPhrase}`) {
          return cmd;
        }
      }
    }

    return null;
  }

  // --- Conversation State Machine ---

  const States = {
    IDLE: "IDLE",
    SCANNING: "SCANNING",
    SPEAKING: "SPEAKING",
    LISTENING: "LISTENING",
    PROCESSING: "PROCESSING",
    VALIDATING: "VALIDATING",
    WAITING_FOR_TAP: "WAITING_FOR_TAP",
    PAUSED: "PAUSED",
    COMPLETED: "COMPLETED",
    ERROR: "ERROR",
    // Compatibility aliases
    ASKING: "SPEAKING",
    CONFIRMING: "SPEAKING",
    ERROR_RECOVERY: "ERROR",
    REVIEW: "WAITING_FOR_TAP"
  };

  class ConversationStateMachine {
    constructor(onStateChange) {
      this.state = States.IDLE;
      this.onStateChange = onStateChange || (() => {});
    }

    transition(newState, meta = {}) {
      const oldState = this.state;
      this.state = newState;
      this.onStateChange(newState, oldState, meta);
      return this.state;
    }

    is(stateName) {
      return this.state === stateName;
    }

    canAcceptInput() {
      return (
        this.state === States.SPEAKING ||
        this.state === States.LISTENING ||
        this.state === States.WAITING_FOR_TAP ||
        this.state === States.ERROR ||
        this.state === States.IDLE ||
        this.state === States.READY
      );
    }
  }

  // --- Natural Dialogue Generation ---

  function generateHumanPrompt(field, lang = "en-IN") {
    const isHindi = String(lang).startsWith("hi");
    const label = field.label || (isHindi ? "यह फ़ील्ड" : "this field");
    const sem = field.semanticType || "general";
    const requiredNote = field.required
      ? (isHindi ? " (यह फ़ील्ड अनिवार्य है)" : " (This field is required)")
      : "";

    if (isHindi) {
      switch (sem) {
        case "full_name":
          return `आपका पूरा नाम क्या है?${requiredNote}`;
        case "first_name":
          return `आपका पहला नाम क्या है?${requiredNote}`;
        case "last_name":
          return `आपका उपनाम या पारिवारिक नाम क्या है?${requiredNote}`;
        case "email":
          return `आपका ईमेल पता क्या है?${requiredNote}`;
        case "phone":
          return `आपका 10 अंकों का मोबाइल नंबर क्या है?${requiredNote}`;
        case "date_of_birth":
          return `आपकी जन्म तिथि क्या है? आप सामान्य रूप से बोल सकते हैं, जैसे 4 अक्टूबर 2003।${requiredNote}`;
        case "date":
          return `“${label}” के लिए कौन सी तारीख दर्ज की जानी चाहिए?${requiredNote}`;
        case "district":
        case "city":
          return `आप किस जिले या शहर में रहते हैं?${requiredNote}`;
        case "state":
          if (field.options && field.options.length > 0) {
            const list = field.options.slice(0, 7).map((o) => o.label).join(", ");
            return `आप किस राज्य में रहते हैं? विकल्प हैं: ${list}। आप नाम या विकल्प संख्या कह सकते हैं।${requiredNote}`;
          }
          return `आप किस राज्य में रहते हैं?${requiredNote}`;
        case "gender":
          if (field.options && field.options.length > 0) {
            const list = field.options.map((o) => o.label).join(", ");
            return `लिंग के लिए, आप ${list} कह सकते हैं।${requiredNote}`;
          }
          return `आपका लिंग क्या है?${requiredNote}`;
        case "pincode":
          return `आपका 6 अंकों का पिन कोड क्या है?${requiredNote}`;
        case "address":
          return `कृपया अपना आवासीय पता या इलाका बताएं।${requiredNote}`;
        case "consent":
          return `घोषणा: “${label}” के लिए, कृपया “हाँ, मैं सहमत हूँ” या “नहीं” कहें।${requiredNote}`;
        default:
          if (field.kind === "select" || field.kind === "radio") {
            const opts = (field.options || []).slice(0, 6).map((o) => o.label).join(", ");
            return `“${label}” के लिए विकल्प हैं: ${opts}।${requiredNote}`;
          }
          return `कृपया “${label}” के लिए उत्तर दें।${requiredNote}`;
      }
    }

    // Default: English (India)
    switch (sem) {
      case "full_name":
        return `What is your full name?${requiredNote}`;
      case "first_name":
        return `What is your first name?${requiredNote}`;
      case "last_name":
        return `What is your surname or family name?${requiredNote}`;
      case "email":
        return `What is your email address?${requiredNote}`;
      case "phone":
        return `What is your mobile or phone number?${requiredNote}`;
      case "date_of_birth":
        return `What is your date of birth? You can speak it naturally, such as 4th of October 2003.${requiredNote}`;
      case "date":
        return `What date should be entered for “${label}”?${requiredNote}`;
      case "district":
      case "city":
        return `Which district or city do you live in?${requiredNote}`;
      case "state":
        if (field.options && field.options.length > 0) {
          const list = field.options.slice(0, 7).map((o) => o.label).join(", ");
          return `Which state do you live in? Options include ${list}. You can say the name or the option number.${requiredNote}`;
        }
        return `Which state do you live in?${requiredNote}`;
      case "gender":
        if (field.options && field.options.length > 0) {
          const list = field.options.map((o) => o.label).join(", ");
          return `For gender, you can say ${list}.${requiredNote}`;
        }
        return `What is your gender?${requiredNote}`;
      case "pincode":
        return `What is your 6-digit PIN code?${requiredNote}`;
      case "address":
        return `Please tell me your residential address or locality.${requiredNote}`;
      case "consent":
        return `For the declaration: “${label}”, please say “Yes, I agree” or “No, I do not agree”.${requiredNote}`;
      default:
        if (field.kind === "select" || field.kind === "radio") {
          const opts = (field.options || []).slice(0, 6).map((o) => o.label).join(", ");
          return `For “${label}”, available options are: ${opts}.${requiredNote}`;
        }
        return `Please provide the answer for “${label}”.${requiredNote}`;
    }
  }

  function generateConfirmationMessage(field, value, nextField = null, lang = "en-IN") {
    const isHindi = String(lang).startsWith("hi");
    const label = field.label || (isHindi ? "फ़ील्ड" : "the field");

    if (isHindi) {
      let base = `बहुत बढ़िया, मैंने “${label}” को “${value}” दर्ज कर दिया है।`;
      if (field.kind === "checkbox") {
        base = value.includes("Yes") || value.includes("agree") || value.includes("हाँ") || value.includes("सहमत")
          ? `मैंने सहमति बॉक्स पर टिक कर दिया है।`
          : `मैंने आपका उत्तर दर्ज कर लिया है और बॉक्स खाली रखा है।`;
      }
      if (nextField) {
        return `${base} आगे, ${nextField.label} भरते हैं।`;
      }
      return base;
    }

    let base = `Perfect, I've updated “${label}” to “${value}”.`;
    if (field.kind === "checkbox") {
      base = value.includes("Yes") || value.includes("agree") || value.includes("Confirmed")
        ? `I have checked the consent box for you.`
        : `I've recorded your answer and left the box unchecked.`;
    }

    if (nextField) {
      return `${base} Next, what’s your ${nextField.label.toLowerCase()}?`;
    }
    return base;
  }

  function generateClarificationMessage(field, rawInput, candidates, lang = "en-IN") {
    const isHindi = String(lang).startsWith("hi");
    const candidateList = candidates.map((c) => c.label).join(isHindi ? " या " : " or ");
    if (isHindi) {
      return `मैंने “${rawInput}” सुना, लेकिन सुनिश्चित करने के लिए: क्या आपका मतलब ${candidateList} था?`;
    }
    return `I heard “${rawInput}”, but I want to be certain. Did you mean ${candidateList}?`;
  }

  // --- Exports ---

  return {
    normalizeText,
    cleanPunctuation,
    stripConversationalFraming,
    parseOrdinal,
    matchOption,
    parseBooleanAnswer,
    parseSpokenDate,
    parseSpokenEmail,
    parseSpokenPhone,
    recognizeCommand,
    ConversationStateMachine,
    States,
    generateHumanPrompt,
    generateConfirmationMessage,
    generateClarificationMessage,
    COMMANDS,
    STATE_ALIASES
  };
});
