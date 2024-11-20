package com.fsck.k9.ui.messagelist

import android.content.Context
import android.text.Spannable
import android.text.style.AbsoluteSizeSpan
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import app.k9mail.core.android.testing.RobolectricTest
import app.k9mail.core.testing.TestClock
import app.k9mail.legacy.account.Account
import assertk.Assert
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.support.expected
import com.fsck.k9.FontSizes
import com.fsck.k9.FontSizes.Companion.FONT_DEFAULT
import com.fsck.k9.FontSizes.Companion.LARGE
import com.fsck.k9.UiDensity
import com.fsck.k9.contacts.ContactPictureLoader
import com.fsck.k9.mail.Address
import com.fsck.k9.ui.R
import com.fsck.k9.ui.helper.RelativeDateTimeFormatter
import com.google.android.material.textview.MaterialTextView
import org.junit.Test
import org.mockito.kotlin.mock
import org.robolectric.Robolectric

private const val SOME_ACCOUNT_UUID = "6b84207b-25de-4dab-97c3-953bbf03fec6"
private const val FIRST_LINE_DEFAULT_FONT_SIZE = 16f
private const val SECOND_LINE_DEFAULT_FONT_SIZE = 14f
private const val DATE_DEFAULT_FONT_SIZE = 14f

class MessageListAdapterTest : RobolectricTest() {
    val activity = Robolectric.buildActivity(AppCompatActivity::class.java).create().get()
    val context: Context = ContextThemeWrapper(activity, com.google.android.material.R.style.Theme_Material3_Light)

    val contactsPictureLoader: ContactPictureLoader = mock()
    val listItemListener: MessageListItemActionListener = mock()

    @Test
    fun withShowAccountChip_shouldShowAccountChip() {
        val adapter = createAdapter(showAccountChip = true)

        val view = adapter.createAndBindView()

        assertThat(view.accountChipView).isVisible()
    }

    @Test
    fun withoutShowAccountChip_shouldHideAccountChip() {
        val adapter = createAdapter(showAccountChip = false)

        val view = adapter.createAndBindView()

        assertThat(view.accountChipView).isGone()
    }

    @Test
    fun withoutStars_shouldHideStarView() {
        val adapter = createAdapter(stars = false)

        val view = adapter.createAndBindView()

        assertThat(view.starView).isGone()
    }

    @Test
    fun withStars_shouldShowStarView() {
        val adapter = createAdapter(stars = true)

        val view = adapter.createAndBindView()

        assertThat(view.starView).isVisible()
    }

    @Test
    fun withStarsAndStarredMessage_shouldSetStarViewToSelected() {
        val adapter = createAdapter(stars = true)
        val messageListItem = createMessageListItem(isStarred = true)

        val view = adapter.createAndBindView(messageListItem)

        assertThat(view.starView).isSelected()
    }

    @Test
    fun withStarsAndUnstarredMessage_shouldNotSetStarViewToSelected() {
        val adapter = createAdapter(stars = true)
        val messageListItem = createMessageListItem(isStarred = false)

        val view = adapter.createAndBindView(messageListItem)

        assertThat(view.starView).isNotSelected()
    }

    @Test
    fun withoutShowContactPicture_shouldHideContactPictureView() {
        val adapter = createAdapter(showContactPicture = false)

        val view = adapter.createAndBindView()

        assertThat(view.contactPictureContainerView).isGone()
    }

    @Test
    fun withShowContactPicture_shouldShowContactPictureView() {
        val adapter = createAdapter(showContactPicture = true)

        val view = adapter.createAndBindView()

        assertThat(view.contactPictureContainerView).isVisible()
    }

    @Test
    fun withThreadCountOne_shouldHideThreadCountView() {
        val adapter = createAdapter()
        val messageListItem = createMessageListItem(threadCount = 1)

        val view = adapter.createAndBindView(messageListItem)

        assertThat(view.threadCountView).isGone()
    }

    @Test
    fun withThreadCountGreaterOne_shouldShowThreadCountViewWithExpectedValue() {
        val adapter = createAdapter()
        val messageListItem = createMessageListItem(threadCount = 13)

        val view = adapter.createAndBindView(messageListItem)

        assertThat(view.threadCountView).isVisible()
        assertThat(view.threadCountView.textString).isEqualTo("13")
    }

