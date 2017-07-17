package uk.ac.ucl.msccs2016.om.gc99;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

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

    JSONHandler(){
        createGson(false);
    }

    JSONHandler(boolean prettyPrinting) {
        createGson(prettyPrinting);
    }

    private void createGson(boolean prettyPrinting){
//        GsonBuilder gsonBuilder = new GsonBuilder().serializeNulls().disableHtmlEscaping();
        GsonBuilder gsonBuilder = new GsonBuilder().disableHtmlEscaping();
        if (prettyPrinting)
            gsonBuilder = gsonBuilder.setPrettyPrinting();
        gson = gsonBuilder.create();
    }


    DiffOutput loadDifFromJSON(String fileName) throws IOException {
        return loadDifFromJSON(Files.newInputStream(Paths.get(fileName)));
    }

    PitOutput loadPitFromJSON(String fileName) throws IOException {
        return loadPitFromJSON(Files.newInputStream(Paths.get(fileName)));
    }

    MatrixOutput loadMatrixFromJSON(String fileName) throws IOException {
        return loadMatrixFromJSON(Files.newInputStream(Paths.get(fileName)));
    }

    void saveDifToJSON(DiffOutput object, String fileName) throws IOException {
        saveDifToJSON(object, Files.newOutputStream(Paths.get(fileName)));
    }

    void savePitToJSON(PitOutput object, String fileName) throws IOException {
        savePitToJSON(object, Files.newOutputStream(Paths.get(fileName)));
    }

    void saveMatrixToJSON(MatrixOutput object, String fileName) throws IOException {
        saveMatrixToJSON(object, Files.newOutputStream(Paths.get(fileName)));
    }


    DiffOutput loadDifFromJSON(InputStream inputStream) throws IOException {
        BufferedReader unicodeBufferedReader =
                new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        DiffOutput object = gson.fromJson(unicodeBufferedReader, new TypeToken<DiffOutput>() {
        }.getType());
        unicodeBufferedReader.close();
        return object;
    }

    PitOutput loadPitFromJSON(InputStream inputStream) throws IOException {
        BufferedReader unicodeBufferedReader =
                new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        PitOutput object = gson.fromJson(unicodeBufferedReader, new TypeToken<PitOutput>() {
        }.getType());
        unicodeBufferedReader.close();
        return object;
    }

    MatrixOutput loadMatrixFromJSON(InputStream inputStream) throws IOException {
        BufferedReader unicodeBufferedReader =
                new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        MatrixOutput object = gson.fromJson(unicodeBufferedReader, new TypeToken<PitOutput>() {
        }.getType());
        unicodeBufferedReader.close();
        return object;
    }

    void saveDifToJSON(DiffOutput object, OutputStream outputStream) throws IOException {
        BufferedWriter unicodeBufferedWriter =
                new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        gson.toJson(object, unicodeBufferedWriter);
        unicodeBufferedWriter.close();
    }

    void savePitToJSON(PitOutput object, OutputStream outputStream) throws IOException {
        BufferedWriter unicodeBufferedWriter =
                new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        gson.toJson(object, unicodeBufferedWriter);
        unicodeBufferedWriter.close();
    }

    void saveMatrixToJSON(MatrixOutput object, OutputStream outputStream) throws IOException {
        BufferedWriter unicodeBufferedWriter =
                new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        gson.toJson(object, unicodeBufferedWriter);
        unicodeBufferedWriter.close();
    }

}