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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Primary Utility Class used to read/write JSON output using the Google Gson library;
 * also used to clone objects using the same library.
 * <p>
 * <b>References &amp; Libraries:</b><br>
 * Google - Gson User Guide.
 * [Online]. Available: <a href="https://github.com/google/gson/blob/master/UserGuide.md" target="_blank">
 * https://github.com/google/gson/blob/master/UserGuide.md</a><br>
 * Google - Gson Library.
 * [Online]. Available: <a href="https://github.com/google/gson" target="_blank">
 * https://github.com/google/gson</a>
 */
class JSONHandler {

    private Gson gson;

    /**
     *
     */
    JSONHandler() {
        createGson(false);
    }

    /**
     * @param prettyPrinting
     */
    JSONHandler(boolean prettyPrinting) {
        createGson(prettyPrinting);
    }

    /**
     * @param prettyPrinting
     */
    private void createGson(boolean prettyPrinting) {
        GsonBuilder gsonBuilder = new GsonBuilder().disableHtmlEscaping();
        if (prettyPrinting) gsonBuilder = gsonBuilder.setPrettyPrinting();
        gson = gsonBuilder.create();
    }

    /**
     * @param o
     * @param fileName
     * @throws IOException
     */
    void saveToJSON(Object o, String fileName) throws IOException {
        saveToJSON(o, Files.newOutputStream(Paths.get(fileName)));
    }

    /**
     * @param o
     * @param os
     * @throws IOException
     */
    void saveToJSON(Object o, OutputStream os) throws IOException {
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        gson.toJson(o, bufferedWriter);
        bufferedWriter.close();
    }

    /**
     * @param fileName
     * @param o
     * @return
     * @throws IOException
     */
    Object loadFromJSON(String fileName, Object o) throws IOException {
        return loadFromJSON(Files.newInputStream(Paths.get(fileName)), o);
    }

    /**
     * @param is
     * @param o
     * @return
     * @throws IOException
     */
    Object loadFromJSON(InputStream is, Object o) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        Object fromJson = gson.fromJson(bufferedReader, o.getClass());
        bufferedReader.close();
        return fromJson;
    }

    /**
     * @param o
     * @return
     */
    static Object cloneObject(Object o) {
        return deserializeObject(serializeObject(o), o);
    }

    /**
     * @param o
     * @return
     */
    private static String serializeObject(Object o) {
        return (new GsonBuilder().disableHtmlEscaping().create()).toJson(o);
    }

    /**
     * @param s
     * @param o
     * @return
     */
    private static Object deserializeObject(String s, Object o) {
        return (new GsonBuilder().disableHtmlEscaping().create()).fromJson(s, o.getClass());
    }
}