package uk.ac.ucl.msccs2016.om.gc99;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;


class JSONHandler {

    private Gson gson;

    JSONHandler() {
        gson = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
//        gson = new GsonBuilder().serializeNulls().disableHtmlEscaping().setPrettyPrinting().create();

    }

    DiffOutput loadDifFromJSON(String fileName) throws Exception {
        BufferedReader unicodeBufferedReader =
                new BufferedReader(new InputStreamReader(new FileInputStream(new File(fileName)), "UTF-8"));
        DiffOutput object = gson.fromJson(unicodeBufferedReader, new TypeToken<DiffOutput>() {}.getType());
        unicodeBufferedReader.close();
        return object;
    }

    PitOutput loadPitFromJSON(String fileName) throws Exception {
        BufferedReader unicodeBufferedReader =
                new BufferedReader(new InputStreamReader(new FileInputStream(new File(fileName)), "UTF-8"));
        PitOutput object = gson.fromJson(unicodeBufferedReader, new TypeToken<PitOutput>() {}.getType());
        unicodeBufferedReader.close();
        return object;
    }

    MatrixOutput loadMatrixFromJSON(String fileName) throws Exception {
        BufferedReader unicodeBufferedReader =
                new BufferedReader(new InputStreamReader(new FileInputStream(new File(fileName)), "UTF-8"));
        MatrixOutput object = gson.fromJson(unicodeBufferedReader, new TypeToken<PitOutput>() {}.getType());
        unicodeBufferedReader.close();
        return object;
    }

    void saveDifToJSON(DiffOutput object, String fileName) throws Exception {
        BufferedWriter unicodeBufferedWriter =
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(fileName)), "UTF-8"));
        gson.toJson(object, unicodeBufferedWriter);
        unicodeBufferedWriter.close();
    }

    void savePitToJSON(PitOutput object, String fileName) throws Exception {
        BufferedWriter unicodeBufferedWriter =
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(fileName)), "UTF-8"));
        gson.toJson(object, unicodeBufferedWriter);
        unicodeBufferedWriter.close();
    }

    void saveMatrixToJSON(MatrixOutput object, String fileName) throws Exception {
        BufferedWriter unicodeBufferedWriter =
                new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(fileName)), "UTF-8"));
        gson.toJson(object, unicodeBufferedWriter);
        unicodeBufferedWriter.close();
    }

}