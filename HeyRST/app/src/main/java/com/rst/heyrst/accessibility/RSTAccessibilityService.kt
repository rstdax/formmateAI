package com.rst.heyrst.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
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
import com.rst.heyrst.model.isPlaceholderValue
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Android Accessibility Service for Formate AI (by RST).
 *
 * Capabilities:
 * 1. Strictly filters out browser UI (Omnibox, search bars, URL bars, toolbar, tabs).
 * 2. Physical hardware touch gestures (dispatchGesture) to reliably click:
 *    - Checkboxes / Agree tick boxes (toggling even custom CSS/hidden inputs)
 *    - Radio buttons (tapping exact option coordinates)
 *    - Dropdown menus (<select> pop-up buttons, selecting options across multi-window dialogs)
 *    - Date of Birth pickers (<input type="date">)
 * 3. Scans real active webpage titles for form filling history.
 * 4. Takes real-time screen thumbnails on form activation for bento grid history (API 30+).
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
         * Dispatches a real hardware touch tap at the specified screen coordinates.
         * Bypasses all virtual DOM and Chrome accessibility action absorption!
         */
        fun dispatchTouchTap(x: Float, y: Float, onComplete: ((Boolean) -> Unit)? = null): Boolean {
            val service = instance ?: return false
            if (x <= 0f || y <= 0f) return false
            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 50)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return service.dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onComplete?.invoke(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onComplete?.invoke(false)
                }
            }, null)
        }

        /**
         * Real hardware touch tap on the exact screen coordinates of a node.
         * Falls back to parent/wrapper container if node has zero dimensions.
         */
        fun clickNodePhysically(node: AccessibilityNodeInfo): Boolean {
            node.refresh()
            val rect = Rect()
            node.getBoundsInScreen(rect)

            // If node bounds are valid, perform real hardware touch tap!
            if (rect.width() > 0 && rect.height() > 0) {
                dispatchTouchTap(rect.exactCenterX(), rect.exactCenterY())
            } else {
                // Check parent bounds if node itself is zero-sized (common in custom web checkboxes)
                node.parent?.let { p ->
                    val pRect = Rect()
                    p.getBoundsInScreen(pRect)
                    if (pRect.width() > 0 && pRect.height() > 0) {
                        dispatchTouchTap(pRect.exactCenterX(), pRect.exactCenterY())
                    }
                }
            }

            // Also invoke accessibility action for dual-layer assurance
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                node.parent?.let { p ->
                    if (p.isClickable) {
                        p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                }
            }
            return true
        }

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
         * Extracts the real webpage name/heading from Chrome for history cards.
         */
        fun getActiveWebpageTitle(): String {
            val service = instance ?: return "Web Form"
            val root = service.rootInActiveWindow ?: return "Web Form"

            // 1. Search inside web content container for prominent <h1> or form title
            val webContainer = findWebContentContainer(root)
            if (webContainer != null) {
                fun findHeading(n: AccessibilityNodeInfo, depth: Int): String? {
                    if (depth > 6) return null
                    val txt = n.text?.toString()?.trim() ?: ""
                    val role = AccessibilityNodeInfoCompat.wrap(n).roleDescription?.toString() ?: ""
                    if (role.contains("heading", ignoreCase = true) && txt.isNotBlank() && txt.length in 4..60) {
                        return txt
                    }
                    if (txt.isNotBlank() && txt.length in 5..50 && !isBrowserChromeElement(n)) {
                        if (txt.contains("Application", ignoreCase = true) ||
                            txt.contains("Form", ignoreCase = true) ||
                            txt.contains("Registration", ignoreCase = true) ||
                            txt.contains("Admission", ignoreCase = true) ||
                            txt.contains("Verification", ignoreCase = true) ||
                            txt.contains("Portal", ignoreCase = true)
                        ) {
                            return txt
                        }
                    }
                    for (i in 0 until n.childCount) {
                        n.getChild(i)?.let {
                            val h = findHeading(it, depth + 1)
                            if (h != null) return h
                        }
                    }
                    return null
                }
                val h = findHeading(webContainer, 0)
                if (!h.isNullOrBlank()) return h
            }

            // 2. Search Chrome's tab title or window title
            fun findTabTitle(n: AccessibilityNodeInfo, depth: Int): String? {
                if (depth > 8) return null
                val resId = n.viewIdResourceName?.lowercase() ?: ""
                if (resId.contains("title") || resId.contains("tab_title")) {
                    val t = n.text?.toString()?.trim()
                    if (!t.isNullOrBlank() && t.length in 3..60 && !t.contains("chrome", ignoreCase = true)) {
                        return t
                    }
                }
                for (i in 0 until n.childCount) {
                    n.getChild(i)?.let {
                        val found = findTabTitle(it, depth + 1)
                        if (found != null) return found
                    }
                }
                return null
            }
            val tabTitle = findTabTitle(root, 0)
            if (!tabTitle.isNullOrBlank()) return tabTitle

            return "Online Application Form"
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
                val isRadio = (isCheckable ||
                        roleDesc.contains("radio", ignoreCase = true) ||
                        cdStr.contains("radio", ignoreCase = true) ||
                        className.contains("RadioButton", ignoreCase = true))

                if (isRadio) {
                    val groupKey = resolveRadioGroupKey(node, parent)
                    radioGroupNodes.getOrPut(groupKey) { mutableListOf() }.add(node)
                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let { traverse(it, node) }
                    }
                    return
                }

                // 2. Checkbox / Agree / Declaration Tick Box Detection
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
                            label.contains("declare", ignoreCase = true) ||
                            label.contains("accurate", ignoreCase = true)

                    val sem = FormmateEngine.inferSemanticType(label, "", node.viewIdResourceName ?: "", className)
                    val isChecked = compatNode.isChecked || node.isChecked

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
                    val currentVal = if (isPlaceholderValue(textStr)) "" else textStr
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
                            currentValue = if (isPlaceholderValue(textStr)) "" else textStr,
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
                    val currentVal = if (isPlaceholderValue(textStr)) "" else textStr
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

        private fun resolveCheckboxLabel(node: AccessibilityNodeInfo, parent: AccessibilityNodeInfo?): String {
            if (parent != null) {
                for (i in 0 until parent.childCount) {
                    if (parent.getChild(i) == node && i + 1 < parent.childCount) {
                        val next = parent.getChild(i + 1)
                        val nextText = next?.text?.toString()?.trim()
                        if (!nextText.isNullOrEmpty() && nextText.length in 3..140) return nextText
                    }
                }

                val pText = parent.text?.toString()?.trim()
                if (!pText.isNullOrEmpty() && pText.length in 4..140) return pText

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

            return "I declare that the information provided is accurate."
        }

        private fun resolveNodeLabel(node: AccessibilityNodeInfo, parent: AccessibilityNodeInfo?): String {
            val compat = AccessibilityNodeInfoCompat.wrap(node)
            val labeledBy = compat.labeledBy
            if (labeledBy != null) {
                val text = labeledBy.text?.toString()?.trim()
                if (!text.isNullOrEmpty()) return text
            }

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
                SemanticType.DISTRICT -> listOf(
                    OptionItem("Kamrup", "Kamrup"),
                    OptionItem("Nagaon", "Nagaon"),
                    OptionItem("Cachar", "Cachar"),
                    OptionItem("Dibrugarh", "Dibrugarh"),
                    OptionItem("Jorhat", "Jorhat"),
                    OptionItem("Sonitpur", "Sonitpur"),
                    OptionItem("Other", "Other")
                )
                SemanticType.GENDER -> listOf(
                    OptionItem("Female", "Female"),
                    OptionItem("Male", "Male"),
                    OptionItem("Other", "Other"),
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
         * Fills a scanned form field on screen with real physical touch gestures and accessibility actions:
         * - Checkbox agree tickboxes (clicks node or parent label wrapper physically)
         * - Radio button groups (clicks exact matching option node coordinates physically)
         * - Dropdown selects (clicks dropdown to open native dialog, selects option, or clicks matched option)
         * - Date of Birth (populates ISO and formatted string, or opens DatePicker)
         * - Text inputs / textareas (focus + set text)
         */
        fun fillField(fieldId: String, value: String): Boolean {
            val node = nodeRegistry[fieldId]
            val normValue = value.lowercase().trim()

            // 1. Radio Button Group Filling
            val targetRadioNode = optionNodeRegistry["$fieldId:$normValue"]
            if (targetRadioNode != null) {
                return clickNodePhysically(targetRadioNode)
            }

            for ((key, rNode) in optionNodeRegistry) {
                if (key.startsWith("$fieldId:")) {
                    val optName = key.substringAfter("$fieldId:")
                    if (optName.contains(normValue) || normValue.contains(optName)) {
                        return clickNodePhysically(rNode)
                    }
                }
            }

            if (node == null) return false
            val compat = AccessibilityNodeInfoCompat.wrap(node)
            val className = node.className?.toString() ?: ""
            val roleDesc = compat.roleDescription?.toString() ?: ""
            val isDateCandidate = node.text?.toString()?.contains("/") == true ||
                    node.hintText?.toString()?.contains("/") == true ||
                    roleDesc.contains("date", ignoreCase = true)

            return try {
                when {
                    // 2. Checkbox / Agree / Declaration Tickbox
                    compat.isCheckable || node.isCheckable || className.contains("CheckBox", ignoreCase = true) || roleDesc.contains("check", ignoreCase = true) -> {
                        val targetChecked = FormmateEngine.parseBooleanAnswer(value) ?: true
                        node.refresh()
                        val currentChecked = node.isChecked || compat.isChecked

                        if (currentChecked != targetChecked) {
                            clickNodePhysically(node)
                            // If still not toggled after 180ms, tap parent <label> container
                            mainHandler.postDelayed({
                                node.refresh()
                                val stillChecked = node.isChecked || AccessibilityNodeInfoCompat.wrap(node).isChecked
                                if (stillChecked != targetChecked) {
                                    node.parent?.let { clickNodePhysically(it) }
                                }
                            }, 180L)
                        }
                        true
                    }

                    // 3. Dropdown Menu / Select / Pop-up button (<select>)
                    className.contains("Spinner", ignoreCase = true) ||
                            roleDesc.contains("combobox", ignoreCase = true) ||
                            roleDesc.contains("pop-up button", ignoreCase = true) ||
                            roleDesc.contains("pop up button", ignoreCase = true) ||
                            roleDesc.contains("select", ignoreCase = true) -> {
                        clickNodePhysically(node)
                        mainHandler.postDelayed({
                            selectOptionInActiveDialog(value, 0)
                        }, 200L)
                        true
                    }

                    // 4. Date of Birth Inputs (<input type="date">)
                    isDateCandidate || className.contains("DatePicker", ignoreCase = true) -> {
                        val parsedIso = FormmateEngine.parseSpokenDate(value) ?: value
                        val slashFormat = if (parsedIso.contains("-") && parsedIso.length == 10) {
                            val parts = parsedIso.split("-")
                            "${parts[2]}/${parts[1]}/${parts[0]}"
                        } else value

                        val argsIso = Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, parsedIso)
                        }
                        val okIso = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, argsIso)

                        node.refresh()
                        if (!okIso || node.text.isNullOrBlank() || isPlaceholderValue(node.text?.toString())) {
                            val argsSlash = Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, slashFormat)
                            }
                            val okSlash = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, argsSlash)
                            node.refresh()

                            if (!okSlash || node.text.isNullOrBlank() || isPlaceholderValue(node.text?.toString())) {
                                clickNodePhysically(node)
                                mainHandler.postDelayed({
                                    findAndClickDialogConfirmButton()
                                }, 350L)
                            }
                        }
                        true
                    }

                    // 5. Editable Text Inputs & Textareas
                    compat.isEditable || node.isEditable -> {
                        val args = Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                value
                            )
                        }
                        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        if (!success) {
                            clickNodePhysically(node)
                            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        }
                        true
                    }

                    else -> {
                        clickNodePhysically(node)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        /**
         * Searches for and clicks the target option inside multi-window dialogs (AlertDialog, ListView, Popup).
         */
        private fun selectOptionInActiveDialog(targetValue: String, attempt: Int) {
            val service = instance ?: return
            val normTarget = targetValue.lowercase().trim()

            fun searchInNode(curr: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                val text = curr.text?.toString()?.lowercase()?.trim() ?: ""
                val cd = curr.contentDescription?.toString()?.lowercase()?.trim() ?: ""

                if (text.isNotBlank() && (text == normTarget || text.contains(normTarget) || normTarget.contains(text))) {
                    return curr
                }
                if (cd.isNotBlank() && (cd == normTarget || cd.contains(normTarget) || normTarget.contains(cd))) {
                    return curr
                }

                for (i in 0 until curr.childCount) {
                    val child = curr.getChild(i) ?: continue
                    val found = searchInNode(child)
                    if (found != null) return found
                }
                return null
            }

            var matchedNode: AccessibilityNodeInfo? = null

            // 1. Search in all active interactive windows (Alert dialogs, Popup menus)
            for (window in service.windows) {
                val wRoot = window.root ?: continue
                matchedNode = searchInNode(wRoot)
                if (matchedNode != null) break
            }

            // 2. Also check active root window
            if (matchedNode == null) {
                val root = service.rootInActiveWindow
                if (root != null) {
                    matchedNode = searchInNode(root)
                }
            }

            if (matchedNode != null) {
                clickNodePhysically(matchedNode)
                mainHandler.postDelayed({
                    findAndClickDialogConfirmButton()
                }, 150L)
            } else if (attempt < 8) {
                mainHandler.postDelayed({
                    selectOptionInActiveDialog(targetValue, attempt + 1)
                }, 150L)
            }
        }

        /**
         * Confirms date picker or single-choice dialogs by tapping "OK" / "Set".
         */
        private fun findAndClickDialogConfirmButton() {
            val service = instance ?: return

            fun findOk(curr: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                val text = curr.text?.toString()?.lowercase()?.trim() ?: ""
                val cd = curr.contentDescription?.toString()?.lowercase()?.trim() ?: ""
                if (text == "ok" || text == "set" || text == "done" || cd == "ok" || cd == "set" || cd == "done") {
                    return curr
                }
                for (i in 0 until curr.childCount) {
                    val child = curr.getChild(i) ?: continue
                    val found = findOk(child)
                    if (found != null) return found
                }
                return null
            }

            for (window in service.windows) {
                val wRoot = window.root ?: continue
                val ok = findOk(wRoot)
                if (ok != null) {
                    clickNodePhysically(ok)
                    return
                }
            }
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
