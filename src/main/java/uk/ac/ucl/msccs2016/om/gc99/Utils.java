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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static uk.ac.ucl.msccs2016.om.gc99.Worker.TYPE_DIFF_MACHINE_OUTPUT;
import static uk.ac.ucl.msccs2016.om.gc99.Worker.TYPE_MATRIX_MACHINE_OUTPUT;
import static uk.ac.ucl.msccs2016.om.gc99.Worker.TYPE_PIT_MACHINE_OUTPUT;
import static uk.ac.ucl.msccs2016.om.gc99.Worker.ZIP_EXTENSION;
import static uk.ac.ucl.msccs2016.om.gc99.Worker.TEMP_DIRECTORY;

/**
 * Primary Utiltity Class - implements a set of utility classes used throughout other classes.
 */
class Utils {

    /**
     * @param pitStatReportsPath
     * @param pitStatReportsPathRelative
     * @param startTime
     * @param createTimestampDirectory
     * @param projectPath
     * @return
     */
    static String createOutputDirectory(String pitStatReportsPath, Boolean pitStatReportsPathRelative,
                                        String startTime, boolean createTimestampDirectory, String projectPath) {
        String resultPath;
        Path outputPath = Paths.get((pitStatReportsPathRelative ? projectPath : ""), pitStatReportsPath);

        try {
            // create all output directories and sub-directories if they don't already exist
            resultPath = Files.createDirectories(outputPath).toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        if (createTimestampDirectory) {
            // create timestamped output sub-directory
            outputPath = Paths.get(resultPath, startTime);
            try {
                resultPath = Files.createDirectory(outputPath).toString();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        return resultPath;
    }

    /**
     * @return
     */
    static List<String> listTempFiles() {
        return directoryContents(TEMP_DIRECTORY);
    }

    /**
     * @param directory
     * @return
     */
    static List<String> directoryContents(String directory) {
        List<String> directoryContents = new ArrayList<>();
        try {
            Files.list(Paths.get(directory))
                    .forEach(file -> directoryContents.add(file.toString()));
        } catch (IOException e) {
            return null;
        }
        return directoryContents;
    }

    /**
     * @param oldTempFiles
     */
    static void deleteNewTempFiles(List<String> oldTempFiles) {
        try {
            Files.list(Paths.get(TEMP_DIRECTORY))
                    .forEach(file -> {
                        if (!oldTempFiles.contains(file.toString()))
                            try {
                                Files.walk(file.toAbsolutePath())
                                        .sorted(Comparator.reverseOrder())
                                        .map(Path::toFile)
                                        .forEach(File::delete);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                    });
        } catch (IOException e) {
            System.out.println("deleteNewTempFiles(): An IOException was thrown while deleting the new temp files.");
        }
    }

    /**
     * @param outputPath
     * @param commitHash
     * @param o
     * @param jsonHandler
     * @return
     */
    static DiffOutput loadDiffOutput(String outputPath, String commitHash, Object o, JSONHandler jsonHandler) {
        return (DiffOutput) loadOutput(outputPath, commitHash, TYPE_DIFF_MACHINE_OUTPUT, o, jsonHandler);
    }

    /**
     * @param outputPath
     * @param commitHash
     * @param o
     * @param jsonHandler
     * @return
     */
    static MatrixOutput loadMatrixOutput(String outputPath, String commitHash, Object o, JSONHandler jsonHandler) {
        return (MatrixOutput) loadOutput(outputPath, commitHash, TYPE_MATRIX_MACHINE_OUTPUT, o, jsonHandler);
    }

    /**
     * @param outputPath
     * @param commitHash
     * @param o
     * @param jsonHandler
     * @return
     */
    static PitOutput loadPitOutput(String outputPath, String commitHash, Object o, JSONHandler jsonHandler) {
        return (PitOutput) loadOutput(outputPath, commitHash, TYPE_PIT_MACHINE_OUTPUT, o, jsonHandler);
    }

    /**
     * @param outputPath
     * @param commitHash
     * @param outputType
     * @param o
     * @param jsonHandler
     * @return
     */
    static Object loadOutput(String outputPath, String commitHash, String outputType, Object o, JSONHandler jsonHandler) {
        String outputFileName = getOutputFileName(commitHash, outputType, outputPath);
        boolean isZipFile = getExtension(outputFileName).equals(ZIP_EXTENSION);
        try {
            return isZipFile ?
                    jsonHandler.loadFromJSON(zipFileInputStream(outputFileName), o) :
                    jsonHandler.loadFromJSON(outputFileName, o);
        } catch (Exception e) {
            System.err.println("The " + outputType + " output file for commit " + commitHash + " could not be loaded.");
        }
        return null;
    }

    /**
     * @param rootPath
     * @param zipFileName
     * @return
     * @throws IOException
     */
    static InputStream zipFileInputStream(String rootPath, String zipFileName) throws IOException {
        return zipFileInputStream(Paths.get(rootPath, zipFileName).toString());
    }

    /**
     * @param qualifiedFileName
     * @return
     * @throws IOException
     */
    static InputStream zipFileInputStream(String qualifiedFileName) throws IOException {
        Path zipFilePath = Paths.get(qualifiedFileName);

        ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFilePath));
        zipInputStream.getNextEntry();

        return zipInputStream;

    }

    /**
     * @param object
     * @param filename
     * @param outputPath
     * @param zipOutput
     * @param jsonHandler
     * @throws IOException
     */
    static void saveMachineOutput(Object object, String filename, String outputPath,
                                  boolean zipOutput, JSONHandler jsonHandler) throws IOException {
        if (zipOutput) {
            OutputStream outputStream = Utils.zipFileOutputStream(outputPath, filename);
            jsonHandler.saveToJSON(object, outputStream);
        } else {
            String machineOutputPath = Paths.get(outputPath, filename).toString();
            jsonHandler.saveToJSON(object, machineOutputPath);
        }
    }

    /**
     * @param rootPath
     * @param sourceFile
     * @return
     * @throws IOException
     */
    static OutputStream zipFileOutputStream(String rootPath, String sourceFile) throws IOException {
        String zipFile = sourceFile.replace(Utils.getExtension(sourceFile), ZIP_EXTENSION);

        Path zipFilePath = Paths.get(rootPath, zipFile);

        ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFilePath));
        zipOutputStream.putNextEntry(new ZipEntry(sourceFile));

        return new BufferedOutputStream(zipOutputStream);
    }

