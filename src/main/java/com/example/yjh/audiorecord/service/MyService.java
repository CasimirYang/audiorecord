package com.example.yjh.audiorecord.service;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
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
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.widget.Toast;

import com.bmob.BmobProFile;
import com.bmob.btp.callback.UploadListener;
import com.example.yjh.audiorecord.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Timer;

import cn.bmob.v3.Bmob;

public class MyService extends Service {
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
    private static final int CAN_UPLOAD = 7;
    private static boolean threadFlag = false;
    private static String mFileName = null;
    private static int maxFileSize = 1024 * 100; //12M, 2 hours
    int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
            RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
    NotificationManagerCompat notificationManager;
    NotificationCompat.Builder mBuilder;
    Handler uploadHandler;
    private AudioRecord recorder = null;
    private MediaRecorder mRecorder = null;
    private Thread recordingThread = null;
    private Thread recordingThread2 = null;
    private String type = null;
    private Boolean isRecording = false;
    //  private static int maxFileSize = 1048576*12; //12M, 2 hours
    private String currentFile = null;
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
                    //  showNotification((Integer) msg.obj);
                    break;
                case UPLOAD_ERROR:
                    Log.i("MyService", "UPLOAD_ERROR:" + String.valueOf(msg.obj));
                    showToast(String.valueOf(msg.arg1));
                    break;
                case UPLOAD_END:
                    Log.i("MyService", "UPLOAD_END:" + String.valueOf(msg.obj));
                    //   updateUploadSituation(String.valueOf(msg.obj));
            }
        }
    };
    private android.media.MediaRecorder.OnInfoListener infoListener = new MediaRecorder.OnInfoListener() {
        @Override
        public void onInfo(MediaRecorder mr, int what, int extra) {
            Log.i("MyService", " -11111i1nfoListener111111111 :" + Thread.currentThread().getId());
            switch (what) {
                case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
                    Log.i("MyService", " -infoListener :" + Thread.currentThread().getId());
                    startRecordingByMediaRecord(false);
                    break;
                case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
                    Log.i("MyService", " -infoListener :" + Thread.currentThread().getId());
                    uploadHandler.sendEmptyMessage(CAN_UPLOAD);
                    startRecordingByMediaRecord(false);
                    break;
            }
        }
    };

    public MyService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Bmob.initialize(this, "bcea8d4327d6fcaa3ecbd0153b5efaa5");
        initmkdir();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MyService.this);
        type = sharedPreferences.getString("type", "media");
        Set<String> set = new LinkedHashSet();
        sharedPreferences.edit().putStringSet("uploadSet", set).commit();
     /*   Set<String> set = sharedPreferences.getStringSet("uploadSet", null);
        if( set == null ){
            set = new LinkedHashSet();
            sharedPreferences.edit().putStringSet("uploadSet", set).commit();
        }*/

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this).setSmallIcon(R.drawable.notification).setContentTitle("My notification").setContentText("Hello World!");
        Notification notification = builder.build();
        startForeground(11, notification);
        Log.i("MyService", "onCreate:" + String.valueOf(Thread.currentThread().getId()));

        new Thread(new Runnable() {
            @Override
            public void run() {
                if ("audio".equalsIgnoreCase(type)) {
                    startRecordingByAudioRecord();
                } else {
                    Log.i("MyService", "startRecordingByMediaRecord:" + String.valueOf(Thread.currentThread().getId()));
                    startRecordingByMediaRecord(true);
                }
            }
        }).start();

        HandlerThread uploadThread = new HandlerThread("uploadThread");
        uploadThread.start();
        uploadHandler = new Handler(uploadThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case CAN_UPLOAD:
                        Log.i("MyService", "doUpload:" + String.valueOf(Thread.currentThread().getId()));
                        doUpload();
                }
            }
        };
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
        Log.i("MyService", "fileSize:" + String.valueOf(fileSize));
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
                //    Log.i("MyService", "bufferReaderResult:" + String.valueOf(bufferReaderResult));
                if (!isRecording) {
                    Log.i("MyService", "break!");
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
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MyService.this);
        return sharedPreferences.getString("filename", fileNameA);
    }

    private FileOutputStream getFileOutputStreamChange() {
        FileOutputStream os = null;
        File file;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MyService.this);
        if (fileNameA.equalsIgnoreCase(getmFileNameFromCache())) {
            file = new File(Environment.getExternalStorageDirectory() + "/audiorecord", fileNameB);
            sharedPreferences.edit().putString("filename", fileNameB).commit();
            Log.i("MyService", "file change to B---------------------");
        } else {
            file = new File(Environment.getExternalStorageDirectory() + "/audiorecord", fileNameA);
            sharedPreferences.edit().putString("filename", fileNameA).commit();
            Log.i("MyService", "file change to A----------------------");
        }
        Log.i("MyService", "file " + String.valueOf(sharedPreferences.getString("filename", null)));
        try {
            os = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return os;
    }

    private void doUpload() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MyService.this);
        Set<String> set = sharedPreferences.getStringSet("uploadSet", null);
        for (String file : set) {
            if (file.equalsIgnoreCase(currentFile)) {
                continue;
            }
            upload(file);
        }
    }

    //bmob
    private void upload(final String filePath) {
        BmobProFile.getInstance(MyService.this).upload(filePath, new UploadListener() {
            Message message = Message.obtain();

            @Override
            public void onSuccess(String fileName, String url) {
                updateUploadSituation(filePath);
                message = message.obtain();
                message.what = UPLOAD_END;
                message.obj = fileName;
                handler.sendMessage(message);

            }

            @Override
            public void onProgress(int ratio) {
                message = message.obtain();
                message.what = UPLOAD_ING;
                message.obj = ratio;
                handler.sendMessage(message);
                Log.i("MyService", " -onProgress :" + ratio);
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

    private void startRecordingByMediaRecord(boolean newFlag) {
        if (newFlag) {
            mRecorder = new MediaRecorder();
        }
        Log.i("MyService", " -startRecordingByMediaRecord :" + Thread.currentThread().getId());
        Log.i("MyService", " -mRecorder :" + mRecorder);
        mRecorder.reset();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(getmFileNameByMediaType());
        Log.i("MyService", " -file :" + getmFileNameByMediaType());
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mRecorder.setOnInfoListener(infoListener);
        mRecorder.setMaxFileSize(maxFileSize);
        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.e("MainActivity", "prepare() failed");
        }
        mRecorder.start();
        //   int SPACE = 10000;// 间隔取样时间
        //  subThreadHandle.postDelayed(mUpdateMicStatusTimer, SPACE);
     /*   recordingThread2 = new Thread(new Runnable() {
            public void run() {
                updateMicStatus();
            }
        }, "MediaRecorder Thread");
        recordingThread2.start();*/
    }

    /* private Runnable mUpdateMicStatusTimer = new Runnable() {
         public void run() {
             Log.i("MyService", " -startRecordingByMediaRecord mUpdateMicStatusTimer:" + Thread.currentThread().getId());
             mRecorder.stop();
             mRecorder.release();
           *//*  Message message = Message.obtain();
            message.what= 2;
            message.setTarget(subThreadHandle);
            message.sendToTarget();*//*
        }
    };*/
    private String getmFileNameByMediaType() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MyService.this);
        SimpleDateFormat format = new SimpleDateFormat("ddMMyyyy_HHmmss");
        String filename = directory + format.format(new Date()) + ".3gp";
        Set<String> set = sharedPreferences.getStringSet("uploadSet", null);
        set.add(filename);
        sharedPreferences.edit().putStringSet("uploadSet", set).apply();
        currentFile = filename;
        return filename;
    }

    private void updateUploadSituation(String fileName) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MyService.this);
        Set<String> set = sharedPreferences.getStringSet("uploadSet", null);
        set.remove(fileName);
        sharedPreferences.edit().putStringSet("uploadSet", set).commit();
        File file = new File(fileName);
        file.delete();
    }
}
