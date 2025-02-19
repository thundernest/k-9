package app.k9mail.feature.widget.message.list

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.PendingIntentCompat
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.k9mail.core.ui.legacy.designsystem.atom.icon.Icons
import app.k9mail.legacy.account.Account.SortType
import app.k9mail.legacy.search.SearchAccount.Companion.createUnifiedInboxAccount
import com.fsck.k9.CoreResourceProvider
import com.fsck.k9.K9
import com.fsck.k9.activity.MessageCompose
import com.fsck.k9.activity.MessageList
import com.fsck.k9.activity.MessageList.Companion.intentDisplaySearch
import kotlin.random.Random.Default.nextInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MyAppWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MyAppWidget()
}


class MyAppWidget : GlanceAppWidget(), KoinComponent {

    private val messageListLoader: MessageListLoader by inject()
    private val coreResourceProvider: CoreResourceProvider by inject()
    companion object {
        private var lastMailList = emptyList<MessageListItem>()
        private const val MESSAGE_COUNT = 100
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // In this method, load data needed to render the AppWidget.
        // Use `withContext` to switch to another thread for long running
        // operations.
        provideContent {
            var mails by remember { mutableStateOf(lastMailList) }

            LaunchedEffect(Unit) {
               CoroutineScope(Dispatchers.IO).launch {
                    val unifiedInboxSearch = createUnifiedInboxAccount(
                        unifiedInboxTitle = coreResourceProvider.searchUnifiedInboxTitle(),
                        unifiedInboxDetail = coreResourceProvider.searchUnifiedInboxDetail(),
                    ).relatedSearch
                    val messageListConfig = MessageListConfig(
                        search = unifiedInboxSearch,
                        showingThreadedList = K9.isThreadedViewEnabled,
                        sortType = SortType.SORT_DATE,
                        sortAscending = false,
                        sortDateAscending = false,
                    )
                    val list = messageListLoader.getMessageList(messageListConfig)
                    mails = list.subList(0, list.size.coerceAtMost(MESSAGE_COUNT))
                    lastMailList = mails
                }
            }

            WidgetContent(mails)
        }
    }

    @Composable
    private fun WidgetContent(mails: List<MessageListItem>) {
        val context = LocalContext.current
        GlanceTheme(GlanceTheme.colors) {
            Column(GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface)) {
                Row(
                    GlanceModifier.padding(horizontal = 8.dp, vertical = 12.dp).fillMaxWidth()
                        .background(GlanceTheme.colors.primaryContainer)
                        .clickable {
                            openApp(context)
                        },
                ) {
                    Text("Unified Inbox", style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 20.sp))
                    Spacer(GlanceModifier.defaultWeight())
                    Image(
                        ImageProvider(Icons.Outlined.Edit),
                        context.getString(R.string.message_list_widget_compose_action),
                        GlanceModifier.padding(2.dp).padding(end = 6.dp).clickable {
                            val intent = Intent(context, MessageCompose::class.java).apply {
                                action = MessageCompose.ACTION_COMPOSE
                            }
                            PendingIntentCompat.getActivity(
                                context,
                                nextInt(),
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT,
                                false,
                            )!!.send()
                        },
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    )
                }
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(mails) {
                        Column {
                            ListItem(it)
                            Spacer(
                                GlanceModifier.height(2.dp).fillMaxWidth()
                                    .background(GlanceTheme.colors.surfaceVariant),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun openApp(context: Context) {
        val unifiedInboxAccount = createUnifiedInboxAccount(
            unifiedInboxTitle = coreResourceProvider.searchUnifiedInboxTitle(),
            unifiedInboxDetail = coreResourceProvider.searchUnifiedInboxDetail(),
        )
        val intent = intentDisplaySearch(
            context = context,
            search = unifiedInboxAccount.relatedSearch,
            noThreading = true,
            newTask = true,
            clearTop = true,
        ).apply {
            action = nextInt().toString()
        }
        PendingIntentCompat.getActivity(
            context,
            nextInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT,
            false,
        )!!.send()
    }
}

@Composable
private fun ListItem(item: MessageListItem) {
    val context = LocalContext.current
    Row(
        GlanceModifier.fillMaxWidth().wrapContentHeight().clickable {
            CoroutineScope(Dispatchers.IO).launch {
                val intent = MessageList.actionDisplayMessageIntent(context, item.messageReference)
                PendingIntentCompat.getActivity(context, nextInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT, false)!!
                    .send()
            }
        },
    ) {
        Spacer(GlanceModifier.width(8.dp).background(Color(item.accountColor)))
        Column(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp)) {
            Row(GlanceModifier.fillMaxWidth()) {
                Row(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Start) {
                    Text(
                        item.subject,
                        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.width(4.dp))
                Row(horizontalAlignment = Alignment.End) {
                    Box(
                        GlanceModifier.background(GlanceTheme.colors.primaryContainer).cornerRadius(8.dp).padding(2.dp),
                    ) {
                        Text(
                            item.threadCount.toString(),
                            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp),
                        )
                    }
                    Spacer(GlanceModifier.width(4.dp))
                    Text(item.displayDate, style = TextStyle(color = GlanceTheme.colors.primary))
                }
            }
            Spacer(GlanceModifier.height(2.dp))
            Row {
                Text(
                    item.displayName,
                    style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 15.sp),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(2.dp))
            Row {
                Text(
                    item.preview,
                    style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp),
                    maxLines = 1,
                )
            }
        }
    }
}
