package com.example.yjh.audiorecord;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bmob.BTPFileResponse;
import com.bmob.BmobProFile;
import com.bmob.btp.callback.UploadListener;
import com.bmob.utils.BmobLog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.LogRecord;

import cn.bmob.v3.Bmob;

/**
 * Created by yjh on 2015/6/15.
 */
public class MainActivity extends Activity {
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
    private boolean isRecording = false;
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
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress((Integer) msg.obj);
                    break;
                case UPLOAD_END:
                    progressBar.setVisibility(View.GONE);
                    showToast("Upload successfully");
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
            if (i == R.id.btnStart) {
                startRecordingByAudioRecord();
                startRecordingByMediaRecord();
                enableButtons(true);
            } else if (i == R.id.btnStart1) {
                startRecordingByAudioRecord();
                enableButtons(true);
            } else if (i == R.id.btnStart2) {
                startRecordingByMediaRecord();
                enableButtons(true);
            } else if (i == R.id.btnStop) {
                enableButtons(false);
                stopRecording();
            } else if (i == R.id.upload) {
                upload(Environment.getExternalStorageDirectory() + "/audiorecordByAudioRecord16bitmono.pcm");
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);
        // 初始化 Bmob SDK
        Bmob.initialize(this, "94e36ad9769577ceb1bf3d9dc2e9c396");
        textView = (TextView) findViewById(R.id.AudioRecord);
        textView2 = (TextView) findViewById(R.id.MediaRecord);
        scrollView = (ScrollView) findViewById(R.id.AudioRecordScrollView);
        scrollView2 = (ScrollView) findViewById(R.id.MediaRecordScrollView);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        setButtonHandlers();
        enableButtons(false);
    }

    private void setButtonHandlers() {
        (findViewById(R.id.btnStart)).setOnClickListener(btnClick);
        (findViewById(R.id.btnStart1)).setOnClickListener(btnClick);
        (findViewById(R.id.btnStart2)).setOnClickListener(btnClick);
        (findViewById(R.id.btnStop)).setOnClickListener(btnClick);
        (findViewById(R.id.upload)).setOnClickListener(btnClick);
    }

    private void enableButtons(boolean isRecording) {
        enableButton(R.id.btnStart, !isRecording);
        enableButton(R.id.btnStart1, !isRecording);
        enableButton(R.id.btnStart2, !isRecording);
        enableButton(R.id.btnStop, isRecording);
    }

    private void enableButton(int id, boolean isEnable) {
        (findViewById(id)).setEnabled(isEnable);
    }

    private void startRecordingByAudioRecord() {
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING, bufferSize);

        recorder.startRecording();
        isRecording = true;
        recordingThread = new Thread(new Runnable() {
            public void run() {
                writeAudioDataToFile();
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }

    private void startRecordingByMediaRecord() {
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        mFileName += "/audiorecordByMediaRecord.3gp";
        mRecorder.setOutputFile(mFileName);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.e("MainActivity", "prepare() failed");
        }
        mRecorder.start();
        recordingThread2 = new Thread(new Runnable() {
            public void run() {
                updateMicStatus();
            }
        }, "MediaRecorder Thread");
        recordingThread2.start();
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

    private void writeAudioDataToFile() {

        File file = new File(Environment.getExternalStorageDirectory(), "audiorecordByAudioRecord16bitmono.pcm");
        byte[] sData = new byte[bufferSize];
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        while (isRecording) {
            // gets the voice output from microphone to byte format

            int bufferReaderResult = recorder.read(sData, 0, bufferSize);
            Log.i("isRecording,", "Short wirting to file" + file.getAbsolutePath());
            try {
                // // writes the data to file from buffer
                Log.e("MainActivity", String.valueOf(bufferReaderResult));
                os.write(sData, 0, bufferReaderResult);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (recorder != null) {
                long v = 0;
                // 将 buffer 内容取出，进行平方和运算
                for (int i = 0; i < sData.length; i++) {
                    v += sData[i] * sData[i];
                    //         Log.i("MainActivity", "读到的内容 :" + sData[i]);
                }
                // 平方和除以数据总长度，得到音量大小。
                double mean = v / (double) bufferReaderResult;
                double volume = 10 * Math.log10(mean);
                Message message = new Message();
                message.what = UPDATE_AUDIORECORD_TEXT;
                message.obj = "分贝值:" + volume + "\n";
                handler.sendMessage(message);
            }
        }
        try {
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        // stops the recording activity
        if (null != recorder) {
            isRecording = false;
            recorder.stop();
            recorder.release();
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
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    //bmob
    private void upload(String filePath) {
        BmobProFile.getInstance(MainActivity.this).upload(filePath, new UploadListener() {
            Message message = Message.obtain();

            @Override
            public void onSuccess(String fileName, String url) {
                message = message.obtain();
                message.what = UPLOAD_END;
                handler.sendMessage(message);
            }

            @Override
            public void onProgress(int ratio) {
                // TODO Auto-generated method stub
                message = message.obtain();
                message.what = UPLOAD_ING;
                message.obj = ratio;
                handler.sendMessage(message);
                Log.i("MainActivity", " -onProgress :" + ratio);
            }

            @Override
            public void onError(int statuscode, String errormsg) {
                message = message.obtain();
                message.what = UPLOAD_END;
                handler.sendMessage(message);
                //  showToast("上传出错：" + errormsg);
            }
        });
    }

    private void showToast(String mess) {
        Toast.makeText(this, mess, Toast.LENGTH_LONG).show();
    }

}

