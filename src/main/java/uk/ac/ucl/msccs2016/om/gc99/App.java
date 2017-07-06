package uk.ac.ucl.msccs2016.om.gc99;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidParameterException;

public class App {

    public static void main(String[] args) throws Exception {

        System.out.println();

        String projPath = "";
        String pitStatReportsPath = "target/pitstat-reports";
        boolean pitStatReportsPathRelative = true;
        boolean noTimestamp = false;
        String newCommit = "";
        String oldCommit = "HEAD";
        int maxRollbacks = 1;

        boolean endCommitArg = false;

//        String projPath = "D:/X/github/joda-time";
////        String projPath = "D:/X/projIdea/MavenTest";
////        String projPath = "D:/X/github/commons-collections";
//
//        String pitStatReportsPath = "target/pitstat-reports";
//        boolean pitStatReportsPathRelative = true;
//        boolean noTimestamp = false;
//
//        String newCommit = "HEAD";
//        String oldCommit = "HEAD~";
//
//        int maxRollbacks = 50;


        int i = -1;
        while (++i < args.length) {

            String arg = args[i];

            switch (arg) {
                case "--project-path":
                    try {
                        projPath = args[++i];
                    } catch (ArrayIndexOutOfBoundsException e) {
                        System.out.println("No project path specified.");
                        System.exit(99);
                    }
                    switch (projectExists(projPath)) {
                        case 1:
                            System.out.println("Cannot access the specified project directory: " + projPath);
                            System.exit(1);
                        case 2:
                            System.out.println("The specified project path is not a directory: " + projPath);
                            System.exit(2);
                        case 3:
                            System.out.println("Cannot access pom.xml in the specified project directory: " + projPath);
                            System.exit(3);
                        case 4:
                            System.out.println("Cannot write inside the specified reports directory: " + projPath);
                            System.exit(4);
                    }
                    break;
                case "--reports-path":
                    try {
                        pitStatReportsPath = args[++i];
                    } catch (ArrayIndexOutOfBoundsException e) {
                        System.out.println("No reports path specified.");
                        System.exit(99);
                    }
                    switch (reportsPathOK(pitStatReportsPath)) {
                        case 1:
                            System.out.println("Cannot access the specified reports directory: " + projPath);
                            System.exit(1);
                        case 2:
                            System.out.println("The specified reports path is not a directory: " + projPath);
                            System.exit(2);
                        case 3:
                            System.out.println("Cannot write inside the specified reports directory: " + projPath);
                            System.exit(4);
                    }
                    pitStatReportsPathRelative = false;
                    break;
                case "--start-commit":
                    try {
                        newCommit = args[++i];
                    } catch (ArrayIndexOutOfBoundsException e) {
                        System.out.println("No start commit specified.");
                        System.exit(99);
                    }
                    break;
                case "--end-commit":
                    try {
                        oldCommit = args[++i];
                    } catch (ArrayIndexOutOfBoundsException e) {
                        System.out.println("No end commit specified.");
                        System.exit(99);
                    }
                    endCommitArg = true;
                    break;
                case "--rollbacks":
                    String rollbacksString = null;
                    try {
                        rollbacksString = args[++i];
                        maxRollbacks = Integer.valueOf(rollbacksString);
                        if (maxRollbacks < 1) throw new InvalidParameterException();
                    } catch (ArrayIndexOutOfBoundsException e) {
                        System.out.println("No end commit specified.");
                        System.exit(99);
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid number of rollbacks: " + rollbacksString);
                        System.exit(5);
                    } catch (InvalidParameterException e) {
                        System.out.println("Invalid number of rollbacks. Cannot be less than 1.");
                        System.exit(6);
                    }
                    if (endCommitArg) System.out.println("WARNING: end-commit overrides rollbacks; specified rollbacks value is ignored.\n");
                    break;
                case "--no-timestamp":
                    noTimestamp = true;
                    break;
                default:
                    System.out.println("Invalid argument: " + arg);
                    System.exit(99);
            }
        }


//        System.out.println("projPath: " + projPath);
//        System.out.println("pitStatReportsPath: " + pitStatReportsPath);
//        System.out.println("pitStatReportsPathRelative: " + pitStatReportsPathRelative);
//        System.out.println("noTimestamp: " + noTimestamp);
//        System.out.println("oldCommit: " + oldCommit);
//        System.out.println("newCommit: " + newCommit);
//        System.out.println("maxRollbacks: " + maxRollbacks);


        MainWorker worker = new MainWorker(
                projPath,
                pitStatReportsPath,
                pitStatReportsPathRelative,
                noTimestamp,
                oldCommit,
                newCommit,
                maxRollbacks
        );

        worker.doWork();

    }

    private static int projectExists(String projPath) {
        File projDir = new File(projPath);
        if (!projDir.canRead()) return 1;
        if (!projDir.isDirectory()) return 2;

        File projPom = new File(Paths.get(projPath, "pom.xml").toString());
        if (!projPom.canRead() || !projPom.isFile()) return 3;

        if (!canWriteInDirectory(projPath)) return 4;

        return 0;
    }

    private static int reportsPathOK(String reportsPath) {
        File repPath = new File(reportsPath);
        if (!repPath.canRead()) return 1;
        if (!repPath.isDirectory()) return 2;
        if (!canWriteInDirectory(reportsPath)) return 4;
        return 0;
    }

    private static boolean canWriteInDirectory(String directory) {
        try {
            Path testPath = Files.createTempFile(Paths.get(directory), "", "");
            File testFile = new File(testPath.toString());
            testFile.delete();
        } catch (IOException e) {
            return false;
        }
        return true;
    }


}
