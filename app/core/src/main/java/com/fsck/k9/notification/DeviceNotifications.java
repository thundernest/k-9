package com.fsck.k9.notification;


import java.util.ArrayList;
import java.util.List;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.support.v4.app.NotificationCompat.InboxStyle;

import com.fsck.k9.Account;
import com.fsck.k9.K9;
import com.fsck.k9.K9.NotificationHideSubject;
import com.fsck.k9.K9.NotificationQuickMoveType;
import com.fsck.k9.NotificationSetting;
import com.fsck.k9.controller.MessageReference;

import static com.fsck.k9.notification.NotificationHelper.NOTIFICATION_LED_BLINK_SLOW;
import static com.fsck.k9.notification.NotificationController.platformSupportsExtendedNotifications;


class DeviceNotifications extends BaseNotifications {
    private final WearNotifications wearNotifications;
    private final LockScreenNotification lockScreenNotification;


    DeviceNotifications(NotificationHelper notificationHelper, NotificationActionCreator actionCreator,
            LockScreenNotification lockScreenNotification, WearNotifications wearNotifications,
            NotificationResourceProvider resourceProvider) {
        super(notificationHelper, actionCreator, resourceProvider);
        this.wearNotifications = wearNotifications;
        this.lockScreenNotification = lockScreenNotification;
    }

    public Notification buildSummaryNotification(Account account, NotificationData notificationData,
            boolean silent) {
        int unreadMessageCount = notificationData.getUnreadMessageCount();

        NotificationCompat.Builder builder;
        if (isPrivacyModeActive() || !platformSupportsExtendedNotifications()) {
            builder = createSimpleSummaryNotification(account, unreadMessageCount);
        } else if (notificationData.isSingleMessageNotification()) {
            NotificationHolder holder = notificationData.getHolderForLatestNotification();
            builder = createBigTextStyleSummaryNotification(account, holder);
        } else {
            builder = createInboxStyleSummaryNotification(account, notificationData, unreadMessageCount);
        }

        if (notificationData.containsStarredMessages()) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        }

        int notificationId = NotificationIds.getNewMailSummaryNotificationId(account);
        PendingIntent deletePendingIntent = actionCreator.createDismissAllMessagesPendingIntent(
                account, notificationId);
        builder.setDeleteIntent(deletePendingIntent);

        lockScreenNotification.configureLockScreenNotification(builder, notificationData);

        boolean ringAndVibrate = false;
        if (!silent && !account.isRingNotified()) {
            account.setRingNotified(true);
            ringAndVibrate = true;
        }

        NotificationSetting notificationSetting = account.getNotificationSetting();
        notificationHelper.configureNotification(
                builder,
                (notificationSetting.isRingEnabled()) ? notificationSetting.getRingtone() : null,
                (notificationSetting.isVibrateEnabled()) ? notificationSetting.getVibration() : null,
                (notificationSetting.isLedEnabled()) ? notificationSetting.getLedColor() : null,
                NOTIFICATION_LED_BLINK_SLOW,
                ringAndVibrate);

