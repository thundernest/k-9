package com.fsck.k9.ui.messageview

import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import android.view.View
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.fsck.k9.controller.MessageReference
import com.fsck.k9.fragment.MessageListFragment
import com.fsck.k9.ui.R
import java.util.WeakHashMap

class MessageFragmentStateAdapter(
    fragment: Fragment,
    private val messageListFragment: MessageListFragment,
    private val viewPager: ViewPager2
) : FragmentStateAdapter(fragment) {

    init {
        viewPager.adapter = this
    }

    /**
     *  This is a 'weak' cache of created fragments, so we can find them again if needed,
     *  but they will be disposed when the ViewPager releases them
     */
    private val fragmentCache: MutableMap<MessageViewFragment, Int> = WeakHashMap()

    @RequiresApi(Build.VERSION_CODES.N)
    override fun createFragment(position: Int): MessageViewFragment {
        val reference = messageListFragment.getReferenceForPosition(position)
        val fragment = MessageViewFragment.newInstance(reference)
        // flush cached fragments which previously had the same position id
        fragmentCache.entries.removeIf { entry -> position == entry.value }
        fragmentCache[fragment] = position
        return fragment
    }

    override fun getItemCount(): Int {
        return messageListFragment.getListView().count
    }

    private fun isTouchOverWebView(webView: View, event: MotionEvent): Boolean {
        val webViewRect = Rect()
        webView.getHitRect(webViewRect)
        val webViewOrigin = IntArray(2)
        webView.getLocationOnScreen(webViewOrigin)
        webViewRect.offset(webViewOrigin[0], webViewOrigin[1])
        return webViewRect.contains(event.getRawX().toInt(), event.getRawY().toInt())
    }

    /**
     *  This lets the WebView intercept swipe actions within its scrollable bounds before the ViewPager will process swipes
     */
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        recyclerView.addOnItemTouchListener(
            object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(view: RecyclerView, event: MotionEvent): Boolean {
                    return super.onInterceptTouchEvent(view, event) || isWebViewIntercepting(event)
                }
            }
        )
        super.onAttachedToRecyclerView(recyclerView)
    }

    private var isWebViewIntercepting = false

    private fun isWebViewIntercepting(event: MotionEvent): Boolean {
        val action = event.actionMasked

        // the WebView is already intercepting
        if ((action == MotionEvent.ACTION_MOVE) && isWebViewIntercepting) {
            return true
        }

        // check for a down hit inside the WebView
        if (action == MotionEvent.ACTION_DOWN) {
            val webView: View? = getCurrentMessageViewFragment()?.view?.findViewById(R.id.message_content)
            if (webView != null) {
                val webViewRect = Rect()
                val webViewOrigin = IntArray(2)
                webView.getHitRect(webViewRect)
                val webViewWidth = (webViewRect.right - webViewRect.left).toFloat()
                webView.getLocationOnScreen(webViewOrigin)
                webViewRect.offset(webViewOrigin[0], webViewOrigin[1])
                if (webViewRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    val cannotScrollRight = !webView.canScrollHorizontally(1)
                    val cannotScrollLeft = !webView.canScrollHorizontally(-1)
                    val disableIntercepting = when {
                        // right side of screen
                        (cannotScrollRight && (event.x > webViewWidth * 0.8f)) -> true
                        // left side of screen
                        (cannotScrollLeft && (event.x < webViewWidth * 0.2f)) -> true
                        // anywhere else on screen
                        else -> cannotScrollRight && cannotScrollLeft
                    }
                    isWebViewIntercepting = !disableIntercepting
                    return isWebViewIntercepting
                }
            }
        }

        // otherwise the default is no intercepting
        isWebViewIntercepting = false
        return false
    }

    fun getCurrentMessageViewFragment(): MessageViewFragment? {
        for ((key, value) in fragmentCache.entries) {
            if (viewPager.currentItem == value) return key
        }
        // ViewPager is not (yet) initialized
        return null
    }

    fun getMessagePosition(reference: MessageReference): Int {
        return messageListFragment.getPosition(reference)
    }

    fun doOnPageSelected(position: Int) {
        isWebViewIntercepting = false
    }
}
