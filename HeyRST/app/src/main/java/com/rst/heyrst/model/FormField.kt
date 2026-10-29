package com.rst.heyrst.model

/**
 * Supported kinds of form fields matching Formmate AI
 */
enum class FieldKind {
    INPUT,
    TEXTAREA,
    SELECT,
    RADIO,
    CHECKBOX
}

/**
 * High-level semantic categorization used for prompt generation & normalization
 */
enum class SemanticType {
    FULL_NAME,
    FIRST_NAME,
    LAST_NAME,
    GUARDIAN_NAME,
    EMAIL,
    PHONE,
    DATE_OF_BIRTH,
    DATE,
    AGE,
    PINCODE,
    ADDRESS,
    CITY,
    DISTRICT,
    STATE,
    COUNTRY,
    GENDER,
    OCCUPATION,
    COURSE,
    EDUCATION,
    INSTITUTION,
    CONSENT,
    NUMBER,
    GENERAL
}

/**
 * Representation of a choice option (for select dropdowns and radio buttons)
 */
data class OptionItem(
    val value: String,
    val label: String,
    val disabled: Boolean = false
)

/**
 * In-memory representation of a scanned form control on the active screen
 */
data class FormField(
    val id: String,
    val label: String,
    val kind: FieldKind,
    val semanticType: SemanticType,
    val required: Boolean = false,
    val currentValue: String = "",
    val placeholder: String = "",
    val options: List<OptionItem> = emptyList(),
    val confidence: Float = 0.8f
) {
    val isFilled: Boolean
        get() = currentValue.isNotBlank() && !isPlaceholderValue(currentValue)
}

/**
 * Checks if a string represents an unselected placeholder (e.g. "Select district", "dd/mm/yyyy")
 */
fun isPlaceholderValue(text: String?): Boolean {
    if (text.isNullOrBlank()) return true
    val t = text.trim().lowercase()
    return t.startsWith("select") ||
            t.startsWith("choose") ||
            t == "dd/mm/yyyy" ||
            t == "yyyy-mm-dd" ||
            t == "mm/dd/yyyy" ||
            t == "dd-mm-yyyy" ||
            t == "-- select --" ||
            t == "none" ||
            t == "null"
}

/**
 * 10-state conversation state machine matching Formmate AI specification
 */
enum class ConversationState {
    IDLE,
    SCANNING,
    SPEAKING,
    LISTENING,
    PROCESSING,
    VALIDATING,
    WAITING_FOR_TAP,
    PAUSED,
    COMPLETED,
    ERROR
}

/**
 * Voice commands recognized during form filling
 */
enum class VoiceCommand {
    REPEAT,
    GO_BACK,
    SKIP,
    START_AGAIN,
    CHANGE_ANSWER,
    FILL_FIELD,
    STOP_LISTENING,
    READ_ANSWERS,
    NEXT_QUESTION
}
