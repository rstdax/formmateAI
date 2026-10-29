package com.rst.heyrst

import com.rst.heyrst.engine.FormmateEngine
import com.rst.heyrst.model.OptionItem
import com.rst.heyrst.model.SemanticType
import com.rst.heyrst.model.VoiceCommand
import org.junit.Assert.*
import org.junit.Test

class FormmateEngineTest {

    @Test
    fun testNegationFirstBoolean() {
        // Negation must take absolute precedence over affirmative words
        assertEquals(false, FormmateEngine.parseBooleanAnswer("No, I do not agree"))
        assertEquals(false, FormmateEngine.parseBooleanAnswer("I disagree"))
        assertEquals(false, FormmateEngine.parseBooleanAnswer("nahin"))
        assertEquals(false, FormmateEngine.parseBooleanAnswer("नहीं"))
        assertEquals(false, FormmateEngine.parseBooleanAnswer("Uncheck"))

        // Affirmative
        assertEquals(true, FormmateEngine.parseBooleanAnswer("Yes, I agree"))
        assertEquals(true, FormmateEngine.parseBooleanAnswer("Proceed"))
        assertEquals(true, FormmateEngine.parseBooleanAnswer("हाँ"))
    }

    @Test
    fun testConversationalFraming() {
        assertEquals("rohan ranjan das", FormmateEngine.stripConversationalFraming("My name is Rohan Ranjan Das", SemanticType.FULL_NAME))
        assertEquals("assam", FormmateEngine.stripConversationalFraming("My state is Assam, please", SemanticType.STATE))
        assertEquals("male", FormmateEngine.stripConversationalFraming("I am a male candidate", SemanticType.GENDER))
    }

    @Test
    fun testSpokenDate() {
        assertEquals("2003-10-04", FormmateEngine.parseSpokenDate("fourth october two thousand three"))
        assertEquals("2003-10-04", FormmateEngine.parseSpokenDate("4th oct 2003"))
        assertEquals("2003-10-04", FormmateEngine.parseSpokenDate("04/10/2003"))
        assertEquals("2024-08-15", FormmateEngine.parseSpokenDate("born on fifteenth august twenty twenty four"))
    }

    @Test
    fun testSpokenEmailAndPhone() {
        assertEquals("rohan@gmail.com", FormmateEngine.parseSpokenEmail("rohan at gmail dot com"))
        assertEquals("9987654321", FormmateEngine.parseSpokenPhone("double nine eight seven six five four three two one"))
    }

    @Test
    fun testOptionMatching() {
        val states = listOf(
            OptionItem("assam", "Assam"),
            OptionItem("meghalaya", "Meghalaya"),
            OptionItem("west_bengal", "West Bengal"),
            OptionItem("arunachal", "Arunachal Pradesh")
        )

        // Tier 1: exact label
        val res1 = FormmateEngine.matchOption(states, "Assam", SemanticType.STATE)
        assertEquals("Assam", res1.matched?.label)
        assertEquals(1, res1.tier)

        // Tier 3: framing match
        val res3 = FormmateEngine.matchOption(states, "I live in Meghalaya", SemanticType.STATE)
        assertEquals("Meghalaya", res3.matched?.label)
        assertEquals(3, res3.tier)

        // Tier 4: alias match (WB -> West Bengal)
        val res4 = FormmateEngine.matchOption(states, "wb", SemanticType.STATE)
        assertEquals("West Bengal", res4.matched?.label)
        assertEquals(4, res4.tier)

        // Tier 5: ordinal selection
        val res5 = FormmateEngine.matchOption(states, "second option", SemanticType.STATE)
        assertEquals("Meghalaya", res5.matched?.label)
        assertEquals(5, res5.tier)
    }

    @Test
    fun testCommandRecognition() {
        assertEquals(VoiceCommand.REPEAT, FormmateEngine.recognizeCommand("repeat the question"))
        assertEquals(VoiceCommand.SKIP, FormmateEngine.recognizeCommand("skip this field"))
        assertEquals(VoiceCommand.GO_BACK, FormmateEngine.recognizeCommand("go back"))
        assertEquals(VoiceCommand.STOP_LISTENING, FormmateEngine.recognizeCommand("stop assistant"))
    }

    @Test
    fun testMispronouncedWakeWords() {
        // User-reported mispronounced phrases must all trigger wake word
        assertTrue(RSTWakeWordEngine.isWakePhraseMatch("hair style").first)
        assertTrue(RSTWakeWordEngine.isWakePhraseMatch("hairstyle").first)
        assertTrue(RSTWakeWordEngine.isWakePhraseMatch("hairsty").first)
        assertTrue(RSTWakeWordEngine.isWakePhraseMatch("hair").first)
        assertTrue(RSTWakeWordEngine.isWakePhraseMatch("heiaresti").first)
        assertTrue(RSTWakeWordEngine.isWakePhraseMatch("hey aresti").first)
        assertTrue(RSTWakeWordEngine.isWakePhraseMatch("Hey RST").first)
        assertTrue(RSTWakeWordEngine.isWakePhraseMatch("hey r s t").first)
        assertTrue(RSTWakeWordEngine.isWakePhraseMatch("hey rusty").first)
        assertTrue(RSTWakeWordEngine.isWakePhraseMatch("aresti").first)
    }
}
