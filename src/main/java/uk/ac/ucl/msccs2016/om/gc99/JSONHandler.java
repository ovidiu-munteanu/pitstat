package uk.ac.ucl.msccs2016.om.gc99;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;


class JSONHandler<E> {

    private Gson gson;

    JSONHandler() {

        gson = new GsonBuilder().serializeNulls().disableHtmlEscaping().setPrettyPrinting().create();

    }

    E loadFromJSON(String fileName) throws Exception {

        Reader unicodeReader = fileBufferedReader_UTF8(fileName);

        E object = gson.fromJson(fileBufferedReader_UTF8(fileName), new TypeToken<E>() {
        }.getType());

        unicodeReader.close();

        return object;
    }

    void saveToJSON(E object, String fileName) throws Exception {

        Writer unicodeWriter = fileBufferedWriter_UTF8(fileName);

        gson.toJson(object, unicodeWriter);

        unicodeWriter.flush();
        unicodeWriter.close();
    }


    private BufferedReader fileBufferedReader_UTF8(String fileName) {

        File jsonFile = new File(fileName);
        BufferedReader bufferedReader = null;

        try {
            bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(jsonFile), "UTF-8"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return bufferedReader;

    }


    private BufferedWriter fileBufferedWriter_UTF8(String fileName) {

        File jsonFile = new File(fileName);
        BufferedWriter bufferedWriter = null;

        try {
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(jsonFile), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return bufferedWriter;

    }


}