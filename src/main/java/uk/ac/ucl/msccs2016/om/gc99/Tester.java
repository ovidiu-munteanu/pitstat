package uk.ac.ucl.msccs2016.om.gc99;

import java.io.BufferedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


public class Tester {

    public static void main(String[] args) throws Exception {

        final JSONHandler jsonHandler = new JSONHandler(true);

        String directory = "D:/X/github/joda-time/target/pitstat-reports/20170714114257";
        String sourceFile = "pitM-20170714115512-5d96cf2f4676a7494e539838409b9a2b78ccdd15.json";

        Path sourceFilePath = Paths.get(directory, sourceFile);

        PitOutput pitOutput = jsonHandler.loadPitFromJSON(sourceFilePath.toString());



        String zipFile = sourceFile.replace(".json", ".zip");
        Path zipFilePath = Paths.get(directory, zipFile);

        ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFilePath));
        zipOutputStream.putNextEntry(new ZipEntry(sourceFile));

        jsonHandler.savePitToJSON(pitOutput, new BufferedOutputStream(zipOutputStream));




        System.out.println(zipFilePath);

        ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFilePath));
        zipInputStream.getNextEntry();

        PitOutput test = jsonHandler.loadPitFromJSON(zipInputStream);

        System.out.println("Commit hash:" + test.commitHash);

        test.mutatedFiles.entrySet().forEach(entry -> {
            System.out.println("File name: " + entry.getKey());
            System.out.println("Change status: " + entry.getValue().changeStatus);
        });

        zipInputStream.close();

    }

}