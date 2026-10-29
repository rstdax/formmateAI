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

        // Rebranded Wake Words
        assertTrue(RSTWakeWordEngine.isWakePhraseMatch("formate ai").first)
        assertTrue(RSTWakeWordEngine.isWakePhraseMatch("formate").first)
        assertTrue(RSTWakeWordEngine.isWakePhraseMatch("hey formate").first)
        assertTrue(RSTWakeWordEngine.isWakePhraseMatch("format ai").first)
    }

    @Test
    fun testUserProfile20Fields() {
        val p = com.rst.heyrst.model.UserProfile(
            profileName = "Personal",
            fullName = "Rohan Ranjan Das",
            firstName = "Rohan",
            lastName = "Das",
            email = "rohan@gmail.com",
            phone = "9876543210",
            dateOfBirth = "04/10/2003",
            gender = "Male",
            guardianName = "Guardian Name",
            streetAddress = "MG Road",
            city = "Guwahati",
            district = "Kamrup",
            state = "Assam",
            country = "India",
            pincode = "781001",
            age = "21",
            occupation = "Student",
            course = "BTech CSE",
            education = "Undergraduate",
            institution = "Gauhati University"
        )
        val json = p.toJson()
        val restored = com.rst.heyrst.model.UserProfile.fromJson(json)
        assertEquals("Personal", restored.profileName)
        assertEquals("Rohan Ranjan Das", restored.fullName)
        assertEquals("04/10/2003", restored.dateOfBirth)
        assertEquals("Assam", restored.state)
        assertEquals("781001", restored.pincode)
        assertEquals("BTech CSE", restored.course)
    }

    @Test
    fun testFormHistoryItemSerialization() {
        val item = com.rst.heyrst.model.FormHistoryItem(
            id = "test-1",
            title = "Indian Passport Application",
            url = "https://passportindia.gov.in",
            dateTime = "Tue, 23 Apr, 02:30 PM",
            timeBadge = "23 Apr",
            status = "Completed",
            progressPercent = 100,
            thumbnailPath = "/sdcard/Pictures/FormateAI/thumb_1.jpg",
            filledFieldsCount = 14,
            totalFieldsCount = 14
        )
        val json = item.toJson()
        val restored = com.rst.heyrst.model.FormHistoryItem.fromJson(json)
        assertEquals("Indian Passport Application", restored.title)
        assertEquals("https://passportindia.gov.in", restored.url)
        assertEquals("Completed", restored.status)
        assertEquals(100, restored.progressPercent)
        assertEquals(14, restored.filledFieldsCount)
        assertEquals(14, restored.totalFieldsCount)
        assertEquals("/sdcard/Pictures/FormateAI/thumb_1.jpg", restored.thumbnailPath)
    }

    @Test
    fun testProfileBatchMatchingAndRemainingFields() {
        val profile = com.rst.heyrst.model.UserProfile(
            profileName = "Personal",
            fullName = "Rohan Ranjan Das",
            email = "rohan@gmail.com",
            phone = "9876543210",
            dateOfBirth = "04/10/2003",
            gender = "Male",
            streetAddress = "House 12, MG Road",
            district = "Kamrup",
            state = "Assam",
            pincode = "781001",
            course = "" // Empty course
        )

        val scannedFields = listOf(
            com.rst.heyrst.model.FormField("f1", "Full Name", com.rst.heyrst.model.FieldKind.INPUT, SemanticType.FULL_NAME),
            com.rst.heyrst.model.FormField("f2", "Email Address", com.rst.heyrst.model.FieldKind.INPUT, SemanticType.EMAIL),
            com.rst.heyrst.model.FormField("f3", "Mobile Number", com.rst.heyrst.model.FieldKind.INPUT, SemanticType.PHONE),
            com.rst.heyrst.model.FormField("f4", "Date of Birth", com.rst.heyrst.model.FieldKind.INPUT, SemanticType.DATE_OF_BIRTH),
            com.rst.heyrst.model.FormField("f5", "District", com.rst.heyrst.model.FieldKind.INPUT, SemanticType.DISTRICT),
            com.rst.heyrst.model.FormField("f6", "State", com.rst.heyrst.model.FieldKind.SELECT, SemanticType.STATE),
            com.rst.heyrst.model.FormField("f7", "Residential Address", com.rst.heyrst.model.FieldKind.TEXTAREA, SemanticType.ADDRESS),
            com.rst.heyrst.model.FormField("f8", "PIN Code", com.rst.heyrst.model.FieldKind.INPUT, SemanticType.PINCODE),
            com.rst.heyrst.model.FormField("f9", "Preferred Course", com.rst.heyrst.model.FieldKind.SELECT, SemanticType.COURSE),
            com.rst.heyrst.model.FormField("f10", "I confirm accuracy", com.rst.heyrst.model.FieldKind.CHECKBOX, SemanticType.CONSENT)
        )

        val matches = com.rst.heyrst.model.ProfileStorage.getMatchingFields(profile, scannedFields)

        // 8 matching fields must be identified for batch autofill
        assertEquals(8, matches.size)
        assertTrue(matches.any { it.first.label == "Full Name" && it.second == "Rohan Ranjan Das" })
        assertTrue(matches.any { it.first.label == "Email Address" && it.second == "rohan@gmail.com" })
        assertTrue(matches.any { it.first.label == "Date of Birth" && it.second == "04/10/2003" })
        assertTrue(matches.any { it.first.label == "State" && it.second == "Assam" })
        assertTrue(matches.any { it.first.label == "PIN Code" && it.second == "781001" })

        // Checkbox and Preferred Course (empty in profile) must NOT be in batch autofill
        assertFalse(matches.any { it.first.id == "f9" })
        assertFalse(matches.any { it.first.id == "f10" })

        // Simulate batch autofill
        val afterAutofill = scannedFields.map { f ->
            val matchVal = matches.find { it.first.id == f.id }?.second
            if (matchVal != null) f.copy(currentValue = matchVal) else f
        }

        // Remaining unfilled fields to be asked one by one
        val remaining = afterAutofill.filter { !it.isFilled }
        assertEquals(2, remaining.size)
        assertEquals("Preferred Course", remaining[0].label)
        assertEquals("I confirm accuracy", remaining[1].label)
    }

    @Test
    fun testPlaceholderValueAndFieldFilledState() {
        assertTrue(com.rst.heyrst.model.isPlaceholderValue("Select district"))
        assertTrue(com.rst.heyrst.model.isPlaceholderValue("Select state"))
        assertTrue(com.rst.heyrst.model.isPlaceholderValue("dd/mm/yyyy"))
        assertTrue(com.rst.heyrst.model.isPlaceholderValue("yyyy-mm-dd"))
        assertTrue(com.rst.heyrst.model.isPlaceholderValue("Choose an option"))
        assertTrue(com.rst.heyrst.model.isPlaceholderValue(""))
        assertTrue(com.rst.heyrst.model.isPlaceholderValue(null))

        assertFalse(com.rst.heyrst.model.isPlaceholderValue("Kamrup"))
        assertFalse(com.rst.heyrst.model.isPlaceholderValue("04/10/2003"))
        assertFalse(com.rst.heyrst.model.isPlaceholderValue("Rohan Ranjan Das"))

        val fieldWithPlaceholder = com.rst.heyrst.model.FormField(
            id = "f_test",
            label = "District",
            kind = com.rst.heyrst.model.FieldKind.SELECT,
            semanticType = SemanticType.DISTRICT,
            currentValue = "Select district"
        )
        assertFalse(fieldWithPlaceholder.isFilled)

        val fieldFilled = fieldWithPlaceholder.copy(currentValue = "Kamrup")
        assertTrue(fieldFilled.isFilled)
    }
}
