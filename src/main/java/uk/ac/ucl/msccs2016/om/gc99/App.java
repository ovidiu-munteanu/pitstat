package uk.ac.ucl.msccs2016.om.gc99;

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
        int threadsNo = 1;
        int shutdownTimeout = 1;

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
                case "-PP":
                case "--project-path":
                    try {
                        projectPath = args[++i];
                    } catch (ArrayIndexOutOfBoundsException e) {
                        System.err.println("No project path specified.");
                        systemExit(99);
                    }
                    switch (Utils.projectExists(projectPath)) {
                        case 1:
                            System.err.println("Cannot access the specified project directory: " + projectPath);
                            if (OS.IS_WINDOWS)
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
                    switch (Utils.reportsPathOK(pitStatReportsPath)) {
                        case 1:
                            System.err.println("Cannot access the specified reports directory: " + projectPath);
                            if (OS.IS_WINDOWS)
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
                case "-T":
                case "--threads":
                    String threadsString = null;
                    try {
                        threadsString = args[++i];
                        threadsNo = Integer.valueOf(threadsString);
                        if (threadsNo < 1 || threadsNo > 8) throw new InvalidParameterException();
                    } catch (ArrayIndexOutOfBoundsException e) {
                        System.err.println("Number of threads not specified.");
                        systemExit(99);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid number of threads: " + threadsString);
                        systemExit(5);
                    } catch (InvalidParameterException e) {
                        System.err.println("Invalid number of threads - may be between 1 and 8.");
                        systemExit(6);
                    }
                    break;
                case "-NH":
                case "--no-human":
                    if (noMachine) {
                        System.err.println("Cannot specify both no human and no machine output.\n" +
                                "Please try again using only one of the two options.");
                        systemExit(99);
                    }
                    noHuman = true;
                    break;
                case "-NM":
                case "--no-machine":
                    if (noHuman) {
                        System.err.println("Cannot specify both no machine and no human output.\n" +
                                "Please try again using only one of the two options.");
                        systemExit(99);
                    }
                    noMachine = true;
                    break;
                case "-Z":
                case "--zip-output":
                    zipOutput = true;
                    break;
                case "-NT":
                case "--no-timestamp":
                    createTimestampFolder = false;
                    break;
                case "-S":
                case "--shutdown":
                    shutdown = true;
                    try {
                        String timeoutString = args[++i];
                        float timeout = Float.valueOf(timeoutString);
                        shutdownTimeout = (int) timeout;
                        if (shutdownTimeout < 1)
                            throw new InvalidParameterException("<1");
                        else if (timeout - shutdownTimeout != 0)
                            throw new InvalidParameterException();
                    } catch (ArrayIndexOutOfBoundsException e) {
                        // that's ok, the timeout value is optional
                    } catch (NumberFormatException e) {
                        // we'll assume that the timeout value was omitted and we got to the next argument;
                        // we then need to roll back one position in the arguments array
                        i--;
                    } catch (InvalidParameterException e) {
                        System.err.print("Invalid timeout value. ");
                        if (e.getMessage().equals("<1"))
                            System.err.println("Cannot be less than 1 minute.");
                        else
                            System.err.println("The timeout value should be expressed in whole minutes.");
                        systemExit(6);
                    }
                    break;
                case "-H":
                case "--help":
                    printHelp();
                    systemExit(0);
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
                maxRollbacks,
                threadsNo
        );


        if (worker.validStartEndCommits()) worker.doWork();

        // shutdown in 1 minute
        if (shutdown) Runtime.getRuntime().exec(Utils.systemShutdownCommand(shutdownTimeout));
        System.exit(0);
    }


    private static void printHelp() {
        System.out.print(Utils.getResourceFileAsString("help.txt"));
    }


    private static void checkCommitLength(String commit) {
        if (commit.length() < 4) {
            System.err.println("The commit reference you entered is too short." +
                    "\nTip: a commit reference is at least 4 characters long, e.g. HEAD");
            systemExit(99);
        } else if (commit.length() > 40) {
            System.err.println("The commit reference you entered is too long." +
                    "\nTip: a full commit hash is 40 characters long");
            systemExit(99);
        }
    }


    static void systemExit(int exitCode) {
        System.out.println();
        System.exit(exitCode);
    }
}
