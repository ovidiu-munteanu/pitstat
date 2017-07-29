package uk.ac.ucl.msccs2016.om.gc99;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.SortedMap;

public class Tester {

    public static void main(String[] args) throws IOException {


    }

    static void test(String filename) {
        SortedMap<String, Charset> charsets = Charset.availableCharsets();
        for (String k : charsets.keySet()) {
            int line = 0;
            boolean success = true;
            try (BufferedReader b = Files.newBufferedReader(Paths.get(filename), charsets.get(k))) {
                while (b.ready()) {
                    b.readLine();
                    line++;
                }
            } catch (IOException e) {
                success = false;
                System.out.println(k + " failed on line " + line);
            }
            if (success) System.out.println("*************************  Successs " + k);
        }
    }


}





