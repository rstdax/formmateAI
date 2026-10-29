package com.rst.heyrst.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.rst.heyrst.engine.FormmateEngine
import com.rst.heyrst.model.FieldKind
import com.rst.heyrst.model.FormField
import com.rst.heyrst.model.OptionItem
import com.rst.heyrst.model.SemanticType
import java.util.concurrent.ConcurrentHashMap

/**
 * Android Accessibility Service for Hey RST.
 * Inspects active Chrome and browser windows to discover form inputs, textareas,
 * select dropdowns, checkboxes, and radio buttons, and programmatically populates them
 * via AccessibilityNodeInfo actions (ACTION_SET_TEXT and ACTION_CLICK).
 */
class RSTAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: RSTAccessibilityService? = null
            private set

        fun isServiceRunning(): Boolean = instance != null

        // In-memory registry mapping runtime field IDs (fm-field-1) to live AccessibilityNodeInfo objects
        private val nodeRegistry = ConcurrentHashMap<String, AccessibilityNodeInfo>()

        /**
         * Scans the currently active window (e.g. Chrome browser) and returns discovered form fields.
         */
        fun scanActiveWindow(): List<FormField> {
            val service = instance ?: return emptyList()
            val root = service.rootInActiveWindow ?: return emptyList()

            nodeRegistry.clear()
            val fields = mutableListOf<FormField>()
            var nextId = 1

            fun traverse(node: AccessibilityNodeInfo, parentNode: AccessibilityNodeInfo? = null) {
                val className = node.className?.toString() ?: ""
                val isEditable = node.isEditable
                val isCheckable = node.isCheckable

                val compatNode = AccessibilityNodeInfoCompat.wrap(node)
                val roleDesc = compatNode.roleDescription?.toString() ?: ""

                var kind: FieldKind? = null
                if (isEditable) {
                    kind = if (className.contains("EditText", ignoreCase = true) ||
                        className.contains("AutoCompleteTextView", ignoreCase = true) ||
                        node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
                    ) {
                        FieldKind.INPUT
                    } else {
                        FieldKind.TEXTAREA
                    }
                } else if (isCheckable) {
                    kind = if (className.contains("RadioButton", ignoreCase = true) ||
                        node.contentDescription?.contains("radio", ignoreCase = true) == true ||
                        roleDesc.contains("radio", ignoreCase = true)
                    ) {
                        FieldKind.RADIO
                    } else {
                        FieldKind.CHECKBOX
                    }
                } else if (className.contains("Spinner", ignoreCase = true) ||
                    roleDesc.contains("combobox", ignoreCase = true) ||
                    roleDesc.contains("drop down", ignoreCase = true)
                ) {
                    kind = FieldKind.SELECT
                }

                if (kind != null) {
                    val fieldId = "fm-field-${nextId++}"
                    nodeRegistry[fieldId] = node

                    val label = resolveNodeLabel(node, parentNode)
                    val hint = node.hintText?.toString() ?: ""
                    val currentVal = node.text?.toString() ?: ""
                    val isReq = node.contentDescription?.contains("required", ignoreCase = true) == true ||
                            label.contains("*")

                    val sem = FormmateEngine.inferSemanticType(
                        label = label,
                        hint = hint,
                        id = node.viewIdResourceName ?: "",
                        className = className
                    )

                    val options = if (kind == FieldKind.SELECT || kind == FieldKind.RADIO) {
                        extractOptions(node)
                    } else emptyList()

                    fields.add(
                        FormField(
                            id = fieldId,
                            label = label.replace("*", "").trim(),
                            kind = kind,
                            semanticType = sem,
                            required = isReq,
                            currentValue = currentVal,
                            placeholder = hint,
                            options = options
                        )
                    )
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    traverse(child, node)
                }
            }

            try {
                traverse(root)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return fields
        }

        /**
         * Resolves human-readable label for an AccessibilityNodeInfo by inspecting:
         * 1. labeledBy node
         * 2. contentDescription
         * 3. hintText
         * 4. Preceding sibling node text (e.g. <label> before <input> in Chrome web DOM)
         * 5. viewIdResourceName
         */
        private fun resolveNodeLabel(node: AccessibilityNodeInfo, parent: AccessibilityNodeInfo?): String {
            // 1. LabeledBy (API 28+)
            val compat = AccessibilityNodeInfoCompat.wrap(node)
            val labeledBy = compat.labeledBy
            if (labeledBy != null) {
                val text = labeledBy.text?.toString()?.trim()
                if (!text.isNullOrEmpty()) return text
            }

            // 2. Content Description
            val cd = node.contentDescription?.toString()?.trim()
            if (!cd.isNullOrEmpty() && !cd.equals("input", ignoreCase = true)) {
                return cd
            }

            // 3. Hint Text
            val hint = node.hintText?.toString()?.trim()
            if (!hint.isNullOrEmpty()) {
                return hint
            }

            // 4. Preceding sibling label in parent container
            if (parent != null) {
                for (i in 0 until parent.childCount) {
                    val child = parent.getChild(i) ?: continue
                    if (child == node && i > 0) {
                        val prev = parent.getChild(i - 1)
                        val prevText = prev?.text?.toString()?.trim()
                        if (!prevText.isNullOrEmpty() && prevText.length < 80) {
                            return prevText
                        }
                    }
                }
            }

            // 5. Fallback to resource ID
            val resId = node.viewIdResourceName?.substringAfterLast("/")?.replace("_", " ")
            if (!resId.isNullOrEmpty()) {
                return resId.replaceFirstChar { it.uppercase() }
            }

            return "Unlabelled field"
        }

        private fun extractOptions(node: AccessibilityNodeInfo): List<OptionItem> {
            val list = mutableListOf<OptionItem>()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val text = child.text?.toString() ?: child.contentDescription?.toString() ?: ""
                if (text.isNotBlank()) {
                    list.add(OptionItem(value = text, label = text))
                }
            }
            return list
        }

        /**
         * Fills a scanned form field on screen with the normalized value.
         */
        fun fillField(fieldId: String, value: String): Boolean {
            val node = nodeRegistry[fieldId] ?: return false
            val compat = AccessibilityNodeInfoCompat.wrap(node)

            return try {
                when {
                    compat.isCheckable -> {
                        val targetChecked = FormmateEngine.parseBooleanAnswer(value) ?: true
                        if (compat.isChecked != targetChecked) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        } else {
                            true
                        }
                    }
                    compat.isEditable -> {
                        val args = Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                value
                            )
                        }
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    }
                    else -> {
                        // Dropdown / Button / Radio click
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Can monitor window change events if needed
    }

    override fun onInterrupt() {
        // Handle interruption
    }

    override fun onDestroy() {
        instance = null
        nodeRegistry.clear()
        super.onDestroy()
    }
}
