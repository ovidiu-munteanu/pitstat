package uk.ac.ucl.msccs2016.om.gc99;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
class ThreadedStreamHandler extends Thread {
    private InputStream inputStream;
    private List<String> outputBuffer;

    /**
     *
     * @param inputStream
     */
    ThreadedStreamHandler(InputStream inputStream) {
        this.inputStream = inputStream;
        this.outputBuffer = new ArrayList<>();
    }

    /**
     *
     */
    public void run() {

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line;

        try {
            while ((line = bufferedReader.readLine()) != null) outputBuffer.add(line);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Throwable t) {
            System.out.println("Something unexpected happened:");
            t.printStackTrace();
        } finally {
            try {
                bufferedReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     * @return
     */
    List<String> getOutputBuffer() {
        return outputBuffer;
    }
}









