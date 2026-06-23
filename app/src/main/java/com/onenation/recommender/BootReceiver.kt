package com.onenation.recommender
import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent
class BootReceiver : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent?) { if(i?.action==Intent.ACTION_BOOT_COMPLETED) RetryAlarmReceiver.schedule(c) } }
