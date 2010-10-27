package com.fsck.k9.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.fsck.k9.K9;
import com.fsck.k9.mail.store.StorageManager;

public class StorageReceiver extends BroadcastReceiver
{

    @Override
    public void onReceive(final Context context, final Intent intent)
    {
        final String action = intent.getAction();
        final Uri uri = intent.getData();

        if (uri == null || uri.getPath() == null)
        {
            return;
        }

        if (K9.DEBUG)
        {
            Log.v(K9.LOG_TAG, "StorageReceiver: " + intent.toString());
        }

        final String path = uri.getPath();

        if (Intent.ACTION_MEDIA_EJECT.equals(action))
        {
            StorageManager.getInstance(K9.app).onBeforeUnmount(path);
        }
        else if (Intent.ACTION_MEDIA_MOUNTED.equals(action))
        {
            StorageManager.getInstance(K9.app).onMount(path,
                    intent.getBooleanExtra("read-only", true));
        }
        else if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action))
        {
            StorageManager.getInstance(K9.app).onAfterUnmount(path);
        }
    }

}