    @Test
    fun withoutSenderAboveSubject_shouldShowSubjectInFirstLine() {
        val adapter = createAdapter(senderAboveSubject = false)
        val messageListItem = createMessageListItem(subject = "Subject")

        val view = adapter.createAndBindView(messageListItem)

        assertThat(view.firstLineView.textString).isEqualTo("Subject")
    }

    @Test
    fun withSenderAboveSubject_shouldShowDisplayNameInFirstLine() {
        val adapter = createAdapter(senderAboveSubject = true)
        val messageListItem = createMessageListItem(displayName = "Display Name")

        val view = adapter.createAndBindView(messageListItem)

        assertThat(view.firstLineView.textString).isEqualTo("Display Name")
    }

    @Test
    fun withoutSenderAboveSubjectAndZeroPreviewLines_shouldShowDisplayNameInSecondLine() {
        val adapter = createAdapter(senderAboveSubject = false, previewLines = 0)
        val messageListItem = createMessageListItem(displayName = "Display Name")

        val view = adapter.createAndBindView(messageListItem)

        assertThat(view.secondLineView.textString).isEqualTo("Display Name")
    }

    @Test
    fun withoutSenderAboveSubjectAndPreviewLines_shouldShowDisplayNameAndPreviewInSecondLine() {
        val adapter = createAdapter(senderAboveSubject = false, previewLines = 1)
        val messageListItem = createMessageListItem(displayName = "Display Name", previewText = "Preview")

        val view = adapter.createAndBindView(messageListItem)

        assertThat(view.secondLineView.textString).isEqualTo(secondLine("Display Name", "Preview"))
    }

    @Test
    fun withSenderAboveSubjectAndZeroPreviewLines_shouldShowSubjectInSecondLine() {
        val adapter = createAdapter(senderAboveSubject = true, previewLines = 0)
        val messageListItem = createMessageListItem(subject = "Subject")

        val view = adapter.createAndBindView(messageListItem)

        assertThat(view.secondLineView.textString).isEqualTo("Subject")
    }

    @Test
    fun withSenderAboveSubjectAndPreviewLines_shouldShowSubjectAndPreviewInSecondLine() {
        val adapter = createAdapter(senderAboveSubject = true, previewLines = 1)
        val messageListItem = createMessageListItem(subject = "Subject", previewText = "Preview")

        val view = adapter.createAndBindView(messageListItem)

        assertThat(view.secondLineView.textString).isEqualTo(secondLine("Subject", "Preview"))
    }

    @Test
    fun withMissingSubject_shouldDisplayNoSubjectIndicator() {
        val adapter = createAdapter(senderAboveSubject = false)
        val messageListItem = createMessageListItem(subject = null)

        val view = adapter.createAndBindView(messageListItem)

        assertThat(view.firstLineView.textString).isMissingSubjectText()
    }

    @Test
    fun withEmptySubject_shouldDisplayNoSubjectIndicator() {
        val adapter = createAdapter(senderAboveSubject = false)
        val messageListItem = createMessageListItem(subject = "")

        val view = adapter.createAndBindView(messageListItem)

        assertThat(view.firstLineView.textString).isMissingSubjectText()
    }

    @Test
    fun withoutAttachments_shouldHideAttachmentCountView() {
        val adapter = createAdapter()
        val messageListItem = createMessageListItem(hasAttachments = false)

        val view = adapter.createAndBindView(messageListItem)

        assertThat(view.attachmentCountView).isGone()
    }

    @Test
    fun withAttachments_shouldShowAttachmentCountView() {
        val adapter = createAdapter()
        val messageListItem = createMessageListItem(hasAttachments = true)

        val view = adapter.createAndBindView(messageListItem)

        assertThat(view.attachmentCountView).isVisible()
    }

    @Test
    fun withoutSenderAboveSubjectAndDefaultFontSize_shouldNotSetTextSizeOfFirstLineView() {
        val adapter = createAdapter(
            fontSizes = createFontSizes(subject = FONT_DEFAULT),
            senderAboveSubject = false,
        )

        val view = adapter.createAndBindView()

        assertThat(view.firstLineView.textSize).isEqualTo(FIRST_LINE_DEFAULT_FONT_SIZE)
    }

    @Test
    fun withoutSenderAboveSubjectAndNonDefaultFontSize_shouldSetTextSizeOfFirstLineView() {
        val adapter = createAdapter(
            fontSizes = createFontSizes(subject = LARGE),
            senderAboveSubject = false,
        )

        val view = adapter.createAndBindView()

        assertThat(view.firstLineView.textSize).isEqualTo(22f)
    }

