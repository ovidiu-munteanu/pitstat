package uk.ac.ucl.msccs2016.om.gc99;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

class CommandStreamThreadedHandler extends Thread {
    private InputStream inputStream;
    private StringBuilder outputBuffer;
    private int outputBufferLines;

    CommandStreamThreadedHandler(InputStream inputStream) {

        this.inputStream = inputStream;
        this.outputBuffer  = new StringBuilder();
        this.outputBufferLines = 0;
    }

    public void run() {

        BufferedReader bufferedReader = null;

        try {
            bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                outputBuffer.append(line);
                outputBuffer.append("\n");

                //System.out.println(line);

                outputBufferLines++;
            }
        } catch (IOException e) {
            e.printStackTrace();

        } catch (Throwable t) {

            t.printStackTrace();
        } finally {
            try {
                bufferedReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    StringBuilder getOutputBuffer() {

        return outputBuffer;
    }


    public int getOutputBufferLines() {

        return outputBufferLines;
    }
}









