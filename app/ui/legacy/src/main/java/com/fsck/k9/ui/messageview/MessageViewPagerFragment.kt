package com.fsck.k9.ui.messageview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.viewpager2.widget.ViewPager2
import com.fsck.k9.DI.get
import com.fsck.k9.controller.MessageReference
import com.fsck.k9.ui.R
import com.fsck.k9.ui.base.ThemeManager

class MessageViewPagerFragment : Fragment() {
    private val themeManager = get(ThemeManager::class.java)
    private lateinit var fragmentListener: MessageViewPagerFragmentListener
    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: MessageViewPagerAdapter
    private var targetMessage: MessageReference? = null
    private var showedMessage: MessageReference? = null

    companion object {
        private const val ARG_ACTIVE_MESSAGE = "activeMessage"

        fun newInstance(reference: MessageReference): MessageViewPagerFragment {
            val fragment = MessageViewPagerFragment()
            val args = Bundle()
            args.putString(ARG_ACTIVE_MESSAGE, reference.toIdentityString())
            fragment.arguments = args
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        fragmentListener = try {
            context as MessageViewPagerFragmentListener
        } catch (e: ClassCastException) {
            error("${context.javaClass} must implement MessageViewPagerFragmentListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val arguments = arguments
        if (arguments != null) {
            val reference = arguments.getString(ARG_ACTIVE_MESSAGE)
            if (reference != null && !reference.isEmpty()) {
                targetMessage = MessageReference.parse(reference)
                return
            }
        }
        targetMessage = null
        return
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val messageViewThemeResourceId = themeManager.messageViewThemeResourceId
        val context: Context = ContextThemeWrapper(inflater.context, messageViewThemeResourceId)
        val layoutInflater = LayoutInflater.from(context)
        val view = layoutInflater.inflate(R.layout.message_viewpager, container, false)
        viewPager = view.findViewById(R.id.viewPager)
        viewPager.isUserInputEnabled = true
        viewPager.offscreenPageLimit = ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT
        adapter = MessageViewPagerAdapter(this)
        viewPager.adapter = adapter
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(@ViewPager2.ScrollState state: Int) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    viewPagerSettled()
                }
                super.onPageScrollStateChanged(state)
            }
        })
        viewPager.addItemDecoration(DividerItemDecoration(this.context, DividerItemDecoration.HORIZONTAL))
        viewPager.post { tryShowTargetMessage() }
        return view
    }

    override fun onDestroy() {
        val arguments = arguments
        if (arguments != null) {
            var referenceId: String? = null
            val reference = getMessageReference(viewPager.currentItem)
            if (reference != null) {
                referenceId = reference.toIdentityString()
            }
            arguments.putString(ARG_ACTIVE_MESSAGE, referenceId)
        }
        super.onDestroy()
    }

    private fun viewPagerSettled() {
        viewPager.post {
            fragmentListener.configureMenu()
            adapter.resetWebView()
            val reference = getMessageReference(viewPager.currentItem)
            if (reference != null) {
                fragmentListener.scrollToMessage(reference)
                showedMessage = reference
            }
            activeMessageViewFragment?.setMessageViewed()
        }
    }

    val activeMessageViewFragment: MessageViewFragment?
        get() {
            return try {
                val reference = getMessageReference(getActivePosition())
                adapter.getMessageViewFragment(reference)
            } catch (e: Exception) {
                null
            }
        }

    @SuppressLint("NotifyDataSetChanged")
    fun onMessageListDirty() {
        if (targetMessage == null) {
            targetMessage = showedMessage
        }
        adapter.notifyDataSetChanged()
        tryShowTargetMessage()
    }

    private fun tryShowTargetMessage() {
        if (targetMessage != null) {
            val position = getMessagePosition(targetMessage!!)
            if (0 <= position) {
                viewPager.setCurrentItem(position, false)
                viewPagerSettled()
                targetMessage = null
            }
        }
    }

    fun showPreviousMessage(): Boolean {
        val position = viewPager.currentItem - 1
        return if (0 <= position) {
            viewPager.setCurrentItem(position, true)
            true
        } else {
            false
        }
    }

    fun showNextMessage(): Boolean {
        val position = viewPager.currentItem + 1
        return if (position < getMessageCount()) {
            viewPager.setCurrentItem(position, true)
            true
        } else {
            false
        }
    }

    fun getMessageCount(): Int {
        return try {
            fragmentListener.getMessageCount()
        } catch (e: Exception) {
            0
        }
    }

    private fun getMessagePosition(reference: MessageReference): Int {
        return try {
            fragmentListener.getMessagePosition(reference)
        } catch (e: Exception) {
            -1
        }
    }

    fun getMessageReference(position: Int): MessageReference? {
        return try {
            fragmentListener.getMessageReference(position)
        } catch (e: Exception) {
            null
        }
    }

    fun getActivePosition(): Int {
        return viewPager.currentItem
    }
}

interface MessageViewPagerFragmentListener {
    fun getMessageCount(): Int
    fun getMessagePosition(reference: MessageReference): Int
    fun getMessageReference(position: Int): MessageReference
    fun configureMenu()
    fun scrollToMessage(reference: MessageReference)
}
