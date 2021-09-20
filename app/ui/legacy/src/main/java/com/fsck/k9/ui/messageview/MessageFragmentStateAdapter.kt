package com.fsck.k9.ui.messageview

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.fsck.k9.ui.R
import java.lang.ref.WeakReference

class MessageFragmentStateAdapter(
    private val viewPagerFragment: MessageViewPagerFragment,
) : FragmentStateAdapter(viewPagerFragment) {

    /**
     *  This is a 'weak' cache of created fragments, so we can find them again if needed,
     *  but they will be disposed when the ViewPager releases them
     */
    private val fragmentCache = mutableMapOf<Int, WeakReference<MessageViewFragment>>()

    override fun createFragment(position: Int): MessageViewFragment {
        val reference = viewPagerFragment.getMessageReference(position)
        val fragment = MessageViewFragment.newInstance(reference)
        fragmentCache[position] = WeakReference(fragment)
        return fragment
    }

    override fun getItemCount(): Int {
        return viewPagerFragment.getMessageCount()
    }

    /**
     *  This lets the WebView capture swipe actions if it is scrollable
     */
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.addOnItemTouchListener(
            object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(view: RecyclerView, event: MotionEvent): Boolean {
                    doInterceptTouchEvent(event)
                    return super.onInterceptTouchEvent(view, event)
                }
            }
        )
    }

    private var webView: WebView? = null
        get() {
            if (field == null) {
                val view: View? = getActiveMessageViewFragment()?.view?.findViewById(R.id.message_content)
                field = if (view is WebView) view else null
            }
            return field
        }

    private fun doInterceptTouchEvent(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (webView != null) {
                val webViewRect = Rect()
                val webViewOrigin = IntArray(2)
                webView!!.getHitRect(webViewRect)
                webView!!.getLocationOnScreen(webViewOrigin)
                webViewRect.offset(webViewOrigin[0], webViewOrigin[1])
                if (webViewRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    val touchInLeft = event.x < (webViewRect.width() * 0.2f)
                    val touchInRight = event.x > (webViewRect.width() * 0.8f)
                    val canScrollLeft = webView!!.canScrollHorizontally(-1)
                    val canScrollRight = webView!!.canScrollHorizontally(1)
                    val canScrollEither = canScrollRight || canScrollLeft
                    val parentIntercept = (!canScrollEither) || (touchInLeft && !canScrollLeft) || (touchInRight && !canScrollRight)
                    webView!!.parent?.requestDisallowInterceptTouchEvent(!parentIntercept)
                }
            }
        }
    }

    fun getActiveMessageViewFragment(): MessageViewFragment? {
        val position = viewPagerFragment.getActivePosition()
        return fragmentCache[position]?.get()
    }

    fun resetWebView() {
        webView = null
    }
}
