package com.example.accelerometer;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.solver.widgets.Helper;

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

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.xml.transform.dom.DOMLocator;

import static java.lang.Math.sqrt;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    public Vibrator v;
    private float vibrateThreshold = 0;
    public double X, Y, Z, magnitude, transformedmag,fftave;
    public ArrayList<Double> transformedX, transformedY, transformedZ;
    public int p, period, i, s;
    private MediaPlayer slow,med,fast;
    public SeekBar sampleRate, windowsize;
    public GraphView graph,FFTGraph;
    public Timer timer = new Timer(),ffttimer = new Timer();
    public FFT fft;
    public LineGraphSeries < DataPoint > seriesx, seriesy, seriesz, seriesmagnitude, fftmagnitude,fftseries;
    public TextView state;


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeViews();


        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            // success! we have an accelerometer

            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            vibrateThreshold = accelerometer.getMaximumRange() / 2;
        } else {
            // fail! we dont have an accelerometer!
        }
        //initialize vibration
        v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

        timer.schedule(new TimerTask() {

            @Override
            public void run() {
                appendToSeries();
                Log.e("STATE", "FFT VAL: " + fftave);
            }

        }, 0, 1000);

        ffttimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateSeries();
            }
        }, 5001, 5001);
    }

    protected void onResume() {
        super.onResume();
        sensorManager.registerListener((SensorEventListener) this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    //onPause() unregister the accelerometer for stop listening the events
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener((SensorEventListener) this);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void onSensorChanged(SensorEvent event) {

        float deltaX = event.values[0];
        float deltaY = event.values[1];
        float deltaZ = event.values[2];


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

        if(!slow.isPlaying() && !med.isPlaying() && !fast.isPlaying()){
            changePlayer(fftave);
        }

        if((slow.isPlaying() || med.isPlaying() || fast.isPlaying()) && fftave < 0.5 && deltaX==0 && deltaY==0) {
            slow.stop();
            med.stop();
            fast.stop();
            state.setText("Player Paused");
        }

        if    ((slow.isPlaying() && fftave>2)
            || (med.isPlaying() && (fftave<=5 || fftave >7))
            || (fast.isPlaying() && fftave <= 7))
        {
            changePlayer(fftave);
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    public void initializeViews() {
        slow = MediaPlayer.create(this, R.raw.sound);
        med = MediaPlayer.create(this, R.raw.sound2);
        fast = MediaPlayer.create(this, R.raw.sound3);

        state = findViewById(R.id.state);
        fft = new FFT(2);
        X = 0;
        Y = 0;
        Z = 0;
        magnitude = 0;
        transformedmag = 0;
        fftave = 0.0;
        transformedX = new ArrayList<>();
        transformedY = new ArrayList<>();
        transformedZ = new ArrayList<>();
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
        fftmagnitude = new LineGraphSeries<>(new DataPoint[]{
                new DataPoint(0, 0)
        });
        fftseries = new LineGraphSeries<>(new DataPoint[]{
                new DataPoint(0, 0)
        });

        FFTGraph.getViewport().setXAxisBoundsManual(true);
        FFTGraph.getViewport().setMinX(0);
        FFTGraph.getViewport().setMaxX(5);

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

                transformedX.clear();
                transformedY.clear();
                transformedZ.clear();
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
                }, 5 * p, 5 * p);
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
                int size = (int)Math.pow(2, s);
                FFTGraph.getViewport().setMaxY(s+10);
                fft = new FFT(size);
            }
        });
    }



    public double[] getTransformed(ArrayList<Double> list){

        double x1 = list.get(0);
        double x2 = list.get(1);
        double x3 = list.get(2);
        double x4 = list.get(3);
        double x5 = list.get(4);

        double[] fx = {x1, x2, x3, x4, x5};
        return fx;
    }

    public double[] getMagnitudeArray(double[] fx, double[] fy, double[] fz){
        double[] a = new double[5];
        for(int k=0;k<5;k++){
            a[k] = sqrt(fx[k] * fx[k] + fy[k] * fy[k] + fz[k] * fz[k]);
        }
        return a;
    }

    public void changePlayer(double fftave){
        slow.stop();
        med.stop();
        fast.stop();

        if(fftave <= 2 && fftave >= 0.5){
            slow.start();
            state.setText("Slow is Playing");
        }else if(fftave > 2 && fftave <= 5){
            med.start();
            state.setText("Medium is Playing");
        }else if(fftave > 5){
            fast.start();
            state.setText("Fast is Playing");
        }
    }

    public void updateSeries(){


        FFTGraph.removeAllSeries();

        double[] zeroes = { 0, 0, 0, 0, 0 };

        double[] fx = getTransformed(transformedX);
        double[] fy = getTransformed(transformedY);
        double[] fz = getTransformed(transformedZ);

        fft.fft(fx, zeroes);
        fft.fft(fy, zeroes);
        fft.fft(fz, zeroes);

        double[] magnitudes = getMagnitudeArray(fx,fy,fz);

        fftave = (magnitudes[0] + magnitudes[1] + magnitudes[2] + magnitudes[3] + magnitudes[4])/5;

        LineGraphSeries<DataPoint> fftseries = new LineGraphSeries<>(new DataPoint[]{
                new DataPoint(0, magnitudes[0]),
                new DataPoint(1, magnitudes[1]),
                new DataPoint(2, magnitudes[2]),
                new DataPoint(3, magnitudes[3]),
                new DataPoint(4, magnitudes[4])
        });

        FFTGraph.addSeries(fftseries);

        transformedX.clear();
        transformedY.clear();
        transformedZ.clear();

    }

    public void appendToSeries(){
        magnitude = sqrt(X*X + Y*Y + Z*Z);

        seriesx.appendData(new DataPoint(i,X),true,10);
        seriesy.appendData(new DataPoint(i,Y),true,10);
        seriesz.appendData(new DataPoint(i,Z),true,10);
        seriesmagnitude.appendData(new DataPoint(i, magnitude), true, 10);
        i++;
        transformedX.add(X);
        transformedY.add(Y);
        transformedZ.add(Z);
    }

}

