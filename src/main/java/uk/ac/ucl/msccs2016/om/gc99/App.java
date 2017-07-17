package uk.ac.ucl.msccs2016.om.gc99;

import org.apache.commons.lang3.SystemUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidParameterException;

public class App {

    public static void main(String[] args) throws Exception {

        System.out.println();

        String projectPath = "";
        String pitStatReportsPath = "target/pitstat-reports";
        boolean pitStatReportsPathRelative = true;
        boolean createTimestampFolder = true;
        String startCommit = "";
        String endCommit = null;
        int maxRollbacks = 1;

        boolean endCommitArg = false, rollbacksArg = false;

        boolean noHuman = false;
        boolean noMachine = false;
        boolean zipOutput = false;
        boolean shutdown = false;

        if (args.length == 0) {
            printHelp();
            systemExit(0);
        }

        int i = -1;
        while (++i < args.length) {

            String arg = args[i];

            switch (arg) {
                case "-H":
                case "--help":
                    printHelp();
                    systemExit(0);
                    break;
                case "-PP":
                case "--project-path":
                    try {
                        projectPath = args[++i];
                    } catch (ArrayIndexOutOfBoundsException e) {
                        System.err.println("No project path specified.");
                        systemExit(99);
                    }
                    switch (projectExists(projectPath)) {
                        case 1:
                            System.err.println("Cannot access the specified project directory: " + projectPath);
                            if (SystemUtils.IS_OS_WINDOWS)
                                System.out.println("Tip: in Windows you need to write the path between inverted commas or use \"/\" instead of \"\\\"");
                            systemExit(1);
                        case 2:
                            System.err.println("The specified project path is not a directory: " + projectPath);
                            systemExit(2);
                        case 3:
                            System.err.println("Cannot access pom.xml in the specified project directory: " + projectPath);
                            systemExit(3);
                        case 4:
                            System.err.println("Cannot write inside the specified reports directory: " + projectPath);
                            systemExit(4);
                    }
                    break;
                case "-RP":
                case "--reports-path":
                    try {
                        pitStatReportsPath = args[++i];
                    } catch (ArrayIndexOutOfBoundsException e) {
                        System.err.println("No reports path specified.");
                        systemExit(99);
                    }
                    switch (reportsPathOK(pitStatReportsPath)) {
                        case 1:
                            System.err.println("Cannot access the specified reports directory: " + projectPath);
                            if (SystemUtils.IS_OS_WINDOWS)
                                System.out.println("Tip: in Windows you need to write the path between inverted commas or use \"/\" instead of \"\\\"");
                            systemExit(1);
                        case 2:
                            System.err.println("The specified reports path is not a directory: " + projectPath);
                            systemExit(2);
                        case 3:
                            System.err.println("Cannot write inside the specified reports directory: " + projectPath);
                            systemExit(4);
                    }
                    pitStatReportsPathRelative = false;
                    break;
                case "-SC":
                case "--start-commit":
                    try {
                        startCommit = args[++i];
                    } catch (ArrayIndexOutOfBoundsException e) {
                        System.err.println("No start commit specified.");
                        systemExit(99);
                    }
                    checkCommitLength(startCommit);
                    break;
                case "-EC":
                case "--end-commit":
                    if (rollbacksArg) {
                        System.err.println("Cannot specify both end commit and rollbacks.\n" +
                                "Please try again using only one of the two options.");
                        systemExit(99);
                    }
                    try {
                        endCommit = args[++i];
                    } catch (ArrayIndexOutOfBoundsException e) {
                        System.err.println("No end commit specified.");
                        systemExit(99);
                    }
                    checkCommitLength(endCommit);
                    maxRollbacks = -1;
                    endCommitArg = true;
                    break;
                case "-R":
                case "--rollbacks":
                    if (endCommitArg) {
                        System.err.println("Cannot specify both rollbacks and end commit.\n" +
                                "Please try again using only one of the two options.");
                        systemExit(99);
                    }
                    String rollbacksString = null;
                    try {
                        rollbacksString = args[++i];
                        if (rollbacksString.equals("max")) {
                            maxRollbacks = Integer.MAX_VALUE;
                        } else {
                            maxRollbacks = Integer.valueOf(rollbacksString);
                            if (maxRollbacks < 1) throw new InvalidParameterException();
                        }
                    } catch (ArrayIndexOutOfBoundsException e) {
                        System.err.println("No end commit specified.");
                        systemExit(99);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid number of rollbacks: " + rollbacksString);
                        systemExit(5);
                    } catch (InvalidParameterException e) {
                        System.err.println("Invalid number of rollbacks. Cannot be less than 1.");
                        systemExit(6);
                    }
                    rollbacksArg = true;
                    break;
                case "-NH":
                case "--no-human":
                    if (noMachine) {
                        System.err.println("Cannot specify both no human and no machine output.\n" +
                                "Please try again using only one of the two options.");
                        systemExit(99);
                    }
                    noHuman = true;
                case "-NM":
                case "--no-machine":
                    if (noHuman) {
                        System.err.println("Cannot specify both no machine and no human output.\n" +
                                "Please try again using only one of the two options.");
                        systemExit(99);
                    }
                    noMachine = true;
                case "-Z":
                case "--zip-output":
                    if (noMachine){
                        System.err.println("Cannot specify zip output with no machine readable output - " +
                                "zip compression is available only for machine readable output.\n" +
                                "Please try again using only one of the two options.");
                        systemExit(99);
                    }
                    zipOutput = true;
                    break;
                case "-NT":
                case "--no-timestamp":
                    createTimestampFolder = false;
                    break;
                case "-S":
                case "--shutdown":
                    shutdown = true;
                    break;
                default:
                    System.err.println("Invalid argument: " + arg + "\n");
                    printHelp();
                    systemExit(99);
            }
        }

        if (startCommit.equals(endCommit)) {
            System.err.println("The start and end commits cannot be the same.");
            systemExit(99);
        }

        MainWorker worker = new MainWorker(
                projectPath,
                pitStatReportsPath,
                pitStatReportsPathRelative,
                createTimestampFolder,
                noHuman,
                zipOutput,
                noMachine,
                startCommit,
                endCommit,
                maxRollbacks
        );


        if (worker.validStartEndCommits()) worker.doWork();

        // shutdown in 1 minute
        if (shutdown) Runtime.getRuntime().exec(systemShutdownCommand(1));
        System.exit(0);

    }

