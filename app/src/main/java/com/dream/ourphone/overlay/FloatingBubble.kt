package com.dream.ourphone.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.dream.ourphone.R

class FloatingBubble(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bubbleView: View? = null
    private var messageView: View? = null
    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 200
    }

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    fun show() {
        if (bubbleView != null) return
        bubbleView = LayoutInflater.from(context).inflate(R.layout.floating_bubble, null)

        bubbleView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(bubbleView, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy < 100) {
                        toggleMessage()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubbleView, layoutParams)
    }

    fun hide() {
        bubbleView?.let { windowManager.removeView(it) }
        bubbleView = null
        hideMessage()
    }

    fun showText(text: String, durationMs: Long = 5000) {
        val tv = messageView?.findViewById<TextView>(R.id.messageText)
        if (tv != null) {
            tv.text = text
        } else {
            showMessageBubble(text)
        }
        bubbleView?.postDelayed({ hideMessage() }, durationMs)
    }

    private fun toggleMessage() {
        if (messageView != null) {
            hideMessage()
        } else {
            showMessageBubble("在呢")
        }
    }

    private fun showMessageBubble(text: String) {
        if (messageView != null) return
        messageView = LayoutInflater.from(context).inflate(R.layout.sticky_note, null)
        messageView?.findViewById<TextView>(R.id.messageText)?.text = text

        val msgParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = layoutParams.x + 120
            y = layoutParams.y
        }

        windowManager.addView(messageView, msgParams)
    }

    private fun hideMessage() {
        messageView?.let { windowManager.removeView(it) }
        messageView = null
    }
}