        return builder.build();
    }

    private NotificationCompat.Builder createSimpleSummaryNotification(Account account, int unreadMessageCount) {
        String accountName = notificationHelper.getAccountName(account);
        CharSequence newMailText = resourceProvider.newMailTitle();
        String unreadMessageCountText = resourceProvider.newMailUnreadMessageCount(unreadMessageCount, accountName);

        int notificationId = NotificationIds.getNewMailSummaryNotificationId(account);
        PendingIntent contentIntent = actionCreator.createViewFolderListPendingIntent(account, notificationId);

        return createAndInitializeNotificationBuilder(account)
                .setNumber(unreadMessageCount)
                .setTicker(newMailText)
                .setContentTitle(unreadMessageCountText)
                .setContentText(newMailText)
                .setContentIntent(contentIntent);
    }

    private NotificationCompat.Builder createBigTextStyleSummaryNotification(Account account,
            NotificationHolder holder) {

        int notificationId = NotificationIds.getNewMailSummaryNotificationId(account);
        Builder builder = createBigTextStyleNotification(account, holder, notificationId)
                .setGroupSummary(true);

        NotificationContent content = holder.content;
        addReplyAction(builder, content, notificationId);
        addMarkAsReadAction(builder, content, notificationId);
        addQuickMoveAction(account, builder, content, notificationId);

        return builder;
    }

    private NotificationCompat.Builder createInboxStyleSummaryNotification(Account account,
            NotificationData notificationData, int unreadMessageCount) {

        NotificationHolder latestNotification = notificationData.getHolderForLatestNotification();

        int newMessagesCount = notificationData.getNewMessagesCount();
        String accountName = notificationHelper.getAccountName(account);
        String title = resourceProvider.newMessagesTitle(newMessagesCount);
        String summary = (notificationData.hasSummaryOverflowMessages()) ?
                resourceProvider.additionalMessages(notificationData.getSummaryOverflowMessagesCount(), accountName) :
                accountName;
        String groupKey = NotificationGroupKeys.getGroupKey(account);

        NotificationCompat.Builder builder = createAndInitializeNotificationBuilder(account)
                .setNumber(unreadMessageCount)
                .setTicker(latestNotification.content.summary)
                .setGroup(groupKey)
                .setGroupSummary(true)
                .setContentTitle(title)
                .setSubText(accountName);

        NotificationCompat.InboxStyle style = createInboxStyle(builder)
                .setBigContentTitle(title)
                .setSummaryText(summary);

        for (NotificationContent content : notificationData.getContentForSummaryNotification()) {
            style.addLine(content.summary);
        }

        builder.setStyle(style);

        addMarkAllAsReadAction(builder, notificationData);
        addQuickMoveAllAction(builder, notificationData);

        wearNotifications.addSummaryActions(builder, notificationData);

        int notificationId = NotificationIds.getNewMailSummaryNotificationId(account);
        List<MessageReference> messageReferences = notificationData.getAllMessageReferences();
        PendingIntent contentIntent = actionCreator.createViewMessagesPendingIntent(
                account, messageReferences, notificationId);
        builder.setContentIntent(contentIntent);

        return builder;
    }

    private void addMarkAsReadAction(Builder builder, NotificationContent content, int notificationId) {
        int icon = resourceProvider.getIconMarkAsRead();
        String title = resourceProvider.actionMarkAsRead();


        MessageReference messageReference = content.messageReference;
        PendingIntent action = actionCreator.createMarkMessageAsReadPendingIntent(messageReference, notificationId);

        builder.addAction(icon, title, action);
    }

    private void addMarkAllAsReadAction(Builder builder, NotificationData notificationData) {
        int icon = resourceProvider.getIconMarkAsRead();
        String title = resourceProvider.actionMarkAsRead();

        Account account = notificationData.getAccount();
        ArrayList<MessageReference> messageReferences = notificationData.getAllMessageReferences();
        int notificationId = NotificationIds.getNewMailSummaryNotificationId(account);
        PendingIntent markAllAsReadPendingIntent =
                actionCreator.createMarkAllAsReadPendingIntent(account, messageReferences, notificationId);

        builder.addAction(icon, title, markAllAsReadPendingIntent);
    }

    private void addQuickMoveAllAction(Builder builder, NotificationData notificationData) {
        if (K9.getNotificationQuickMoveTrigger() != K9.NotificationQuickMoveTrigger.ALWAYS) {
            return;
        }
        NotificationQuickMoveType quickMoveType = K9.getNotificationQuickMoveType();
        if(NotificationQuickMoveType.DELETE == quickMoveType) {
            addDeleteAllAction(builder, notificationData);
        }
        if(NotificationQuickMoveType.ARCHIVE == quickMoveType) {
            addArchiveAllAction(builder, notificationData);
        }
    }

    private void addDeleteAllAction(Builder builder, NotificationData notificationData) {
        int icon = resourceProvider.getIconDelete();
        String title = resourceProvider.actionDelete();

        Account account = notificationData.getAccount();
        int notificationId = NotificationIds.getNewMailSummaryNotificationId(account);
        ArrayList<MessageReference> messageReferences = notificationData.getAllMessageReferences();
        PendingIntent action = actionCreator.createDeleteAllPendingIntent(account, messageReferences, notificationId);

        builder.addAction(icon, title, action);
    }

    private void addArchiveAllAction(Builder builder, NotificationData notificationData) {
        Account account = notificationData.getAccount();
        if(!isArchiveActionAvailable(account))
        {
            return;
        }

        int icon = resourceProvider.getIconArchive();
        String title = resourceProvider.actionArchive();

        int notificationId = NotificationIds.getNewMailSummaryNotificationId(account);
        ArrayList<MessageReference> messageReferences = notificationData.getAllMessageReferences();
        PendingIntent action = actionCreator.createArchiveAllPendingIntent(account, messageReferences, notificationId);

        builder.addAction(icon, title, action);
    }

    private void addReplyAction(Builder builder, NotificationContent content, int notificationId) {
        int icon = resourceProvider.getIconReply();
        String title = resourceProvider.actionReply();

        MessageReference messageReference = content.messageReference;
        PendingIntent replyToMessagePendingIntent =
                actionCreator.createReplyPendingIntent(messageReference, notificationId);

        builder.addAction(icon, title, replyToMessagePendingIntent);
    }

    private boolean isPrivacyModeActive() {
        KeyguardManager keyguardService = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);

        boolean privacyModeAlwaysEnabled = K9.getNotificationHideSubject() == NotificationHideSubject.ALWAYS;
        boolean privacyModeEnabledWhenLocked = K9.getNotificationHideSubject() == NotificationHideSubject.WHEN_LOCKED;
        boolean screenLocked = keyguardService.inKeyguardRestrictedInputMode();

        return privacyModeAlwaysEnabled || (privacyModeEnabledWhenLocked && screenLocked);
    }

    protected InboxStyle createInboxStyle(Builder builder) {
        return new InboxStyle(builder);
    }
}
