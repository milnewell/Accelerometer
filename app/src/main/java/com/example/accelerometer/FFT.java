package com.example.accelerometer;
// IMPORTANT: Remember to declare your package! This should be the same as your main activity's package.


/*********************************************** Ignore this part, it's legal stuff *************************************************/
/************************************************************************************************************************************
 * fft.c
 * Douglas L. Jones
 * University of Illinois at Urbana-Champaign
 * January 19, 1992
 * http://cnx.rice.edu/content/m12016/latest/
 *
 *   fft: in-place radix-2 DIT DFT of a complex input
 *
 *   input:
 * n: length of FFT: must be a power of two
 * m: n = 2**m
 *   input/output
 * x: double array of length n with real part of data
 * y: double array of length n with imag part of data
 *
 *   Permission to copy and use this program is granted
 *   as long as this header is included.
 *************************************************************************************************************************************/


/**************************************** Don't ignore this part, it's important stuff **********************************************/
/**
 * This class should handle the FFT operations for you. To use it, declare a single instance of it and then call its fft()
 * method whenever you need some data to be processed.
 *
 * A Fast Fourier Transform (FFT) processes signals for you and extracts any underlying frequencies within potentially noisy data;
 * for example, if you took the audio signal from a piece of music and ran it through an FFT you could figure out every single
 * note that is being played at any one time. In our case we use it to get rid of noise in our sensor readings; noise shouldn't have
 * a constant frequency and so won't show up once the readings have been processed.
 *
 * This FFT has a window size associated with it, which you have to declare when you construct it. This window size has to be a
 * power of two otherwise your app will crash.
 *
 * When you call the the fft() method you give it the data you want processed. Each value should be a constant time
 * period apart; for example, each could be readings from your sensor taken exactly a second apart. Once the method
 * returns, the results will be stored in the same arrays that you used to pass the data into the method. This data will
 * now be in the frequency domain, which sounds fancy but all that means is that one of your graph axes will now be
 * specific frequencies rather than specific points in time. High values in any array index then means that there was
 * a lot of that specific frequency present in your input data; e.g. if x[1] is high then there was a 1Hz frequency
 * present in your data.
 *
 * I know this is all very confusing so I'd highly recommend having a look at this video which explains it in visual terms:
 * https://www.youtube.com/watch?v=spUNpyF58BY
 * You don't need to know exactly how the maths works, you just need to know how to work with it. So don't worry too much
 * about the details.
 *
 * @author Jacob Young, but adapted from here: https://www.ee.columbia.edu/~ronw/code/MEAPsoft/doc/html/FFT_8java-source.html
 * @since 2019-06-28
 */
public class FFT {

    private int n, m;

    // Lookup tables. Only need to recompute when size of FFT changes.
    private double[] cos;
    private double[] sin;

    /**
     * The constructor. n is the size of your FFT window and must be a power of two.
     *
     * @param n The size of your FFT window. Must be a power of two.
     */
    public FFT(int n) {
        setWindowSize(n);
    }

    /**
     * This method will do the actual signal processing for you. To use it you need to pass in two
     * separate arrays that are used for both input and output, both of which should be of length n
     * (which is the same n you specify in this class' constructor):
     *    x - When passed into this function, this will contain the signal values that you want processed
     *        (i.e. your accelerometer values). After the method returns it will contain the real part of
     *        the result of the FFT; this is the part you actually care about and is what you should be plotting.
     *    y - The imaginary part of the FFT. When you call this method it should be full of 0's, and afterwards
     *        you don't necessarily need to care about what is in there. The imaginary part of the FFT does
     *        contain some useful information if you want to look deeper though so I've left it in in changes
     *        you want to use it.
     *
     * @param x Contains the real part of the FFT (both input and output).
     * @param y Contains the imaginary part of the FFT (both input and output).
     */
    public synchronized void fft(double[] x, double[] y) {
        int i, j, k, n1, n2, a;
        double c, s, t1, t2;

        // Bit-reverse
        j = 0;
        n2 = n / 2;
        for (i = 1; i < n - 1; i++) {
            n1 = n2;
            while (j >= n1) {
                j = j - n1;
                n1 = n1 / 2;
            }
            j = j + n1;

            if (i < j) {
                t1 = x[i];
                x[i] = x[j];
                x[j] = t1;
                t1 = y[i];
                y[i] = y[j];
                y[j] = t1;
            }
        }

        // FFT
        n1 = 0;
        n2 = 1;

        for (i = 0; i < m; i++) {
            n1 = n2;
            n2 = n2 + n2;
            a = 0;

            for (j = 0; j < n1; j++) {
                c = cos[a];
                s = sin[a];
                a += 1 << (m - i - 1);

                for (k = j; k < n; k = k + n2) {
                    t1 = c * x[k + n1] - s * y[k + n1];
                    t2 = s * x[k + n1] + c * y[k + n1];
                    x[k + n1] = x[k] - t1;
                    y[k + n1] = y[k] - t2;
                    x[k] = x[k] + t1;
                    y[k] = y[k] + t2;
                }
            }
        }
    }

    /**
     * You can use this method to change the window size of the FFT after it's been constructed.
     * Make sure it's a power of two!
     *
     * @param n This FFT's new window size
     * @throws RuntimeException
     */
    public synchronized void setWindowSize(int n) {
        this.n = n;
        this.m = (int) (Math.log(n) / Math.log(2));

        // Make sure n is a power of 2
        if (n != (1 << m))
            throw new RuntimeException("FFT length must be power of 2");

        // precompute tables
        cos = new double[n / 2];
        sin = new double[n / 2];

        for (int i = 0; i < n / 2; i++) {
            cos[i] = Math.cos(-2 * Math.PI * i / n);
            sin[i] = Math.sin(-2 * Math.PI * i / n);
        }
    }

    /**
     * Returns the current FFT window size.
     *
     * @return The current FFT window size.
     */
    public int getWindowSize() {
        return n;
    }
}