    /**
     * @param commit
     * @param type
     * @param directory
     * @return
     */
    static String getOutputFileName(String commit, String type, String directory) {
        List<String> fileList = filesInDirectory(directory);

        if (fileList == null) return null;

        for (String qualifiedName : fileList) {
            String fileName = Paths.get(qualifiedName).getFileName().toString();
            if (fileName.startsWith(type) && fileName.contains(commit)) return qualifiedName;
        }

        return null;
    }

    /**
     * @param directory
     * @return
     */
    static List<String> filesInDirectory(String directory) {
        List<String> filesInDirectory = new ArrayList<>();
        try {
            Files.list(Paths.get(directory))
                    .filter(Files::isRegularFile)
                    .forEach(file -> filesInDirectory.add(file.toString()));
        } catch (IOException e) {
            return null;
        }
        return filesInDirectory;
    }

    /**
     * @param fileName
     * @return
     */
    static String getNameOnly(String fileName) {
        fileName = Paths.get(fileName).getFileName().toString();
        int lastIndexOfDot = fileName.lastIndexOf(".");
        return lastIndexOfDot > 0 ? fileName.substring(0, lastIndexOfDot) : fileName;
    }

    /**
     * @param fileName
     * @return
     */
    static String getExtension(String fileName) {
        fileName = Paths.get(fileName).getFileName().toString();
        int lastIndexOfDot = fileName.lastIndexOf(".");
        return lastIndexOfDot > 0 ? fileName.substring(lastIndexOfDot) : "";
    }

