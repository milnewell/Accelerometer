package com.example.accelerometer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;

import static java.lang.Math.sqrt;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    public Vibrator v;
    private float vibrateThreshold = 0;
    public double X, Y, Z, magnitude, fftave;
    public int p, period, i, s, windsize ;
    private MediaPlayer slow, med, fast;
    public SeekBar sampleRate, windowsize;
    public GraphView graph, FFTGraph;
    public Timer timer = new Timer(), ffttimer = new Timer();
    public FFT fft;
    public Queue<Double> magQueue = new LinkedList<>();
    public LineGraphSeries<DataPoint> seriesx, seriesy, seriesz, seriesmagnitude, fftseries;
    public TextView state;
    public double[] fftmag,q, gravity = new double[3];

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeViews();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        assert sensorManager != null;
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            // success! we have an accelerometer

            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            vibrateThreshold = accelerometer.getMaximumRange() / 2;
        }
        v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                appendToSeries();
            }
        }, 0, 1000);

        ffttimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateSeries();
            }
        }, windsize*1000+1, windsize*1000+1);
    }

    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    //onPause() unregister the accelerometer for stop listening the events
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void onSensorChanged(SensorEvent event) {

        final float alpha = (float) 0.8;

        // Isolate the force of gravity with the low-pass filter.
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

        // Remove the gravity contribution with the high-pass filter.
        float deltaX = (float) (event.values[0] - gravity[0]);
        float deltaY = (float) (event.values[1] - gravity[1]);
        float deltaZ = (float) (event.values[2] - gravity[2]);

        // if the change is below 2, it is just plain noise
        if (deltaX < 2)
            deltaX = 0;
        if (deltaY < 2)
            deltaY = 0;
        if ((deltaX > vibrateThreshold) || (deltaY > vibrateThreshold) || (deltaZ > vibrateThreshold)) {
            v.vibrate(50);
        }

        X = deltaX;
        Y = deltaY;
        Z = deltaZ;

    }

   public void changePlayer(final double fftave) {
       runOnUiThread(new Runnable() {
           @SuppressLint("SetTextI18n")
           @Override
           public void run() {
              if(slow.isPlaying()) {
                  slow.pause();
              }else if(med.isPlaying()) {
                  med.pause();
              }else if(fast.isPlaying()) {
                  fast.pause();
              }
              if (fftave <= 2 && fftave >= 0.5) {
                  slow.start();
                  state.setText("Slow playing");
              } else if (fftave > 2 && fftave <= 5) {
                  med.start();
                  state.setText("Med playing");
              } else if (fftave > 5) {
                  fast.start();
                  state.setText("Fast playing");
              }
           }
       });
   }

    @SuppressLint("SetTextI18n")
    public void updateSeries() {

        FFTGraph.removeAllSeries();

        double[] zeroes = new double[windsize];
        q = new double[windsize];

        for(int p = 0; p < q.length; p++){
            q[p] = magQueue.remove();

        }
        fft.fft(q, zeroes);

        double max = 0;

        for(int m = 0; m < q.length;m++) {
            if(max<q[m]){
               max=q[m];
            }
        }

        fftave=max;

        Log.e("STATE", "Average: " + fftave);

        fftseries= new LineGraphSeries<>(data());
        FFTGraph.addSeries(fftseries);

        if (!slow.isPlaying() && !med.isPlaying() && !fast.isPlaying()) {
            changePlayer(fftave);
        }
        runOnUiThread(new Runnable() {
            @SuppressLint("SetTextI18n")
            @Override
            public void run() {
                if (fftave < 0.5 && X == 0 && Y == 0) {
                    if(slow.isPlaying()){
                        slow.pause();
                    }else if(med.isPlaying()){
                        med.pause();
                    }else if(fast.isPlaying()){
                        fast.pause();
                    }
                    state.setText("Player Paused");
                }
            }
        });

        if ((slow.isPlaying() && fftave > 2)
                || (med.isPlaying() && (fftave <= 5 || fftave > 7))
                || (fast.isPlaying() && fftave <= 7)) {
            changePlayer(fftave);
        }

    }

    public void appendToSeries() {
        magnitude = sqrt(X * X + Y * Y + Z * Z);

        //noinspection SuspiciousNameCombination
        seriesx.appendData(new DataPoint(i, X), true, 10);
        seriesy.appendData(new DataPoint(i, Y), true, 10);
        seriesz.appendData(new DataPoint(i, Z), true, 10);
        seriesmagnitude.appendData(new DataPoint(i, magnitude), true, 10);
        i++;
        magQueue.add(magnitude);
    }

    public DataPoint[] data(){
        int n=windsize;     //to find out the no. of data-points
        DataPoint[] values = new DataPoint[n];     //creating an object of type DataPoint[] of size 'n'
        for(int i=0;i<n;i++){
            DataPoint v = new DataPoint(i,q[i]);
            values[i] = v;
        }
        return values;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void initializeViews() {
        slow = MediaPlayer.create(this, R.raw.sound);
        med = MediaPlayer.create(this, R.raw.sound2);
        fast = MediaPlayer.create(this, R.raw.sound3);

        windsize=4;
        state = findViewById(R.id.state);
        fft = new FFT(windsize);
        X = 0;
        Y = 0;
        Z = 0;
        magnitude = 0;
        fftave = 0.0;
        fftmag = new double[windsize];
        period = 1000;
        graph = findViewById(R.id.graph);
        FFTGraph = findViewById(R.id.fftgraph);
        windowsize = findViewById(R.id.seekBar);
        sampleRate = findViewById(R.id.seekBarSampleRate);

        seriesx = new LineGraphSeries<>(new DataPoint[]{
                new DataPoint(0, 0)
        });
        seriesy = new LineGraphSeries<>(new DataPoint[]{
                new DataPoint(0, 0)
        });
        seriesz = new LineGraphSeries<>(new DataPoint[]{
                new DataPoint(0, 0)
        });
        seriesmagnitude = new LineGraphSeries<>(new DataPoint[]{
                new DataPoint(0, 0)
        });
        fftseries= new LineGraphSeries<>(new DataPoint[]{
                new DataPoint(0, 0)
        });

        FFTGraph.getViewport().setXAxisBoundsManual(true);
        FFTGraph.getViewport().setMinX(0);
        FFTGraph.getViewport().setMaxX(windsize);

        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(10);

        seriesx.setColor(Color.RED);
        seriesy.setColor(Color.GREEN);
        seriesz.setColor(Color.BLUE);
        seriesmagnitude.setColor(Color.BLACK);

        graph.addSeries(seriesx);
        graph.addSeries(seriesy);
        graph.addSeries(seriesz);
        graph.addSeries(seriesmagnitude);
        FFTGraph.addSeries(fftseries);

        sampleRate.setMin(100);
        sampleRate.setMax(5000);
        sampleRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int period, boolean b) {
                p = period;

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                timer.cancel();
                timer = new Timer();
                ffttimer.cancel();
                ffttimer = new Timer();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        appendToSeries();
                    }
                }, 0, p);

                ffttimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        updateSeries();
                        FFTGraph.addSeries(fftseries);

                    }
                }, windsize*p+1, windsize*p+1);
            }
        });

        windowsize.setMin(1);
        windowsize.setMax(5);
        windowsize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                s = i;
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                windsize = (int) Math.pow(2, s);
                fft = new FFT(windsize);
                ffttimer.cancel();
                ffttimer = new Timer();
                ffttimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        updateSeries();
                    }
                }, windsize*1000+1, windsize*1000+1);
            }
        });
    }
}