    private static void printHelp(){
        System.out.print(getResourceFileAsString("help.txt"));
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

    private static void checkCommitLength(String commit) {
        if (commit.length() < 4) {
            System.err.println("The commit reference you entered is too short.\nTip: a commit reference is at least 4 characters long, e.g. HEAD");
            systemExit(99);
        } else if (commit.length() > 40) {
            System.err.println("The commit reference you entered is too long.\nTip: a full commit hash is 40 characters long");
            systemExit(99);
        }
    }

    private static String getResourceFileAsString(String resourceFile) {
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

    static void systemExit(int exitCode) {
        System.out.println();
        System.exit(exitCode);
    }


    private static String systemShutdownCommand(int timeout) {
        String shutdownCommand = null;

        if (SystemUtils.IS_OS_AIX)
            shutdownCommand = "shutdown -Fh " + timeout;
        else if (SystemUtils.IS_OS_FREE_BSD ||
                SystemUtils.IS_OS_LINUX ||
                SystemUtils.IS_OS_MAC ||
                SystemUtils.IS_OS_MAC_OSX ||
                SystemUtils.IS_OS_NET_BSD ||
                SystemUtils.IS_OS_OPEN_BSD)
            shutdownCommand = "shutdown -h " + timeout;
        else if (SystemUtils.IS_OS_HP_UX)
            shutdownCommand = "shutdown -hy " + timeout;
        else if (SystemUtils.IS_OS_IRIX)
            shutdownCommand = "shutdown -y -g " + timeout;
        else if (SystemUtils.IS_OS_SOLARIS ||
                SystemUtils.IS_OS_SUN_OS)
            shutdownCommand = "shutdown -y -i5 -g" + timeout;
        else if (SystemUtils.IS_OS_WINDOWS)
            shutdownCommand = "shutdown -s -t " + (timeout * 60);

        return shutdownCommand;
    }

}
