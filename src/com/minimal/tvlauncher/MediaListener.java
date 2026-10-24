package com.minimal.tvlauncher;

import android.service.notification.NotificationListenerService;

/**
 * Does nothing itself. Android only lets an app see which music is playing (media
 * sessions) if the user has given it notification access, and that access is granted to
 * a notification listener like this one. Grant it with:
 *   adb shell cmd notification allow_listener com.minimal.tvlauncher/com.minimal.tvlauncher.MediaListener
 */
public class MediaListener extends NotificationListenerService {
}
