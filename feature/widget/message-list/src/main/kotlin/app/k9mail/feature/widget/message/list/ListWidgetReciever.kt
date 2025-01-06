package app.k9mail.feature.widget.message.list

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.LinearLayout
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
import androidx.glance.GlanceComposable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.components.CircleIconButton
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.material3.ColorProviders
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.k9mail.core.ui.legacy.designsystem.atom.icon.Icons
import app.k9mail.legacy.account.Account.SortType
import app.k9mail.legacy.message.controller.MessageReference
import app.k9mail.legacy.search.SearchAccount
import app.k9mail.legacy.search.SearchAccount.Companion.createUnifiedInboxAccount
import com.fsck.k9.CoreResourceProvider
import com.fsck.k9.K9
import com.fsck.k9.activity.MessageCompose
import com.fsck.k9.activity.MessageList.Companion.intentDisplaySearch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MyAppWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MyAppWidget()
}



class MyAppWidget : GlanceAppWidget(), KoinComponent {

    private val messageListLoader: MessageListLoader by inject()
    private val coreResourceProvider: CoreResourceProvider by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {

        // In this method, load data needed to render the AppWidget.
        // Use `withContext` to switch to another thread for long running
        // operations.

        provideContent {
            var mails by remember { mutableStateOf(emptyList<MessageListItem>()) }

            LaunchedEffect(Unit) {
                val unifiedInboxSearch = SearchAccount.createUnifiedInboxAccount(
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
                mails = messageListLoader.getMessageList(messageListConfig)
            }

            GlanceTheme(GlanceTheme.colors) {
                Column(GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface)) {
                    Row(
                        GlanceModifier.padding(horizontal = 8.dp, vertical = 12.dp).fillMaxWidth().background(GlanceTheme.colors.primaryContainer)
                            .clickable {
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
                                )
                                PendingIntentCompat.getActivity(context, -1, intent, PendingIntent.FLAG_UPDATE_CURRENT, false)!!.send()
                            }
                    ) {
                        Text("Unified Inbox", style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 20.sp))
                        Spacer(GlanceModifier.defaultWeight())
                        Image(ImageProvider(Icons.Outlined.Edit), null, GlanceModifier.padding(2.dp).padding(end = 6.dp).clickable {
                            val intent = Intent(context, MessageCompose::class.java).apply {
                                action = MessageCompose.ACTION_COMPOSE
                            }
                            PendingIntentCompat.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT, false)!!.send()
                        }, colorFilter = ColorFilter.tint(GlanceTheme.colors.primary))
                    }
                    LazyColumn(GlanceModifier.fillMaxSize()) {
                        items(mails) {
                            Column {
                                ListItem(it)
                                Spacer(GlanceModifier.height(2.dp).fillMaxWidth().background(GlanceTheme.colors.surfaceVariant)
                                )
                            }
                        }
//                        ListItem(MessageListItem(
//                            "Wikipedia",
//                            "May 5",
//                            "Kinda long subject that should be long enough to exceed the available display space",
//                            "Towel Day is celebrated every year on 25 May as a tribute to the author Douglas Adams by his fans.",
//                            false,
//                            false,
//                            1,
//                            Color.Blue.hashCode(),
//                            MessageReference("", 0, ""),
//                            11,
//                            "",
//                            1,
//                            0,
//                            false,
//                            0
//                        ))
                    }
                }
            }
        }
    }
}

@Composable
private fun ListItem(item: MessageListItem) {
    Row(GlanceModifier.fillMaxWidth().wrapContentHeight()) {
        Spacer(GlanceModifier.width(8.dp).background(Color(item.accountColor)))
        Column(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp)) {
            Row(GlanceModifier.fillMaxWidth()) {
                Row(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Start) {
                    Text(item.subject, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp), maxLines = 1)
                }
                Spacer(GlanceModifier.width(4.dp))
                Row(horizontalAlignment = Alignment.End) {
                    Box(GlanceModifier.background(GlanceTheme.colors.primaryContainer).cornerRadius(8.dp).padding(2.dp)) {
                        Text(
                            item.threadCount.toString(),
                            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp)
                        )
                    }
                    Spacer(GlanceModifier.width(4.dp))
                    Text(item.displayDate, style = TextStyle(color = GlanceTheme.colors.primary))
                }
            }
            Spacer(GlanceModifier.height(2.dp))
            Row {
                Text(item.displayName, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 15.sp), maxLines = 1)
            }
            Spacer(GlanceModifier.height(2.dp))
            Row {
                Text(item.preview, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp), maxLines = 1)
            }
        }
    }
}
