package com.rst.heyrst.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.rst.heyrst.engine.FormmateEngine
import com.rst.heyrst.model.FieldKind
import com.rst.heyrst.model.FormField
import com.rst.heyrst.model.OptionItem
import com.rst.heyrst.model.SemanticType
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Android Accessibility Service for Hey RST / Formmate.
 *
 * Capabilities:
 * 1. Strictly filters out browser UI (Omnibox, search bars, URL bars, toolbar, tabs).
 * 2. Scans web content for:
 *    - Text inputs & textareas
 *    - Date of Birth & date pickers (<input type="date">)
 *    - Dropdown menus (<select>, comboboxes, pop-up buttons, spinners)
 *    - Radio button groups (grouped under unified category with individual option dispatch)
 *    - Checkboxes / Agree tick boxes (resolving preceding/following labels & clicking wrappers)
 * 3. Takes real-time screen thumbnails on form activation for bento grid history (API 30+).
 */
class RSTAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: RSTAccessibilityService? = null
            private set

        fun isServiceRunning(): Boolean = instance != null

        // In-memory registry mapping runtime field IDs (fm-field-1) to live AccessibilityNodeInfo objects
        private val nodeRegistry = ConcurrentHashMap<String, AccessibilityNodeInfo>()

        // Option-level registry mapping "fieldId:optionValue" to the specific child/radio node
        private val optionNodeRegistry = ConcurrentHashMap<String, AccessibilityNodeInfo>()

        private val mainHandler = Handler(Looper.getMainLooper())

        /**
         * Takes a screenshot of the active screen and saves it as a thumbnail image.
         */
        fun captureScreenThumbnail(context: Context, onComplete: (String?) -> Unit) {
            val service = instance
            if (service == null) {
                onComplete(null)
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    service.takeScreenshot(
                        Display.DEFAULT_DISPLAY,
                        context.mainExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(screenshotResult: ScreenshotResult) {
                                try {
                                    val hardwareBuffer = screenshotResult.hardwareBuffer
                                    val colorSpace = screenshotResult.colorSpace
                                    val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                    hardwareBuffer.close()

                                    if (bitmap != null) {
                                        val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                                        val thumbDir = File(context.filesDir, "thumbnails").apply { mkdirs() }
                                        val file = File(thumbDir, "thumb_${System.currentTimeMillis()}.jpg")
                                        FileOutputStream(file).use { out ->
                                            softwareBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                        }
                                        onComplete(file.absolutePath)
                                        return
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                onComplete(null)
                            }

                            override fun onFailure(errorCode: Int) {
                                onComplete(null)
                            }
                        }
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    onComplete(null)
                }
            } else {
                onComplete(null)
            }
        }

        /**
         * Checks if an accessibility node belongs to Chrome's browser chrome
         * (Omnibox, URL bar, search box, top toolbar, tab switcher, menu button).
         */
        private fun isBrowserChromeElement(node: AccessibilityNodeInfo): Boolean {
            val resId = node.viewIdResourceName?.lowercase() ?: ""
            if (resId.contains("url_bar") ||
                resId.contains("omnibox") ||
                resId.contains("toolbar") ||
                resId.contains("search_box") ||
                resId.contains("location_bar") ||
                resId.contains("tab_switcher") ||
                resId.contains("menu_button") ||
                resId.contains("custom_tab_toolbar") ||
                resId.contains("home_button") ||
                resId.contains("action_bar") ||
                resId.contains("lineartab")
            ) {
                return true
            }

            val text = (node.text?.toString() ?: "").lowercase().trim()
            val hint = (node.hintText?.toString() ?: "").lowercase().trim()
            val cd = (node.contentDescription?.toString() ?: "").lowercase().trim()

            val browserPhrases = listOf(
                "search google or type url",
                "search or type url",
                "search or type web address",
                "type a url",
                "search web",
                "address and search bar",
                "new tab",
                "switch or close tabs",
                "more options",
                "open tabs"
            )

            for (phrase in browserPhrases) {
                if (text == phrase || hint == phrase || cd == phrase ||
                    text.contains(phrase) || hint.contains(phrase) || cd.contains(phrase)
                ) {
                    return true
                }
            }

            return false
        }

        /**
         * Locates the web content container inside Chrome or custom WebView.
         */
        private fun findWebContentContainer(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            fun search(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
                if (depth > 10) return null
                val cls = node.className?.toString() ?: ""
                val role = AccessibilityNodeInfoCompat.wrap(node).roleDescription?.toString() ?: ""

                if (cls.contains("WebView", ignoreCase = true) ||
                    cls.contains("ContentView", ignoreCase = true) ||
                    role.equals("web", ignoreCase = true) ||
                    role.equals("document", ignoreCase = true)
                ) {
                    return node
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    val found = search(child, depth + 1)
                    if (found != null) return found
                }
                return null
            }
            return search(root, 0)
        }

        /**
         * Scans the currently active window (Chrome browser or app) and returns structured form fields.
         */
        fun scanActiveWindow(): List<FormField> {
            val service = instance ?: return emptyList()
            val root = service.rootInActiveWindow ?: return emptyList()

            nodeRegistry.clear()
            optionNodeRegistry.clear()

            val fields = mutableListOf<FormField>()
            var nextId = 1

            // Intermediate structures to group radio buttons
            val radioGroupNodes = mutableMapOf<String, MutableList<AccessibilityNodeInfo>>()

            // Find web content container to narrow search away from browser URL bar
            val scanRoot = findWebContentContainer(root) ?: root

            fun traverse(node: AccessibilityNodeInfo, parent: AccessibilityNodeInfo? = null) {
                // Strict Browser Chrome Exclusion: Skip Omnibox, URL bars, navigation tools
                if (isBrowserChromeElement(node)) {
                    return
                }

                val className = node.className?.toString() ?: ""
                val isEditable = node.isEditable
                val isCheckable = node.isCheckable
                val compatNode = AccessibilityNodeInfoCompat.wrap(node)
                val roleDesc = compatNode.roleDescription?.toString() ?: ""
                val textStr = node.text?.toString()?.trim() ?: ""
                val hintStr = node.hintText?.toString()?.trim() ?: ""
                val cdStr = node.contentDescription?.toString()?.trim() ?: ""

                // 1. Radio Button Detection (Grouped together)
                val isRadio = isCheckable && (
                        className.contains("RadioButton", ignoreCase = true) ||
                        roleDesc.contains("radio", ignoreCase = true) ||
                        cdStr.contains("radio", ignoreCase = true) ||
                        textStr.contains("radio", ignoreCase = true)
                )

                if (isRadio) {
                    val groupKey = resolveRadioGroupKey(node, parent)
                    radioGroupNodes.getOrPut(groupKey) { mutableListOf() }.add(node)
                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let { traverse(it, node) }
                    }
                    return
                }

                // 2. Checkbox / Agree Tick Box Detection
                // In Chrome, checkboxes often have className = "android.view.View" and isCheckable = true
                val isCheckbox = (isCheckable && !isRadio) ||
                        roleDesc.contains("check", ignoreCase = true) ||
                        className.contains("CheckBox", ignoreCase = true) ||
                        className.contains("Switch", ignoreCase = true)

                if (isCheckbox) {
                    val fieldId = "fm-field-${nextId++}"
                    nodeRegistry[fieldId] = node

                    val label = resolveCheckboxLabel(node, parent)
                    val isReq = cdStr.contains("required", ignoreCase = true) ||
                            label.contains("*") ||
                            label.contains("agree", ignoreCase = true) ||
                            label.contains("consent", ignoreCase = true) ||
                            label.contains("accurate", ignoreCase = true)

                    val sem = FormmateEngine.inferSemanticType(label, "", node.viewIdResourceName ?: "", className)
                    val isChecked = compatNode.isChecked

                    fields.add(
                        FormField(
                            id = fieldId,
                            label = label.replace("*", "").trim(),
                            kind = FieldKind.CHECKBOX,
                            semanticType = if (sem == SemanticType.GENERAL) SemanticType.CONSENT else sem,
                            required = isReq,
                            currentValue = if (isChecked) "Yes, I agree (Checked)" else "",
                            placeholder = "Say 'Yes, I agree' or 'No'",
                            options = listOf(
                                OptionItem("Yes", "Yes, I agree"),
                                OptionItem("No", "No, I do not agree")
                            )
                        )
                    )

                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let { traverse(it, node) }
                    }
                    return
                }

                // 3. Dropdown Menu / <select> / Combobox / Pop-up Button
                // In Chrome on Android, HTML <select> elements have roleDescription = "pop-up button" or "combobox"!
                val isSelect = className.contains("Spinner", ignoreCase = true) ||
                        roleDesc.contains("pop-up button", ignoreCase = true) ||
                        roleDesc.contains("pop up button", ignoreCase = true) ||
                        roleDesc.contains("combobox", ignoreCase = true) ||
                        roleDesc.contains("drop down", ignoreCase = true) ||
                        roleDesc.contains("select", ignoreCase = true) ||
                        (node.isClickable && !isCheckable && !isEditable && (
                                textStr.startsWith("Choose", ignoreCase = true) ||
                                textStr.startsWith("Select", ignoreCase = true) ||
                                hintStr.startsWith("Choose", ignoreCase = true) ||
                                hintStr.startsWith("Select", ignoreCase = true)
                        ))

                if (isSelect) {
                    val fieldId = "fm-field-${nextId++}"
                    nodeRegistry[fieldId] = node

                    val label = resolveNodeLabel(node, parent)
                    val currentVal = textStr
                    val isReq = cdStr.contains("required", ignoreCase = true) || label.contains("*")
                    val sem = FormmateEngine.inferSemanticType(label, "", node.viewIdResourceName ?: "", className)

                    var options = extractOptionsFromNode(node)
                    if (options.isEmpty()) {
                        options = getSemanticDefaultOptions(sem)
                    }

                    options.forEach { opt ->
                        optionNodeRegistry["$fieldId:${opt.value.lowercase()}"] = node
                        optionNodeRegistry["$fieldId:${opt.label.lowercase()}"] = node
                    }

                    fields.add(
                        FormField(
                            id = fieldId,
                            label = label.replace("*", "").trim(),
                            kind = FieldKind.SELECT,
                            semanticType = sem,
                            required = isReq,
                            currentValue = currentVal,
                            placeholder = "Choose an option",
                            options = options
                        )
                    )

                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let { traverse(it, node) }
                    }
                    return
                }

                // 4. Date of Birth / Date Inputs (<input type="date">)
                // In Chrome, date inputs may be editable or clickable pickers
                val isDateCandidate = run {
                    val lbl = resolveNodeLabel(node, parent).lowercase()
                    val lowerTxt = textStr.lowercase()
                    val lowerHint = hintStr.lowercase()
                    val lowerCd = cdStr.lowercase()

                    lbl.contains("date of birth") || lbl.contains("dob") || lbl.contains("birth") ||
                            lowerTxt.contains("dd/mm/yyyy") || lowerTxt.contains("yyyy-mm-dd") ||
                            lowerHint.contains("dd/mm/yyyy") || lowerHint.contains("yyyy-mm-dd") ||
                            lowerCd.contains("date") || lowerCd.contains("birth")
                }

                if (isDateCandidate && (isEditable || node.isClickable)) {
                    val fieldId = "fm-field-${nextId++}"
                    nodeRegistry[fieldId] = node

                    val label = resolveNodeLabel(node, parent)
                    val isReq = cdStr.contains("required", ignoreCase = true) || label.contains("*")

                    fields.add(
                        FormField(
                            id = fieldId,
                            label = label.replace("*", "").trim(),
                            kind = FieldKind.INPUT,
                            semanticType = SemanticType.DATE_OF_BIRTH,
                            required = isReq,
                            currentValue = textStr,
                            placeholder = if (hintStr.isNotBlank()) hintStr else "dd/mm/yyyy"
                        )
                    )

                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let { traverse(it, node) }
                    }
                    return
                }

                // 5. Editable Text Inputs & Textareas
                if (isEditable) {
                    val kind = if (className.contains("EditText", ignoreCase = true) ||
                        className.contains("AutoCompleteTextView", ignoreCase = true) ||
                        node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
                    ) {
                        FieldKind.INPUT
                    } else {
                        FieldKind.TEXTAREA
                    }

                    val fieldId = "fm-field-${nextId++}"
                    nodeRegistry[fieldId] = node

                    val label = resolveNodeLabel(node, parent)
                    val hint = hintStr
                    val currentVal = textStr
                    val isReq = cdStr.contains("required", ignoreCase = true) || label.contains("*")
                    val sem = FormmateEngine.inferSemanticType(label, hint, node.viewIdResourceName ?: "", className)

                    fields.add(
                        FormField(
                            id = fieldId,
                            label = label.replace("*", "").trim(),
                            kind = kind,
                            semanticType = sem,
                            required = isReq,
                            currentValue = currentVal,
                            placeholder = hint
                        )
                    )
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    traverse(child, node)
                }
            }

            try {
                traverse(scanRoot)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Consolidate Radio Button Groups into single structured FormField per group
            radioGroupNodes.forEach { (groupLabel, radioNodes) ->
                val fieldId = "fm-field-${nextId++}"
                val options = mutableListOf<OptionItem>()
                var checkedVal = ""

                radioNodes.forEach { rNode ->
                    val optLabel = resolveRadioOptionLabel(rNode)
                    val optItem = OptionItem(value = optLabel, label = optLabel)
                    options.add(optItem)

                    optionNodeRegistry["$fieldId:${optLabel.lowercase()}"] = rNode
                    optionNodeRegistry["$fieldId:${optItem.value.lowercase()}"] = rNode

                    val compat = AccessibilityNodeInfoCompat.wrap(rNode)
                    if (compat.isChecked) {
                        checkedVal = optLabel
                    }
                }

                radioNodes.firstOrNull()?.let { nodeRegistry[fieldId] = it }

                val sem = FormmateEngine.inferSemanticType(groupLabel, "", "", "RadioButton")

                fields.add(
                    FormField(
                        id = fieldId,
                        label = groupLabel.replace("*", "").trim(),
                        kind = FieldKind.RADIO,
                        semanticType = sem,
                        required = true,
                        currentValue = checkedVal,
                        placeholder = "Select one option",
                        options = options
                    )
                )
            }

            return fields
        }

        /**
         * Resolves the group name / category for radio buttons (e.g. "Gender")
         */
        private fun resolveRadioGroupKey(node: AccessibilityNodeInfo, parent: AccessibilityNodeInfo?): String {
            if (parent != null) {
                val pText = parent.text?.toString()?.trim()
                if (!pText.isNullOrEmpty() && pText.length < 50) return pText

                val pCd = parent.contentDescription?.toString()?.trim()
                if (!pCd.isNullOrEmpty() && pCd.length < 50) return pCd

                for (i in 0 until parent.childCount) {
                    if (parent.getChild(i) == node && i > 0) {
                        val prev = parent.getChild(i - 1)
                        val prevText = prev?.text?.toString()?.trim()
                        if (!prevText.isNullOrEmpty() && prevText.length < 50 && !prev.isCheckable) {
                            return prevText
                        }
                    }
                }

                // Check grandparent label/legend
                val gp = parent.parent
                if (gp != null) {
                    for (i in 0 until gp.childCount) {
                        val c = gp.getChild(i)
                        val cText = c?.text?.toString()?.trim()
                        if (!cText.isNullOrEmpty() && cText.length in 3..40 && !c.isCheckable) {
                            return cText
                        }
                    }
                }
            }

            val resId = node.viewIdResourceName?.substringAfterLast("/")?.replace("_", " ")
            if (!resId.isNullOrEmpty()) {
                val cleaned = resId.replace(Regex("\\d+$"), "").trim()
                if (cleaned.isNotBlank()) return cleaned.replaceFirstChar { it.uppercase() }
            }

            return "Gender"
        }

        private fun resolveRadioOptionLabel(node: AccessibilityNodeInfo): String {
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) return text

            val cd = node.contentDescription?.toString()?.trim()
            if (!cd.isNullOrEmpty()) return cd

            val parent = node.parent
            if (parent != null) {
                for (i in 0 until parent.childCount) {
                    if (parent.getChild(i) == node) {
                        if (i + 1 < parent.childCount) {
                            val next = parent.getChild(i + 1)
                            val nextText = next?.text?.toString()?.trim()
                            if (!nextText.isNullOrEmpty() && nextText.length < 40) return nextText
                        }
                        if (i > 0) {
                            val prev = parent.getChild(i - 1)
                            val prevText = prev?.text?.toString()?.trim()
                            if (!prevText.isNullOrEmpty() && prevText.length < 40) return prevText
                        }
                    }
                }
                val parentText = parent.text?.toString()?.trim()
                if (!parentText.isNullOrEmpty() && parentText.length < 40) return parentText
            }

            return "Option"
        }

        /**
         * Resolves label for Checkbox / Agree Tickbox.
         * Looks at following sibling, preceding sibling, or enclosing parent text.
         */
        private fun resolveCheckboxLabel(node: AccessibilityNodeInfo, parent: AccessibilityNodeInfo?): String {
            if (parent != null) {
                // 1. Check next sibling (standard: <input type="checkbox"> <label>I confirm...</label>)
                for (i in 0 until parent.childCount) {
                    if (parent.getChild(i) == node && i + 1 < parent.childCount) {
                        val next = parent.getChild(i + 1)
                        val nextText = next?.text?.toString()?.trim()
                        if (!nextText.isNullOrEmpty() && nextText.length in 3..140) return nextText
                    }
                }

                // 2. Check enclosing parent text
                val pText = parent.text?.toString()?.trim()
                if (!pText.isNullOrEmpty() && pText.length in 4..140) return pText

                // 3. Check preceding sibling
                for (i in 0 until parent.childCount) {
                    if (parent.getChild(i) == node && i > 0) {
                        val prev = parent.getChild(i - 1)
                        val prevText = prev?.text?.toString()?.trim()
                        if (!prevText.isNullOrEmpty() && prevText.length in 3..140) return prevText
                    }
                }
            }

            val cd = node.contentDescription?.toString()?.trim()
            if (!cd.isNullOrEmpty()) return cd

            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) return text

            return "I confirm that the information I entered is accurate."
        }

        private fun resolveNodeLabel(node: AccessibilityNodeInfo, parent: AccessibilityNodeInfo?): String {
            val compat = AccessibilityNodeInfoCompat.wrap(node)
            val labeledBy = compat.labeledBy
            if (labeledBy != null) {
                val text = labeledBy.text?.toString()?.trim()
                if (!text.isNullOrEmpty()) return text
            }

            // Check preceding sibling in parent container
            if (parent != null) {
                for (i in 0 until parent.childCount) {
                    val child = parent.getChild(i) ?: continue
                    if (child == node && i > 0) {
                        val prev = parent.getChild(i - 1)
                        val prevText = prev?.text?.toString()?.trim()
                        if (!prevText.isNullOrEmpty() && prevText.length < 80 &&
                            !prevText.startsWith("Choose", ignoreCase = true) &&
                            !prevText.startsWith("Select", ignoreCase = true)
                        ) {
                            return prevText
                        }
                    }
                }
                // Check parent preceding sibling
                val gp = parent.parent
                if (gp != null) {
                    for (i in 0 until gp.childCount) {
                        val child = gp.getChild(i) ?: continue
                        if (child == parent && i > 0) {
                            val prev = gp.getChild(i - 1)
                            val prevText = prev?.text?.toString()?.trim()
                            if (!prevText.isNullOrEmpty() && prevText.length < 80) {
                                return prevText
                            }
                        }
                    }
                }
            }

            val cd = node.contentDescription?.toString()?.trim()
            if (!cd.isNullOrEmpty() && !cd.equals("input", ignoreCase = true)) {
                return cd
            }

            val hint = node.hintText?.toString()?.trim()
            if (!hint.isNullOrEmpty() && !hint.startsWith("Choose", ignoreCase = true) && !hint.startsWith("Select", ignoreCase = true)) {
                return hint
            }

            val resId = node.viewIdResourceName?.substringAfterLast("/")?.replace("_", " ")
            if (!resId.isNullOrEmpty()) {
                return resId.replaceFirstChar { it.uppercase() }
            }

            return "Form Field"
        }

        private fun extractOptionsFromNode(node: AccessibilityNodeInfo): List<OptionItem> {
            val list = mutableListOf<OptionItem>()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val text = child.text?.toString()?.trim() ?: child.contentDescription?.toString()?.trim() ?: ""
                if (text.isNotBlank() && !text.startsWith("Choose", ignoreCase = true) && !text.startsWith("Select", ignoreCase = true)) {
                    list.add(OptionItem(value = text, label = text))
                }
            }
            return list
        }

        private fun getSemanticDefaultOptions(semanticType: SemanticType): List<OptionItem> {
            return when (semanticType) {
                SemanticType.STATE -> listOf(
                    OptionItem("Assam", "Assam"),
                    OptionItem("Meghalaya", "Meghalaya"),
                    OptionItem("Manipur", "Manipur"),
                    OptionItem("Tripura", "Tripura"),
                    OptionItem("Arunachal Pradesh", "Arunachal Pradesh"),
                    OptionItem("West Bengal", "West Bengal"),
                    OptionItem("Delhi", "Delhi"),
                    OptionItem("Maharashtra", "Maharashtra"),
                    OptionItem("Other", "Other")
                )
                SemanticType.GENDER -> listOf(
                    OptionItem("Female", "Female"),
                    OptionItem("Male", "Male"),
                    OptionItem("Prefer not to say", "Prefer not to say")
                )
                SemanticType.COURSE -> listOf(
                    OptionItem("BSc", "BSc"),
                    OptionItem("BA", "BA"),
                    OptionItem("BCom", "BCom"),
                    OptionItem("BTech", "BTech"),
                    OptionItem("MCA", "MCA"),
                    OptionItem("MBA", "MBA"),
                    OptionItem("Other", "Other")
                )
                else -> emptyList()
            }
        }

        /**
         * Fills a scanned form field on screen with high reliability across:
         * - Text inputs / textareas
         * - Checkbox agree tickboxes (clicks node or parent label wrapper)
         * - Radio button groups (clicks exact matching option node)
         * - Dropdown selects (clicks dropdown to open native dialog, selects option, or clicks matched option)
         */
        fun fillField(fieldId: String, value: String): Boolean {
            val node = nodeRegistry[fieldId]
            val normValue = value.lowercase().trim()

            // 1. Radio Button Group Filling
            val targetRadioNode = optionNodeRegistry["$fieldId:$normValue"]
            if (targetRadioNode != null) {
                return clickNodeSafely(targetRadioNode)
            }

            for ((key, rNode) in optionNodeRegistry) {
                if (key.startsWith("$fieldId:")) {
                    val optName = key.substringAfter("$fieldId:")
                    if (optName.contains(normValue) || normValue.contains(optName)) {
                        return clickNodeSafely(rNode)
                    }
                }
            }

            if (node == null) return false
            val compat = AccessibilityNodeInfoCompat.wrap(node)

            return try {
                when {
                    // 2. Checkbox / Agree Tickbox
                    compat.isCheckable || node.isCheckable -> {
                        val targetChecked = FormmateEngine.parseBooleanAnswer(value) ?: true
                        val currentChecked = compat.isChecked

                        if (currentChecked != targetChecked) {
                            clickNodeSafely(node)
                        } else {
                            true
                        }
                    }

                    // 3. Dropdown Menu / Select / Pop-up button
                    node.className?.toString()?.contains("Spinner", ignoreCase = true) == true ||
                            compat.roleDescription?.contains("combobox", ignoreCase = true) == true ||
                            compat.roleDescription?.contains("pop-up button", ignoreCase = true) ||
                            compat.roleDescription?.contains("pop up button", ignoreCase = true) ||
                            compat.roleDescription?.contains("select", ignoreCase = true) == true -> {
                        clickNodeSafely(node)
                        mainHandler.postDelayed({
                            selectOptionInActiveDialog(value)
                        }, 250L)
                        true
                    }

                    // 4. Editable Text Inputs & Textareas & Date
                    compat.isEditable || node.isEditable -> {
                        val args = Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                value
                            )
                        }
                        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        if (!success) {
                            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        }
                        true
                    }

                    else -> {
                        clickNodeSafely(node)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        private fun clickNodeSafely(node: AccessibilityNodeInfo): Boolean {
            if (node.isClickable) {
                val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (ok) return true
            }

            var current: AccessibilityNodeInfo? = node.parent
            var depth = 0
            while (current != null && depth < 3) {
                if (current.isClickable) {
                    val ok = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (ok) return true
                }
                current = current.parent
                depth++
            }

            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        private fun selectOptionInActiveDialog(targetValue: String) {
            val root = instance?.rootInActiveWindow ?: return
            val normTarget = targetValue.lowercase().trim()

            fun findAndClick(curr: AccessibilityNodeInfo): Boolean {
                val text = curr.text?.toString()?.lowercase()?.trim() ?: ""
                val cd = curr.contentDescription?.toString()?.lowercase()?.trim() ?: ""

                if (text.isNotBlank() && (text == normTarget || text.contains(normTarget) || normTarget.contains(text))) {
                    clickNodeSafely(curr)
                    return true
                }
                if (cd.isNotBlank() && (cd == normTarget || cd.contains(normTarget) || normTarget.contains(cd))) {
                    clickNodeSafely(curr)
                    return true
                }

                for (i in 0 until curr.childCount) {
                    curr.getChild(i)?.let {
                        if (findAndClick(it)) return true
                    }
                }
                return false
            }

            findAndClick(root)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        nodeRegistry.clear()
        optionNodeRegistry.clear()
        super.onDestroy()
    }
}