    /**
     * @param projectPath
     * @param pitReportPath
     * @param pitReportPathRelative
     * @return
     */
    static Path getLatestPitReportPath(String projectPath, String pitReportPath, boolean pitReportPathRelative) {

        Path latestPitReportPath = Paths.get((pitReportPathRelative ? projectPath : ""), pitReportPath);

        try {
            latestPitReportPath = Files
                    .list(latestPitReportPath)
                    .filter(Files::isDirectory)
                    .max(Comparator.naturalOrder()).get();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchElementException e) {
            e.printStackTrace();
        }

        System.out.println("\nLatest Pit Report Path: " + latestPitReportPath + "\n");

        return latestPitReportPath;
    }

    /**
     * @param n
     * @return
     */
    static String paddingSpaces(int n) {
        return String.join("", Collections.nCopies(n, " "));
    }

    /**
     * @param projPath
     * @return
     */
    static int projectExists(String projPath) {
        File projDir = new File(projPath);
        if (!projDir.canRead()) return 1;
        if (!projDir.isDirectory()) return 2;

        File projPom = new File(Paths.get(projPath, "pom.xml").toString());
        if (!projPom.canRead() || !projPom.isFile()) return 3;

        if (!canWriteInDirectory(projPath)) return 4;

        return 0;
    }

    /**
     * @param reportsPath
     * @return
     */
    static int reportsPathOK(String reportsPath) {
        File repPath = new File(reportsPath);
        if (!repPath.canRead()) return 1;
        if (!repPath.isDirectory()) return 2;
        if (!canWriteInDirectory(reportsPath)) return 4;
        return 0;
    }

    /**
     * @param directory
     * @return
     */
    static boolean canWriteInDirectory(String directory) {
        try {
            Path testPath = Files.createTempFile(Paths.get(directory), "", "");
            File testFile = new File(testPath.toString());
            testFile.delete();
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    /**
     * @param resourceFile
     * @return
     */
    static String getResourceFileAsString(String resourceFile) {
        BufferedReader bufferedReader = null;
        StringBuilder stringBuilder = new StringBuilder();

        InputStream inputStream = App.class.getClassLoader().getResourceAsStream(resourceFile);

        try {
            bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String inputLine = bufferedReader.readLine();
            if (inputLine != null)
                do {
                    stringBuilder.append(inputLine);
                    inputLine = bufferedReader.readLine();
                    if (inputLine == null) break;
                    stringBuilder.append("\n");
                } while (true);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bufferedReader != null)
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }

        return stringBuilder.toString();
    }


    // Based on Stack Overflow post at https://stackoverflow.com/a/14297352

    /**
     * Sends architecture specific system shutdown command.
     * <p>
     * <b>References:</b><br>
     * Stack Overflow post by Kezz101.
     * [Online]. Available: <a href="https://stackoverflow.com/a/14297352" target="_blank">
     * https://stackoverflow.com/a/14297352</a>
     *
     * @param timeout
     * @return
     */
    static String systemShutdownCommand(int timeout) {
        String shutdownCommand = null;

        if (OS.IS_AIX)
            shutdownCommand = "shutdown -Fh " + timeout;
        else if (OS.IS_FREE_BSD || OS.IS_LINUX || OS.IS_MAC || OS.IS_NET_BSD || OS.IS_OPEN_BSD)
            shutdownCommand = "shutdown -h " + timeout;
        else if (OS.IS_HP_UX)
            shutdownCommand = "shutdown -hy " + timeout;
        else if (OS.IS_IRIX)
            shutdownCommand = "shutdown -y -g " + timeout;
        else if (OS.IS_SOLARIS || OS.IS_SUN_OS)
            shutdownCommand = "shutdown -y -i5 -g" + timeout;
        else if (OS.IS_WINDOWS)
            shutdownCommand = "shutdown -s -t " + (timeout * 60);

        return shutdownCommand;
    }

    /**
     * @param path
     * @return
     * @throws IOException
     */
    static List<String> readAllLines(Path path) throws IOException {
        List<String> allLines = new ArrayList<>();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(Files.newInputStream(path)));
        while (bufferedReader.ready()) {
            allLines.add(bufferedReader.readLine());
        }
        bufferedReader.close();
        return allLines;
    }
}