    @Test
    fun withSenderAboveSubjectAndDefaultFontSize_shouldNotSetTextSizeOfFirstLineView() {
        val adapter = createAdapter(
            fontSizes = createFontSizes(sender = FONT_DEFAULT),
            senderAboveSubject = true,
        )

        val view = adapter.createAndBindView()

        assertThat(view.firstLineView.textSize).isEqualTo(FIRST_LINE_DEFAULT_FONT_SIZE)
    }

    @Test
    fun withSenderAboveSubjectAndNonDefaultFontSize_shouldSetTextSizeOfFirstLineView() {
        val adapter = createAdapter(
            fontSizes = createFontSizes(sender = LARGE),
            senderAboveSubject = true,
        )

        val view = adapter.createAndBindView()

        assertThat(view.firstLineView.textSize).isEqualTo(22f)
    }

    @Test
    fun withoutSenderAboveSubjectAndDefaultFontSize_shouldNotSetTextSizeSpanInSecondLineView() {
        val adapter = createAdapter(
            fontSizes = createFontSizes(sender = FONT_DEFAULT),
            senderAboveSubject = false,
        )

        val view = adapter.createAndBindView()

        assertThat(view.secondLineView.text).firstAbsoluteSizeSpanValue().isNull()
    }

    @Test
    fun withoutSenderAboveSubjectAndNonDefaultFontSize_shouldSetTextSizeSpanInSecondLineView() {
        val adapter = createAdapter(
            fontSizes = createFontSizes(sender = LARGE),
            senderAboveSubject = false,
        )

        val view = adapter.createAndBindView()

        assertThat(view.secondLineView.text).firstAbsoluteSizeSpanValue().isEqualTo(22)
    }

    @Test
    fun withSenderAboveSubjectAndDefaultFontSize_shouldNotSetTextSizeSpanInSecondLineView() {
        val adapter = createAdapter(
            fontSizes = createFontSizes(subject = FONT_DEFAULT),
            senderAboveSubject = true,
        )

        val view = adapter.createAndBindView()

        assertThat(view.secondLineView.text).firstAbsoluteSizeSpanValue().isNull()
    }

    @Test
    fun withSenderAboveSubjectAndNonDefaultFontSize_shouldSetTextSizeSpanInSecondLineView() {
        val adapter = createAdapter(
            fontSizes = createFontSizes(subject = LARGE),
            senderAboveSubject = true,
        )

        val view = adapter.createAndBindView()

        assertThat(view.secondLineView.text).firstAbsoluteSizeSpanValue().isEqualTo(22)
    }

    @Test
    fun dateWithDefaultFontSize_shouldNotSetTextSizeOfDateView() {
        val adapter = createAdapter(fontSizes = createFontSizes(date = FONT_DEFAULT))

        val view = adapter.createAndBindView()

        assertThat(view.dateView.textSize).isEqualTo(DATE_DEFAULT_FONT_SIZE)
    }

    @Test
    fun dateWithNonDefaultFontSize_shouldSetTextSizeOfDateView() {
        val adapter = createAdapter(fontSizes = createFontSizes(date = LARGE))

        val view = adapter.createAndBindView()

        assertThat(view.dateView.textSize).isEqualTo(22f)
    }

    @Test
    fun previewWithDefaultFontSize_shouldNotSetTextSizeOfSecondLineView() {
        val adapter = createAdapter(
            fontSizes = createFontSizes(preview = FONT_DEFAULT),
            previewLines = 1,
        )

        val view = adapter.createAndBindView()

        assertThat(view.secondLineView.textSize).isEqualTo(SECOND_LINE_DEFAULT_FONT_SIZE)
    }

    @Test
    fun previewWithNonDefaultFontSize_shouldSetTextSizeOfSecondLineView() {
        val adapter = createAdapter(
            fontSizes = createFontSizes(preview = LARGE),
            previewLines = 1,
        )

        val view = adapter.createAndBindView()

        assertThat(view.secondLineView.textSize).isEqualTo(22f)
    }

    fun createFontSizes(
        subject: Int = FONT_DEFAULT,
        sender: Int = FONT_DEFAULT,
        preview: Int = FONT_DEFAULT,
        date: Int = FONT_DEFAULT,
    ): FontSizes {
        return FontSizes().apply {
            messageListSubject = subject
            messageListSender = sender
            messageListPreview = preview
            messageListDate = date
        }
    }

