package com.example.yjh.audiorecord.service;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bmob.BmobProFile;
import com.bmob.btp.callback.UploadListener;
import com.example.yjh.audiorecord.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import cn.bmob.v3.Bmob;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class MyIntentService extends IntentService {
    private static final String directory = Environment.getExternalStorageDirectory() + "/audiorecord/";
    private static final String fileNameA = "audiorecordByAudioRecord16bitmonoA.pcm";
    private static final String fileNameB = "audiorecordByAudioRecord16bitmonoB.pcm";
    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int UPLOAD_BEGIN = 3;
    private static final int UPLOAD_ING = 4;
    private static final int UPLOAD_END = 5;
    private static final int UPLOAD_ERROR = 6;
    private static String mFileName = null;
    int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
            RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
    NotificationManagerCompat notificationManager;
    NotificationCompat.Builder mBuilder;
    private AudioRecord recorder = null;
    private MediaRecorder mRecorder = null;
    private Thread recordingThread = null;
    private Thread recordingThread2 = null;
    private Boolean isRecording = false;
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPLOAD_BEGIN:
                    if (fileNameA.equalsIgnoreCase(directory + getmFileNameFromCache())) {
                        upload(directory + fileNameB);
                    } else {
                        upload(directory + fileNameA);
                    }
                    break;
                case UPLOAD_ING:
                    //    progressBar.setVisibility(View.VISIBLE);
                    //     progressBar.setProgress((Integer) msg.obj);
                    showNotification((Integer) msg.obj);
                    break;
                case UPLOAD_ERROR:
                    Log.i("MyIntentService", "UPLOAD_ERROR:" + String.valueOf(msg.obj));
                    showToast(String.valueOf(msg.arg1));
                    break;
            }
        }
    };

    public MyIntentService() {
        super("MyIntentService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Bmob.initialize(this, "94e36ad9769577ceb1bf3d9dc2e9c396");
        initmkdir();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            startRecordingByAudioRecord();
        }
    }

    private void initmkdir() {
        File file = new File(directory);
        file.mkdir();
    }

    private void startRecordingByAudioRecord() {
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING, bufferSize);
        recorder.startRecording();
        isRecording = true;
        writeAudioDataToFile();
    }

    private void writeAudioDataToFile() {
        byte[] sData = new byte[bufferSize];
        FileOutputStream os = null;
        int fileSize = 1024 << 10;
        Log.i("MyIntentService", "fileSize:" + String.valueOf(fileSize));
        try {
            File file = new File(directory, getmFileNameFromCache());
            os = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        int sizeFlag = 0;
        Message message = Message.obtain();
        while (isRecording) {
            if (sizeFlag > fileSize * 5) {
                try {
                    os.flush();
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                message = message.obtain();
                message.what = UPLOAD_BEGIN;
                handler.sendMessage(message);
                os = getFileOutputStreamChange();
                sizeFlag = 0;
            }
            // gets the voice output from microphone to byte format
            int bufferReaderResult = recorder.read(sData, 0, bufferSize);
            try {
                // // writes the data to file from buffer
                //    Log.i("MyIntentService", "bufferReaderResult:" + String.valueOf(bufferReaderResult));
                if (!isRecording) {
                    Log.i("MyIntentService", "break!");
                    break;
                }
                sizeFlag += bufferReaderResult;
                os.write(sData, 0, bufferReaderResult);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            if (os != null) os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getmFileNameFromCache() {
        return PreferenceManager.getDefaultSharedPreferences(this).getString("filename", fileNameA);
    }

    private FileOutputStream getFileOutputStreamChange() {
        FileOutputStream os = null;
        File file;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (fileNameA.equalsIgnoreCase(getmFileNameFromCache())) {
            file = new File(Environment.getExternalStorageDirectory() + "/audiorecord", fileNameB);
            sharedPreferences.edit().putString("filename", fileNameB).commit();
            Log.i("MyIntentService", "file change to B---------------------");
        } else {
            file = new File(Environment.getExternalStorageDirectory() + "/audiorecord", fileNameA);
            sharedPreferences.edit().putString("filename", fileNameA).commit();
            Log.i("MyIntentService", "file change to A----------------------");
        }
        Log.i("MyIntentService", "file " + String.valueOf(sharedPreferences.getString("filename", null)));
        try {
            os = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return os;
    }

    //bmob
    private void upload(String filePath) {
        BmobProFile.getInstance(MyIntentService.this).upload(filePath, new UploadListener() {
            Message message = Message.obtain();

            @Override
            public void onSuccess(String fileName, String url) {
                message = message.obtain();
                message.what = UPLOAD_END;
                handler.sendMessage(message);
            }

            @Override
            public void onProgress(int ratio) {
                message = message.obtain();
                message.what = UPLOAD_ING;
                message.obj = ratio;
                handler.sendMessage(message);
                Log.i("MyIntentService", " -onProgress :" + ratio);
            }

            @Override
            public void onError(int statuscode, String errormsg) {
                message = message.obtain();
                message.what = UPLOAD_ERROR;
                message.arg1 = statuscode;
                message.obj = errormsg;
                handler.sendMessage(message);
            }
        });
    }

    private void showNotification(int progress) {
        if (mBuilder == null) {
            CreateNotification();
        }
        if (progress < 100) {
            mBuilder.setOnlyAlertOnce(true); //重复notify也只会调用一次sound, vibrate and ticker
            //将setProgress的第三个参数indeterminate 设为true即可显示为无明确进度的进度条样式
            mBuilder.setProgress(100, progress, false);
            notificationManager.notify(1, mBuilder.build());
            //ongoing notification > regular notifications
        }
        if (progress == 100) {
            mBuilder.setOnlyAlertOnce(false)
                    .setTicker("Upload complete.");
            mBuilder.setContentTitle("Upload complete").setProgress(0, 0, false).setOngoing(false);
            //1.排序 default notification > ongoing notification > regular notifications
            //2.Ongoing notifications level do not have an 'X' close button, and are not affected by the "Clear all" button.
            notificationManager.notify(1, mBuilder.build());
        }
    }

    private void CreateNotification() {
        notificationManager = NotificationManagerCompat.from(this);
        mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.presence_online)
                .setContentTitle("Uploading")
                .setContentText("Hello World!")
                .setNumber(12)
                .setTicker("Begin to Upload...")
                .setAutoCancel(true);
        //         .setSound(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.fallbackring));//raw构建uri
        //获取assets 下的文件的流
        AssetManager assetManager = getAssets();
        //      InputStream inputStream = assetManager.open("filename");  //aset没有ID 直接用文件名，如果直接在assets下没有路径
        //获取raw 下的资源
        Resources res = getResources();
        //  XmlResourceParser getAnimation(int id);
        // InputStream openRawResource(int id)  //res用ID
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        mBuilder.setSound(alarmSound, AudioManager.STREAM_NOTIFICATION);//中音量控制键控制的音频流
        long[] vibrate = {0, 1000, 0, 0}; //等待 震动 等待 震动（毫秒）
        //   mBuilder.setVibrate(vibrate);
        mBuilder.setLights(Color.GREEN, 1000, 1000);
    }

    private void showToast(String mess) {
        Toast.makeText(this, mess, Toast.LENGTH_LONG).show();
    }


}
