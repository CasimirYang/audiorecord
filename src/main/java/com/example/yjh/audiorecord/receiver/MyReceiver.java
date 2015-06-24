package com.example.yjh.audiorecord.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.yjh.audiorecord.activity.MainActivity;
import com.example.yjh.audiorecord.service.MyIntentService;

public class MyReceiver extends BroadcastReceiver {
    public MyReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        //context.startActivity(new Intent(context,MainActivity.class));
        context.startService(new Intent(context, MyIntentService.class));
    }
}
