package com.dream.ourphone.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

object ScreenReader {

    private const val MAX_RETRY = 3
    private const val RETRY_DELAY_MS = 300L
    private const val MAX_DEPTH = 15

    data class ScreenSnapshot(
        val packageName: String,
        val timestamp: Long,
        val visibleTexts: List<String>,
        val clickableElements: List<Map<String, Any>>,
        val scrollableElements: List<Map<String, Any>>,
        val editableElements: List<Map<String, Any>>
    )

    data class FoundElement(
        val centerX: Int,
        val centerY: Int,
        val bounds: Rect,
        val text: String?,
        val contentDescription: String?,
        val viewId: String?,
        val node: AccessibilityNodeInfo
    )

    suspend fun captureWithRetry(service: AccessibilityService, packageName: String?): ScreenSnapshot {
        repeat(MAX_RETRY) { attempt ->
            val root = service.rootInActiveWindow
            if (root != null) {
                val snapshot = captureFlat(root, packageName)
                root.recycle()
                return snapshot
            }
            if (attempt < MAX_RETRY - 1) delay(RETRY_DELAY_MS)
        }
        return ScreenSnapshot(
            packageName = packageName ?: "unknown",
            timestamp = System.currentTimeMillis(),
            visibleTexts = emptyList(),
            clickableElements = emptyList(),
            scrollableElements = emptyList(),
            editableElements = emptyList()
        )
    }

    fun captureFlat(rootNode: AccessibilityNodeInfo?, packageName: String?): ScreenSnapshot {
        val texts = mutableListOf<String>()
        val clickables = mutableListOf<Map<String, Any>>()
        val scrollables = mutableListOf<Map<String, Any>>()
        val editables = mutableListOf<Map<String, Any>>()
        if (rootNode != null) {
            flattenNode(rootNode, texts, clickables, scrollables, editables)
        }
        return ScreenSnapshot(
            packageName = packageName ?: "unknown",
            timestamp = System.currentTimeMillis(),
            visibleTexts = texts,
            clickableElements = clickables,
            scrollableElements = scrollables,
            editableElements = editables
        )
    }

    fun findByText(root: AccessibilityNodeInfo, text: String, exact: Boolean = false): FoundElement? {
        return findNode(root) { node ->
            val nodeText = node.text?.toString() ?: ""
            val nodeDesc = node.contentDescription?.toString() ?: ""
            if (exact) {
                nodeText == text || nodeDesc == text
            } else {
                nodeText.contains(text, ignoreCase = true)
                        || nodeDesc.contains(text, ignoreCase = true)
            }
        }
    }

    fun findByViewId(root: AccessibilityNodeInfo, viewId: String): FoundElement? {
        return findNode(root) { node ->
            node.viewIdResourceName?.contains(viewId) == true
        }
    }

    fun findByClassName(root: AccessibilityNodeInfo, className: String): FoundElement? {
        return findNode(root) { node ->
            node.className?.toString()?.contains(className) == true
        }
    }

    fun findAllByText(root: AccessibilityNodeInfo, text: String): List<FoundElement> {
        val results = mutableListOf<FoundElement>()
        findAllNodes(root, results) { node ->
            val nodeText = node.text?.toString() ?: ""
            val nodeDesc = node.contentDescription?.toString() ?: ""
            nodeText.contains(text, ignoreCase = true)
                    || nodeDesc.contains(text, ignoreCase = true)
        }
        return results
    }

    private fun findNode(
        node: AccessibilityNodeInfo,
        depth: Int = 0,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): FoundElement? {
        if (predicate(node)) return nodeToElement(node)
        if (depth >= MAX_DEPTH) return null
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNode(child, depth + 1, predicate)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun findAllNodes(
        node: AccessibilityNodeInfo,
        results: MutableList<FoundElement>,
        depth: Int = 0,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ) {
        if (predicate(node)) results.add(nodeToElement(node))
        if (depth >= MAX_DEPTH) return
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findAllNodes(child, results, depth + 1, predicate)
            child.recycle()
        }
    }

    private fun nodeToElement(node: AccessibilityNodeInfo): FoundElement {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return FoundElement(
            centerX = rect.centerX(),
            centerY = rect.centerY(),
            bounds = rect,
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            viewId = node.viewIdResourceName,
            node = AccessibilityNodeInfo.obtain(node)
        )
    }

    private fun flattenNode(
        node: AccessibilityNodeInfo,
        texts: MutableList<String>,
        clickables: MutableList<Map<String, Any>>,
        scrollables: MutableList<Map<String, Any>>,
        editables: MutableList<Map<String, Any>>,
        depth: Int = 0
    ) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() && node.text == null }
            ?.let { texts.add(it) }

        val rect = Rect()
        node.getBoundsInScreen(rect)
        val label = node.text?.toString()
            ?: node.contentDescription?.toString()
            ?: node.viewIdResourceName
            ?: ""

        if (node.isClickable && label.isNotBlank()) {
            clickables.add(mapOf(
                "label" to label,
                "bounds" to "${rect.left},${rect.top},${rect.right},${rect.bottom}",
                "center_x" to rect.centerX(),
                "center_y" to rect.centerY(),
                "id" to (node.viewIdResourceName ?: "")
            ))
        }

        if (node.isScrollable) {
            scrollables.add(mapOf(
                "bounds" to "${rect.left},${rect.top},${rect.right},${rect.bottom}",
                "class" to (node.className?.toString() ?: ""),
                "id" to (node.viewIdResourceName ?: "")
            ))
        }

        if (node.isEditable) {
            editables.add(mapOf(
                "text" to (node.text?.toString() ?: ""),
                "hint" to (node.hintText?.toString() ?: ""),
                "bounds" to "${rect.left},${rect.top},${rect.right},${rect.bottom}",
                "focused" to node.isFocused,
                "id" to (node.viewIdResourceName ?: "")
            ))
        }

        if (depth < MAX_DEPTH) {
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    flattenNode(child, texts, clickables, scrollables, editables, depth + 1)
                    child.recycle()
                }
            }
        }
    }
}
