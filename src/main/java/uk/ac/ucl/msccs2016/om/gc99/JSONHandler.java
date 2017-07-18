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

class JSONHandler {

    private Gson gson;

    JSONHandler() {
        createGson(false);
    }

    JSONHandler(boolean prettyPrinting) {
        createGson(prettyPrinting);
    }

    private void createGson(boolean prettyPrinting) {
        GsonBuilder gsonBuilder = new GsonBuilder().disableHtmlEscaping();
        if (prettyPrinting) gsonBuilder = gsonBuilder.setPrettyPrinting();
        gson = gsonBuilder.create();
    }


    void saveToJSON(Object o, String fileName) throws IOException {
        saveToJSON(o, Files.newOutputStream(Paths.get(fileName)));
    }

    void saveToJSON(Object o, OutputStream os) throws IOException {
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        gson.toJson(o, bufferedWriter);
        bufferedWriter.close();
    }


    Object loadFromJSON(String fileName, Object o) throws IOException {
        return loadFromJSON(Files.newInputStream(Paths.get(fileName)), o);
    }

    Object loadFromJSON(InputStream is, Object o) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        Object fromJson = gson.fromJson(bufferedReader, o.getClass());
        bufferedReader.close();
        return fromJson;
    }


    static Object cloneObject(Object o) {
        return deserializeObject(serializeObject(o), o);
    }

    private static String serializeObject(Object o) {
        return (new GsonBuilder().disableHtmlEscaping().create()).toJson(o);
    }

    private static Object deserializeObject(String s, Object o) {
        return (new GsonBuilder().disableHtmlEscaping().create()).fromJson(s, o.getClass());
    }
}