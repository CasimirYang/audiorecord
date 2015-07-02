package com.example.yjh.audiorecord.activity;

import android.app.Activity;
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
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bmob.BmobProFile;
import com.bmob.btp.callback.UploadListener;
import com.example.yjh.audiorecord.R;
import com.example.yjh.audiorecord.service.MyIntentService;
import com.example.yjh.audiorecord.service.MyService;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import cn.bmob.v3.Bmob;

/**
 * Created by yjh on 2015/6/15.
 */
public class MainActivity extends Activity {
    private static final String fileNameA = "audiorecordByAudioRecord16bitmonoA.pcm";
    private static final String fileNameB = "audiorecordByAudioRecord16bitmonoB.pcm";
    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int UPDATE_AUDIORECORD_TEXT = 1;
    private static final int UPDATE_AUDIORECORD_TEXT2 = 2;
    private static final int UPLOAD_BEGIN = 3;
    private static final int UPLOAD_ING = 4;
    private static final int UPLOAD_END = 5;
    private static String mFileName = null;
    int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
            RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
    private AudioRecord recorder = null;
    private MediaRecorder mRecorder = null;
    private Thread recordingThread = null;
    private Thread recordingThread2 = null;
    private Boolean isRecording = false;
    private TextView textView;
    private TextView textView2;
    private ScrollView scrollView;
    private ScrollView scrollView2;
    private ProgressBar progressBar;
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_AUDIORECORD_TEXT:
                    textView.append((String) msg.obj);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                        }
                    });
                    break;
                case UPDATE_AUDIORECORD_TEXT2:
                    textView2.append((String) msg.obj);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            scrollView2.fullScroll(ScrollView.FOCUS_DOWN);
                        }
                    });
                    break;
                case UPLOAD_ING:
                    //    progressBar.setVisibility(View.VISIBLE);
                    //     progressBar.setProgress((Integer) msg.obj);
                    //      showNotification((Integer) msg.obj);
                    break;
                case UPLOAD_END:
                    //   progressBar.setVisibility(View.GONE);
                    //    showToast("Upload successfully");
                    break;
            }
        }
    };
    private Runnable mUpdateMicStatusTimer = new Runnable() {
        public void run() {
            updateMicStatus();
        }
    };
    private View.OnClickListener btnClick = new View.OnClickListener() {
        public void onClick(View v) {
            int i = v.getId();
          /*  if (i == R.id.btnStart) {
                startRecordingByAudioRecord();
                startRecordingByMediaRecord();
                enableButtons(true);
            }*/
            if (i == R.id.btnStart1) {
                //   startRecordingByAudioRecord();
                enableButtons(true);
            } else if (i == R.id.btnStart2) {
                //   startRecordingByMediaRecord();
                enableButtons(true);
            } else if (i == R.id.btnStop) {
                enableButtons(false);
                stopRecording();
            } else if (i == R.id.upload) {
                //   upload(Environment.getExternalStorageDirectory()+"/"+getFilesDir());
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);
        // 初始化 Bmob SDK
        //  startService(new Intent(this, MyService.class));
    /*    Bmob.initialize(this, "94e36ad9769577ceb1bf3d9dc2e9c396");
        textView = (TextView) findViewById(R.id.AudioRecord);
        textView2 = (TextView) findViewById(R.id.MediaRecord);
        scrollView = (ScrollView) findViewById(R.id.AudioRecordScrollView);
        scrollView2 = (ScrollView) findViewById(R.id.MediaRecordScrollView);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        setButtonHandlers();
        enableButtons(false);*/


      /* Callback interface you can use when instantiating a Handler to avoid having to implement your own subclass of Handler.
        返回true代表有Callback去handle,那么Handler本身的那个handleMessage就不会被调用了*/
        Handler handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                return false; //default false
            }
        }) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
            }
        };
        Message m1 = handler.obtainMessage(); //获得的Message包含target Handle的sendMessage(Message msg)
        Message m2 = Message.obtain();  //不包含target 所以Message的sendToTarget() 前需要设置target
    }

    private void setButtonHandlers() {
        //  (findViewById(R.id.btnStart)).setOnClickListener(btnClick);
        (findViewById(R.id.btnStart1)).setOnClickListener(btnClick);
        (findViewById(R.id.btnStart2)).setOnClickListener(btnClick);
        (findViewById(R.id.btnStop)).setOnClickListener(btnClick);
        (findViewById(R.id.upload)).setOnClickListener(btnClick);
    }

    private void enableButtons(boolean isRecording) {
        //   enableButton(R.id.btnStart, !isRecording);
        enableButton(R.id.btnStart1, !isRecording);
        enableButton(R.id.btnStart2, !isRecording);
        enableButton(R.id.btnStop, isRecording);
    }

    private void enableButton(int id, boolean isEnable) {
        (findViewById(id)).setEnabled(isEnable);
    }


    //convert short to byte
/*    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];
        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;
    }*/

    private void updateMicStatus() {
        if (mRecorder != null) {
            int amplitude = mRecorder.getMaxAmplitude();
            double db = 0;// 分贝
            if (amplitude > 1)
                db = 20 * Math.log10(amplitude);
            Message message = new Message();
            message.what = UPDATE_AUDIORECORD_TEXT2;
            message.obj = "分贝值m:" + String.valueOf(db) + "\n";
            handler.sendMessage(message);
            int SPACE = 100;// 间隔取样时间
            handler.postDelayed(mUpdateMicStatusTimer, SPACE);
        }
    }



    private void stopRecording() {
        // stops the recording activity
        if (null != recorder) {
            isRecording = false;
            Log.i("isRecording", "begin stop");
            recorder.stop();
            Log.i("isRecording", "after stop");
            recorder.release();
            Log.i("isRecording", "after release");
            recorder = null;
            recordingThread = null;
        }
        if (null != mRecorder) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
            recordingThread2 = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
//            if(notificationManager!=null)notificationManager.cancelAll();
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }



    private void showToast(String mess) {
        Toast.makeText(this, mess, Toast.LENGTH_LONG).show();
    }


}

