package com.dream.ourphone.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 把 UI 树转成结构化描述，让我们能"看见"屏幕上有什么。
 */
object ScreenReader {

    data class ScreenNode(
        val className: String,
        val text: String?,
        val contentDescription: String?,
        val viewId: String?,
        val bounds: String,
        val clickable: Boolean,
        val scrollable: Boolean,
        val children: List<ScreenNode>
    )

    data class ScreenSnapshot(
        val packageName: String,
        val timestamp: Long,
        val tree: List<ScreenNode>
    )

    fun capture(rootNode: AccessibilityNodeInfo?, packageName: String?): ScreenSnapshot {
        return ScreenSnapshot(
            packageName = packageName ?: "unknown",
            timestamp = System.currentTimeMillis(),
            tree = if (rootNode != null) listOf(parseNode(rootNode)) else emptyList()
        )
    }

    fun captureFlat(rootNode: AccessibilityNodeInfo?, packageName: String?): Map<String, Any> {
        val texts = mutableListOf<String>()
        val clickables = mutableListOf<Map<String, Any>>()
        if (rootNode != null) {
            flattenNode(rootNode, texts, clickables)
        }
        return mapOf(
            "package" to (packageName ?: "unknown"),
            "timestamp" to System.currentTimeMillis(),
            "visible_texts" to texts,
            "clickable_elements" to clickables
        )
    }

    private fun parseNode(node: AccessibilityNodeInfo, depth: Int = 0): ScreenNode {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val children = mutableListOf<ScreenNode>()
        if (depth < 15) {
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    children.add(parseNode(child, depth + 1))
                    child.recycle()
                }
            }
        }
        return ScreenNode(
            className = node.className?.toString() ?: "",
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            viewId = node.viewIdResourceName,
            bounds = "${rect.left},${rect.top},${rect.right},${rect.bottom}",
            clickable = node.isClickable,
            scrollable = node.isScrollable,
            children = children
        )
    }

    private fun flattenNode(
        node: AccessibilityNodeInfo,
        texts: MutableList<String>,
        clickables: MutableList<Map<String, Any>>,
        depth: Int = 0
    ) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }

        if (node.isClickable) {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            val label = node.text?.toString()
                ?: node.contentDescription?.toString()
                ?: node.viewIdResourceName
                ?: ""
            if (label.isNotBlank()) {
                clickables.add(mapOf(
                    "label" to label,
                    "bounds" to "${rect.left},${rect.top},${rect.right},${rect.bottom}",
                    "center_x" to (rect.left + rect.right) / 2,
                    "center_y" to (rect.top + rect.bottom) / 2
                ))
            }
        }

        if (depth < 15) {
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    flattenNode(child, texts, clickables, depth + 1)
                    child.recycle()
                }
            }
        }
    }
}