    fun createAdapter(
        fontSizes: FontSizes = createFontSizes(),
        previewLines: Int = 0,
        stars: Boolean = true,
        senderAboveSubject: Boolean = false,
        showContactPicture: Boolean = true,
        showingThreadedList: Boolean = true,
        backGroundAsReadIndicator: Boolean = false,
        showAccountChip: Boolean = false,
        density: UiDensity = UiDensity.Default,
    ): MessageListAdapter {
        val appearance = MessageListAppearance(
            fontSizes,
            previewLines,
            stars,
            senderAboveSubject,
            showContactPicture,
            showingThreadedList,
            backGroundAsReadIndicator,
            showAccountChip,
            density,
        )

        return MessageListAdapter(
            theme = context.theme,
            res = context.resources,
            layoutInflater = LayoutInflater.from(context),
            contactsPictureLoader = contactsPictureLoader,
            listItemListener = listItemListener,
            appearance = appearance,
            relativeDateTimeFormatter = RelativeDateTimeFormatter(context, TestClock()),
        )
    }

    fun createMessageListItem(
        account: Account = Account(SOME_ACCOUNT_UUID),
        subject: String? = "irrelevant",
        threadCount: Int = 0,
        messageDate: Long = 0L,
        internalDate: Long = 0L,
        displayName: CharSequence = "irrelevant",
        displayAddress: Address? = Address.parse("irrelevant@domain.example").first(),
        previewText: String = "irrelevant",
        isMessageEncrypted: Boolean = false,
        isRead: Boolean = false,
        isStarred: Boolean = false,
        isAnswered: Boolean = false,
        isForwarded: Boolean = false,
        hasAttachments: Boolean = false,
        uniqueId: Long = 0L,
        folderId: Long = 0L,
        messageUid: String = "irrelevant",
        databaseId: Long = 0L,
        threadRoot: Long = 0L,
    ): MessageListItem {
        return MessageListItem(
            account,
            subject,
            threadCount,
            messageDate,
            internalDate,
            displayName,
            displayAddress,
            previewText,
            isMessageEncrypted,
            isRead,
            isStarred,
            isAnswered,
            isForwarded,
            hasAttachments,
            uniqueId,
            folderId,
            messageUid,
            databaseId,
            threadRoot,
        )
    }

    fun MessageListAdapter.createAndBindView(item: MessageListItem = createMessageListItem()): View {
        messages = listOf(item)
        val holder = onCreateViewHolder(LinearLayout(context), 0)
        onBindViewHolder(holder, 0)
        return holder.itemView
    }

    fun secondLine(senderOrSubject: String, preview: String) = "$senderOrSubject – $preview"

    val View.accountChipView: View get() = findViewById(R.id.account_color_chip)
    val View.starView: View get() = findViewById(R.id.star)
    val View.contactPictureContainerView: View get() = findViewById(R.id.contact_picture_click_area)
    val View.threadCountView: MaterialTextView get() = findViewById(R.id.thread_count)
    val View.firstLineView: MaterialTextView get() = findViewById(R.id.subject)
    val View.secondLineView: MaterialTextView get() = findViewById(R.id.preview)
    val View.attachmentCountView: View get() = findViewById(R.id.attachment)
    val View.dateView: MaterialTextView get() = findViewById(R.id.date)

    private fun Assert<View>.isVisible() = given { actual ->
        if (!actual.isVisible) {
            expected("View to be visible ($actual)")
        }
    }

    private fun Assert<View>.isGone() = given { actual ->
        if (!actual.isGone) {
            expected("View to be gone ($actual)")
        }
    }

    private fun Assert<View>.isSelected() = given { actual ->
        if (!actual.isSelected) {
            expected("View to be selected ($actual)")
        }
    }

    private fun Assert<View>.isNotSelected() = given { actual ->
        if (actual.isSelected) {
            expected("View to not be selected ($actual)")
        }
    }

    private fun Assert<CharSequence>.isMissingSubjectText() {
        isEqualTo(context.getString(R.string.general_no_subject))
    }

    private fun Assert<CharSequence>.firstAbsoluteSizeSpanValue() = transform { text ->
        val spans = (text as Spannable).getSpans(0, text.length, AbsoluteSizeSpan::class.java)
        spans.firstOrNull()?.size
    }

    private val MaterialTextView.textString: String
        get() = text.toString()
}
