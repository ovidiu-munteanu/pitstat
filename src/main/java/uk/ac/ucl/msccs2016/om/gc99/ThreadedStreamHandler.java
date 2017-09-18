/*
 * University College London
 * MSc Computer Science
 * September 2017
 *
 * PitStat
 *
 * This software is a component of the final project titled:
 *
 * Change Impact Analysis through Mutation Testing
 *
 * Author: Ovidiu Munteanu
 * Supervisor: Jens Krinke
 *
 * This software is submitted as part requirement for the MSc
 * Computer Science degree at UCL.It is substantially the result
 * of my own work except where explicitly indicated in the code.
 *
 * This software may be freely copied and distributed provided
 * the source is explicitly acknowledged.
 */
package uk.ac.ucl.msccs2016.om.gc99;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Primary Utility Class that implements a threaded stream handler used by CommandExecutor to read the standard output and
 * standard error streams of an external command.
 * <p>
 * <b>References:</b><br>
 * A. Alexander, 'Java exec - execute system processes with Java ProcessBuilder and Process (part 1)'.
 * [Online]. Available: <a href="https://alvinalexander.com/java/java-exec-processbuilder-process-1" target="_blank">
 * https://alvinalexander.com/java/java-exec-processbuilder-process-1 </a><br>
 * A. Alexander, 'Java exec - execute system processes with Java ProcessBuilder and Process (part 2)'.
 * [Online]. Available: <a href="https://alvinalexander.com/java/java-exec-processbuilder-process-2" target="_blank">
 * https://alvinalexander.com/java/java-exec-processbuilder-process-1 </a><br>
 * A. Alexander, 'Java exec - execute system processes with Java ProcessBuilder and Process (part 3)'.
 * [Online]. Available: <a href="https://alvinalexander.com/java/java-exec-processbuilder-process-3" target="_blank">
 * https://alvinalexander.com/java/java-exec-processbuilder-process-1 </a><br>
 */
class ThreadedStreamHandler extends Thread {
    private InputStream inputStream;
    private List<String> outputBuffer;

    /**
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
     * @return
     */
    List<String> getOutputBuffer() {
        return outputBuffer;
    }
}









