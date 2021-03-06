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

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import static uk.ac.ucl.msccs2016.om.gc99.Utils.getNameOnly;
import static uk.ac.ucl.msccs2016.om.gc99.Utils.paddingSpaces;

/**
 * Main worker class of the application - comprises doWork(), the business logic method,
 * as well as the primary utility methods.
 * <p>
 * <b>References &amp; libraries:</b><br>
 * Apache Maven Invoker.
 * [Online]. Available: <a href="https://maven.apache.org/shared/maven-invoker/" target="_blank">
 * https://maven.apache.org/shared/maven-invoker/</a>
 */
class MainWorker implements Worker {

    private final CommandExecutor commandExecutor;
    private final Invoker mavenInvoker;
    private final InvocationRequest invocationRequest;
    private final JSONHandler jsonHandler;
    private final DocumentBuilder documentBuilder;
    private final XPath xPath;

    private final String projectPath, pitStatReportsPath;
    private final boolean pitStatReportsPathRelative, createTimestampDirectory;
    private final boolean humanOutput, zipOutput, machineOutput;
    private final String startCommit, endCommit;
    private final int threadsNo;
    private int maxRollbacks;

    private boolean resetIndex, resetUntracked;
    private String indexCommitHash, notStagedCommitHash, tempHead;

    private List<String> commitsHashList;
    private String originalGitBranch, startCommitHash, endCommitHash;
    private int indexOfStartCommit;

    private String outputPath, startTime, outputTime;

    private StringBuilder diffHumanOutput;

    private boolean isEndCommit;
    private HashMap<String, ChangedFile> changedFiles;
    private ChangedMutations changedMutations;

    private String childCommitHash, currentCommitHash, parentCommitHash;

    private PitOutput childPitOutput, currentPitOutput;
    private DiffOutput childDiffOutput, currentDiffOutput;

    /**
     * @param projectPath                String, holds the path of the target project
     * @param pitStatReportsPath         String, holds the paths where the PitStat reports will be saved
     * @param pitStatReportsPathRelative boolean, records whether the path for the PitStat reports is absolute
     *                                   or relative to the project path (true if path is relative)
     * @param createTimestampDirectory   boolean, records whether the PitStat reports are to be placed into a
     *                                   timestamped subdirectory (true is timestamp directory is to be created)
     * @param noHuman                    boolean, records whether human readable output should be created (false
     *                                   if human readable output is to be created)
     * @param zipOutput                  boolean, records whether machine readable output is to be saver as zip
     *                                   compressed archive
     * @param noMachine                  boolean, records whether machine readable output is to be created; only
     *                                   relevant where both human and machine readable output is available (false
     *                                   if machine readable output is to be created)
     * @param startCommit                String, holds the hash string of the start commit (most recent)
     * @param endCommit                  String, holds the hash string of the end commit (oldest)
     * @param maxRollbacks               int, holds the number of rollback to be performed
     * @param threadsNo                  int, holds the number of threads that will be passed to PIT
     * @throws Exception
     */
    MainWorker(String projectPath,
               String pitStatReportsPath,
               boolean pitStatReportsPathRelative,
               boolean createTimestampDirectory,
               boolean noHuman,
               boolean zipOutput,
               boolean noMachine,
               String startCommit,
               String endCommit,
               int maxRollbacks,
               int threadsNo)
            throws Exception {

        startTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN));

        commandExecutor = new CommandExecutor();
        mavenInvoker = new DefaultInvoker();

        String mavenHome = System.getenv(MAVEN_HOME);
        if (mavenHome != null) {
            mavenInvoker.setMavenHome(new File(mavenHome));
        } else {
            System.err.println("Maven home environment variable not set.");
            System.out.println("Tip: the M2_HOME environment variable needs to point to the path of your maven installation.");
            App.systemExit(98);
        }

        invocationRequest = new DefaultInvocationRequest();
        invocationRequest.setBatchMode(true);

        documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        xPath = XPathFactory.newInstance().newXPath();

        jsonHandler = new JSONHandler(PRETTY_PRINTING);

        this.projectPath = projectPath;
        this.pitStatReportsPath = pitStatReportsPath;
        this.pitStatReportsPathRelative = pitStatReportsPathRelative;
        this.createTimestampDirectory = createTimestampDirectory;
        this.humanOutput = !noHuman;
        this.zipOutput = zipOutput;
        this.machineOutput = !noMachine;
        this.startCommit = startCommit;
        this.endCommit = endCommit;
        this.maxRollbacks = maxRollbacks;
        this.threadsNo = threadsNo;
    }

    /**
     * Primary Business Logic Method.
     *
     * @throws Exception
     */
    void doWork() throws Exception {

        originalGitBranch = GitUtils.getGitBranch(Git.HEAD, projectPath, commandExecutor);
        String pitStatBranch = GitUtils.checkoutPitStatBranch(startTime, projectPath, commandExecutor);

        validateBoundaryCommits();

        if (pitStatReportsPathRelative || createTimestampDirectory) {

            outputPath = Utils.createOutputDirectory(pitStatReportsPath, pitStatReportsPathRelative,
                    startTime, createTimestampDirectory, projectPath);

            if (outputPath == null) App.systemExit(98);

        } else {
            outputPath = pitStatReportsPath;
        }

        if (indexOfStartCommit > 0) GitUtils.gitCheckout(startCommitHash, projectPath, commandExecutor);

        int currentCommitIndex = indexOfStartCommit;
        int parentCommitIndex = currentCommitIndex + 1;
        childCommitHash = null;
        currentCommitHash = startCommitHash;
        parentCommitHash = parentCommitIndex != commitsHashList.size() ?
                commitsHashList.get(parentCommitIndex) : "currently at initial commit -> no parent hash";

        int currentRollback = 0;

        do {
            System.out.println("\nRollback: " + currentRollback++);

            String currentCommitOutput = "Current commit hash: " + hashToOutput(currentCommitHash, true) + "\n";
            String parentCommitOutput = "Parent  commit hash: " + hashToOutput(parentCommitHash, true) + "\n\n";

            System.out.print(currentCommitOutput);
            System.out.print(parentCommitOutput);

            isEndCommit = currentCommitHash.equals(endCommitHash);

            currentDiffOutput = null;
            if (isEndCommit) {
                System.out.println("Currently at end commit for this run (" +
                        hashToOutput(currentCommitHash) + ") -> skipping git diff\n");
            } else {
                diffHumanOutput = new StringBuilder();
                diffHumanOutput.append(currentCommitOutput);
                diffHumanOutput.append(parentCommitOutput);

                runGitDiff();
            }

            List<String> oldTempFiles = Utils.listTempFiles();

            currentPitOutput = null;
            Thread thread = new Thread(() ->
            {
                try {
                    runPitMutationTesting();
                } catch (Exception e) {
                    System.err.println("\nPitest Mutations Testing - Commit " + currentCommitHash);
                    System.err.println("runPitMutationTesting(): has thrown and exception - stack trace is included below:\n");
                    e.printStackTrace();
                }
            });
            thread.start();
            thread.join();

            Utils.deleteNewTempFiles(oldTempFiles);

            if (childCommitHash != null) {
                if (childPitOutput != null && currentPitOutput != null) {
                    changedMutations = null;

                    thread = new Thread(() ->
                    {
                        try {

                            // The changes output is built at the same time as the Pit matrix!
                            // When a mutation is found that is not on the diagonal of the Pit matrix, then it is
                            // a change so a new entry needs to be added to the changes record.

                            // NOTE 1: a pit matrix is only produced if the Pit mutation test was run for both the
                            // child and the current commit (i.e. Pit outputs are available for the child and the
                            // current commit)

                            // NOTE 2: similarly, a change record is only produced if there is a Pit matrix can be
                            // produced (i.e. Pit outputs were produced for both the previous and the current commit).
                            // Additionally, a change record is only produced it there are actually any changes in the
                            // Pit matrix (i.e. there are values outside the matrix diagonal)

                            // NOTE 3: the Pit matrix is named using the child commit hash, where the child commit is
                            // the 'new' commit and the current commit is the 'old' commit;

                            // NOTE 4: similarly, the change record is named using the child commit hash in the same
                            // way as the matrix is named

                            runPitMatrixAnalysis();

                        } catch (IOException e) {
                            System.err.println();
                            System.err.println("Pitest Mutations Testing - Commit " + currentCommitHash);
                            System.err.println("runPitMatrixAnalysis(): has thrown and exception - stack trace is included below:\n");
                            e.printStackTrace();
                            System.exit(0);
                        }
                    });
                    thread.start();
                    thread.join();

                } else {
                    System.err.println("\nPit Matrix Analysis - Child   (new) commit " + childCommitHash);
                    System.err.println("                      Current (old) commit " + currentCommitHash);
                    System.err.println("Skipping analysis: childPitOutput   == " + childPitOutput);
                    System.err.println("                   currentPitOutput == " + currentPitOutput + "\n");
                }
            }

            thread = new Thread(() ->
            {
                try {
                    mavenClean();
                } catch (Exception e) {
                    System.err.println("\nPitest Mutations Testing - Commit " + currentCommitHash);
                    System.err.println("mavenClean(): has thrown and exception - stack trace is included below:\n");
                    e.printStackTrace();
                }
            });
            thread.start();
            thread.join();

            if (!isEndCommit) {
                childCommitHash = currentCommitHash;
                currentCommitHash = commitsHashList.get(++currentCommitIndex);
                parentCommitHash = ++parentCommitIndex == commitsHashList.size() ?
                        "currently at initial commit -> no parent hash" : commitsHashList.get(parentCommitIndex);

                thread = new Thread(() -> GitUtils.gitCheckout(currentCommitHash, projectPath, commandExecutor, true));
                thread.start();
                thread.join();

                childDiffOutput = currentDiffOutput;
                childPitOutput = currentPitOutput;
            }

        } while (!isEndCommit && currentRollback <= maxRollbacks);

        GitUtils.gitCheckout(tempHead, projectPath, commandExecutor, true);

        if (resetUntracked) GitUtils.gitResetMixedTo(Git.HEAD_PARENT, projectPath, commandExecutor);
        if (resetIndex) GitUtils.gitResetSoftTo(Git.HEAD_PARENT, projectPath, commandExecutor);

        GitUtils.checkoutOriginalBranch(originalGitBranch, projectPath, commandExecutor);
        GitUtils.deletePitStatBranch(pitStatBranch, projectPath, commandExecutor);
    }

    /**
     * Utility method - executes a maven clean goal on the target project
     * (deletes the target project build folder and all of its contents).
     *
     * @throws Exception
     */
    private void mavenClean() throws Exception {

        File defaultPom = new File(Paths.get(projectPath, POM_FILE).toString());

        invocationRequest.setPomFile(defaultPom);
        invocationRequest.setGoals(Arrays.asList(MVN_GOAL_CLEAN));

        InvocationResult result = mavenInvoker.execute(invocationRequest);

        if (result.getExitCode() != 0) {
            if (result.getExecutionException() != null) {
                throw new Exception("Maven invocation failed.", result.getExecutionException());
            } else {
                throw new Exception("Maven invocation failed. Exit code: " + result.getExitCode());
            }
        }
    }

    /**
     * Utility method - checks if the user entered boundary commits are valid.<br>
     * Note: This methods is used also when the user enters the number of rollbacks, thus no end commit.
     */
    private void validateBoundaryCommits() {

        if (startCommit.equals(Git.INDEX))

            if (GitUtils.indexNotEmpty(projectPath, commandExecutor)) {

                tempHead = startCommitHash = indexCommitHash = GitUtils.commitIndex(projectPath, commandExecutor);
                resetIndex = true;

                if (GitUtils.notStagedNotEmpty(projectPath, commandExecutor)) {
                    tempHead = notStagedCommitHash = GitUtils.commitUntracked(projectPath, commandExecutor);
                    resetUntracked = true;
                }

            } else {
                System.err.println("The index is empty - there are no staged changes.");
                App.systemExit(99);
            }

        else if (startCommit.equals(Git.NOT_STAGED))

            if (GitUtils.notStagedNotEmpty(projectPath, commandExecutor)) {

                if (GitUtils.indexNotEmpty(projectPath, commandExecutor)) {
                    indexCommitHash = GitUtils.commitIndex(projectPath, commandExecutor);
                    resetIndex = true;
                }

                tempHead = startCommitHash = notStagedCommitHash = GitUtils.commitUntracked(projectPath, commandExecutor);
                resetUntracked = true;

            } else {
                System.err.println("There are no changes not yet added to the staging area.");
                App.systemExit(99);
            }


        commitsHashList = GitUtils.getCommitsHashList(projectPath, commandExecutor);


        if (startCommitHash == null) {
            startCommitHash = GitUtils.parseCommit(startCommit, originalGitBranch,
                    commitsHashList, projectPath, commandExecutor);
            resetIndex = resetUntracked = false;
        }


        indexOfStartCommit = commitsHashList.indexOf(startCommitHash);
        int indexOfInitialCommit = commitsHashList.size() - 1;


        if (maxRollbacks > 0 && endCommit == null) {

            int indexOfEndCommit = maxRollbacks == Integer.MAX_VALUE ?
                    indexOfInitialCommit : indexOfStartCommit + maxRollbacks;

            if (indexOfEndCommit > indexOfInitialCommit) {
                System.err.println("The number of rollbacks is greater than the history of this branch.");
                App.systemExit(99);
            }

            endCommitHash = commitsHashList.get(indexOfEndCommit);

        } else {

            if (endCommit.equals(Git.INITIAL_COMMIT)) {
                endCommitHash = commitsHashList.get(indexOfInitialCommit);
            } else {
                endCommitHash = GitUtils.parseCommit(
                        endCommit, originalGitBranch,
                        commitsHashList, projectPath, commandExecutor);
            }

            maxRollbacks = commitsHashList.indexOf(endCommitHash) - indexOfStartCommit;
        }

        if (maxRollbacks < 0) {
            System.err.println("Start commit is older than end commit.");
            System.out.println("Tip: pitStat rolls back from the start commit towards the end commit");
            App.systemExit(99);
        }
    }

    /**
     * Primary Utility Method - business logic used to record file differences between
     * subsequent commits and generate the mapping between the new and the old commit.
     *
     * @throws IOException
     */
    private void runGitDiff() throws IOException {

        // git diff name-status between previous and current commit
        List<String> nameStatusList = GitUtils.gitDiffNameStatus(currentCommitHash, parentCommitHash,
                projectPath, commandExecutor);

        int hashMapCapacity = (int) (nameStatusList.size() * 1.35);
        changedFiles = new HashMap<>(hashMapCapacity);

        if (nameStatusList.size() == 0) {

            String noDifferenceOutput = "No difference between " + hashToOutput(currentCommitHash) +
                    " and " + hashToOutput(parentCommitHash) + "\n";

            diffHumanOutput.append(noDifferenceOutput);
            System.out.println(noDifferenceOutput);
        } else {

            for (String nameStatusLine : nameStatusList) {

                String diffStatus = Character.toString(nameStatusLine.charAt(0));

                String[] splitLine = nameStatusLine.split("\\s+");

                String changedFile = splitLine[1], newFile = null;

                StringBuilder diffStatusOutput = new StringBuilder();
                boolean defaultCase = false;

                switch (diffStatus) {
                    case DIFF_STATUS_ADDED:
                        diffStatusOutput.append("Added file:    ");
                        break;
                    case DIFF_STATUS_DELETED:
                        diffStatusOutput.append("Deleted file:  ");
                        break;
                    case DIFF_STATUS_MODIFIED:
                        diffStatusOutput.append("Modified file: ");
                        break;
                    case DIFF_STATUS_COPIED:
                        newFile = splitLine[2];
                        diffStatusOutput.append("Copied file:   ");
                        break;
                    case DIFF_STATUS_RENAMED:
                        newFile = splitLine[2];
                        diffStatusOutput.append("Renamed file:  ");
                        break;
                    default:
                        defaultCase = true;
                        diffStatusOutput.append("Change type " + diffStatus + " unsupported: ");
                        for (int i = 1; i < splitLine.length; i++) {
                            if (i > 1) diffStatusOutput.append(" --> ");
                            diffStatusOutput.append(splitLine[i]);
                        }
                }

                if (!defaultCase) diffStatusOutput.append(changedFile);
                if (newFile != null) diffStatusOutput.append(" --> " + newFile);

                diffStatusOutput.append("\n");

                diffHumanOutput.append(diffStatusOutput);
                System.out.print(diffStatusOutput);

                if (newFile == null && !diffStatus.equals(DIFF_STATUS_DELETED)) newFile = changedFile;
                if (diffStatus.equals(DIFF_STATUS_ADDED)) changedFile = null;

                List<ChangedFile.LineOfCode> mergedLines = null;
                List<Integer> newLinesMap = null, oldLinesMap = null;


                boolean fileIsJar = !diffStatus.equals(DIFF_STATUS_DELETED) && Utils.getExtension(newFile).equals(".jar");


                if ((DIFF_STATUS_ADDED + DIFF_STATUS_COPIED + DIFF_STATUS_DELETED).contains(diffStatus) || fileIsJar) {


                    // If the file is added, copied or deleted it would be superfluous to list all its lines as added
                    // or deleted so the line diff is skipped.

                    // Line mapping of copied files is not done since as far as the change in mutations and mutation
                    // test results is concerned, a copy of an existing file is the same as adding a new file; even
                    // if the copy and the original are identical in content, the copy will have a different name
                    // and/or a different path; therefore, it is a newly added file and even if the mutations and
                    // mutation test results are the same as in the original file, it cannot be said that this is the
                    // same set of mutations - for all intents and purposes it is a new set of mutations applied to a
                    // new file; therefore, when searching for changes in mutations and mutation test results, these
                    // mutations will be classed as new and thus the line difference and line mapping between the copy
                    // and original file is irrelevant

                    // There have also been found situations where the changed file is a jar and not a source file.
                    // In this case it would also be superfluous to run a line diff.

                    diffHumanOutput.append("\n");
                    System.out.println();

                } else if ((DIFF_STATUS_MODIFIED + DIFF_STATUS_RENAMED).contains(diffStatus)) {

                    List<String> mapFileLines = Utils.readAllLines(Paths.get(projectPath, newFile));

                    mapFileLines.add(0, null);
                    int mapFileLinePointer = 1;

                    // git diff between previous and current version of the specific file
                    List<String> diffOutputLines =
                            GitUtils.gitDiff(changedFile, newFile,
                                    currentCommitHash, parentCommitHash, projectPath, commandExecutor);

                    ListIterator<String> diffOutputIterator = diffOutputLines.listIterator();

                    // Skip git diff header lines, i.e. skip until the first line starting with @@ is found
                    while (diffOutputIterator.hasNext() && !diffOutputIterator.next().startsWith("@@")) ;

                    // If the file was copied or renamed but not modified (100% similarity) then the while loop above
                    // will have reached the end of the diff output so we need to continue with the next changed file
                    if (!diffOutputIterator.hasNext()) {
                        ChangedFile changedFileEntry = new ChangedFile(newFile, changedFile, diffStatus,
                                mergedLines, newLinesMap, oldLinesMap);
                        changedFiles.put(changedFile, changedFileEntry);
                        continue;
                    }

                    // We consumed the "@@" line in the while loop above so we need to go back one iteration
                    diffOutputIterator.previous();

                    mergedLines = new ArrayList<>();
                    newLinesMap = new ArrayList<>();
                    oldLinesMap = new ArrayList<>();

                    mergedLines.add(null);
                    newLinesMap.add(null);
                    oldLinesMap.add(null);

                    int diffNewPointer, diffNewLinesNo;
                    int oldFileLinePointer = 1, newFileLinePointer = 1, lineOffset = 0;

                    while (diffOutputIterator.hasNext()) {

                        String diffLine = diffOutputIterator.next();

                        if (diffLine.contains("\\ No newline at end of file")) continue;

                        if (diffLine.startsWith("@@")) {

                            // Strip down diff "@@" lines and add ",1" where the number of lines is omitted by default
                            // eg.  @@ -23 +23 @@       becomes     -23,1 +23,1
                            //      @@ -23 +22,0 @@     becomes     -23,1 +22,0
                            // etc.
                            diffLine = diffLine.substring(3, diffLine.lastIndexOf("@@") - 1);
                            if (diffLine.lastIndexOf(",") < diffLine.indexOf("+")) diffLine = diffLine + ",1";
                            if (diffLine.indexOf(",") == diffLine.lastIndexOf(","))
                                diffLine = new StringBuilder(diffLine).insert(diffLine.indexOf("+") - 1, ",1").toString();

                            diffLine = diffLine.replace("-", "");
                            diffLine = diffLine.replace("+", "");
                            String split[] = diffLine.split(",|\\s");

//                            diffOldPointer = Integer.valueOf(split[0]);
//                            diffOldLinesNo = Integer.valueOf(split[1]);
                            diffNewPointer = Integer.valueOf(split[2]);
                            diffNewLinesNo = Integer.valueOf(split[3]);

                            // there seems to be an inconsistency in the way git diff reports the addition of a single line
                            // and the pointer to the new line, i.e. if a single line is added it actually reports zero
                            // lines and the pointer is one line behind; the following line of code adjusts the line
                            // pointer accordingly
                            int actualNewPointer = (diffNewLinesNo == 0 ? diffNewPointer + 1 : diffNewPointer) + lineOffset;

                            // check if there are any unchanged lines of code between the last line added to the map file
                            // and the current changed line, and if so add them to the map file as well as to the old and
                            // new file tracker
                            if (mapFileLinePointer < actualNewPointer) {

                                while (mapFileLinePointer < actualNewPointer) {

                                    String unchangedLine = mapFileLines.get(mapFileLinePointer);
                                    ChangedFile.LineOfCode lineOfCode =
                                            new ChangedFile.LineOfCode(unchangedLine, STATUS_UNCHANGED,
                                                    newFileLinePointer, oldFileLinePointer);

                                    mergedLines.add(lineOfCode);
                                    newLinesMap.add(mapFileLinePointer);
                                    oldLinesMap.add(mapFileLinePointer);

                                    String numberedLine =
                                            oldFileLinePointer + ":" + newFileLinePointer + ": " + unchangedLine;
                                    mapFileLines.set(mapFileLinePointer, numberedLine);

                                    newFileLinePointer++;
                                    oldFileLinePointer++;
                                    mapFileLinePointer++;
                                }

                            }

                        } else if (diffLine.startsWith("-")) {

                            String oldLine = diffLine.substring(1);
                            ChangedFile.LineOfCode lineOfCode =
                                    new ChangedFile.LineOfCode(oldLine, STATUS_DELETED, 0, oldFileLinePointer);

                            mergedLines.add(lineOfCode);
                            oldLinesMap.add(mapFileLinePointer);

                            diffLine = oldFileLinePointer + ":0: " + oldLine;
                            mapFileLines.add(mapFileLinePointer, diffLine);

                            mapFileLinePointer++;
                            oldFileLinePointer++;
                            lineOffset++;

                        } else if (diffLine.startsWith("+")) {

                            String newLine = diffLine.substring(1);
                            ChangedFile.LineOfCode lineOfCode =
                                    new ChangedFile.LineOfCode(newLine, STATUS_ADDED, newFileLinePointer, 0);

                            mergedLines.add(lineOfCode);
                            newLinesMap.add(mapFileLinePointer);

                            diffLine = "0:" + newFileLinePointer + ": " + newLine;
                            mapFileLines.set(mapFileLinePointer, diffLine);

                            mapFileLinePointer++;
                            newFileLinePointer++;

                        } else {
                            // For a copied file git diff also outputs a few lines with details about the source
                            // of the copy; we don't need this information so we stop parsing and skip over it
                            break;
                        }
                    }

                    // write out any remaining lines after the last changed line
                    ListIterator<String> mapFileIterator = mapFileLines.listIterator(mapFileLinePointer);
                    while (mapFileIterator.hasNext()) {

                        String unchangedLine = mapFileIterator.next();
                        ChangedFile.LineOfCode lineOfCode =
                                new ChangedFile.LineOfCode(unchangedLine, STATUS_UNCHANGED,
                                        newFileLinePointer, oldFileLinePointer);

                        mergedLines.add(lineOfCode);
                        newLinesMap.add(mapFileLinePointer);
                        oldLinesMap.add(mapFileLinePointer);

                        String mapFileLine = oldFileLinePointer + ":" + newFileLinePointer + ": " + unchangedLine;
                        mapFileIterator.set(mapFileLine);

                        oldFileLinePointer++;
                        newFileLinePointer++;
                        mapFileLinePointer++;
                    }

                    String mapFileLinesOutput = formatDiffMapOutput(mapFileLines);

                    diffHumanOutput.append(mapFileLinesOutput.replaceAll(
                            "(\\\u001B\\[0m)|(\\\u001B\\[31m)|(\\\u001B\\[32m)",
                            ""));
                    System.out.print(mapFileLinesOutput);


                } else {

                    // TODO handle other file types of file changes?
                    // At the moment, we only handle cases where files that are added, deleted, modified,
                    // copied or renamed
                }

                ChangedFile changedFileEntry = new ChangedFile(newFile, changedFile, diffStatus, mergedLines,
                        newLinesMap, oldLinesMap);

                if (!diffStatus.equals(DIFF_STATUS_DELETED)) {
                    changedFiles.put(newFile, changedFileEntry);
                } else {
                    changedFiles.put(changedFile, changedFileEntry);
                }
            }
        }

        String modifiedFilesOutput = "Modified files: " + nameStatusList.size() + "\n";

        diffHumanOutput.append(modifiedFilesOutput);
        System.out.println(modifiedFilesOutput);

        outputTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN));

        String commitHash = hashToFileName(currentCommitHash);

        if (humanOutput) {
            // Write git diff human readable output
            String diffHumanOutputFileName = DIFF_HUMAN_OUTPUT_BASE_FILE_NAME;
            diffHumanOutputFileName = diffHumanOutputFileName.replace(TIMESTAMP_PLACEHOLDER, outputTime);
            diffHumanOutputFileName = diffHumanOutputFileName.replace(HASH_PLACEHOLDER, commitHash);
            Path diffHumanOutputPath = Paths.get(outputPath, diffHumanOutputFileName);
            try {
                Files.write(diffHumanOutputPath, diffHumanOutput.toString().getBytes());
            } catch (IOException e) {
                System.err.println("runGitDiff(): can't write diff output file for some reason");
                e.printStackTrace();
            }
        }

        currentDiffOutput = new DiffOutput(hashToOutput(currentCommitHash), hashToOutput(parentCommitHash), changedFiles);

        // The next line of comment tells IntelliJ to ignore code duplicates in the subsequent block
        //noinspection Duplicates
        if (machineOutput) {
            // Write git diff machine readable output file
            String diffMachineOutputFileName = DIFF_MACHINE_OUTPUT_BASE_FILE_NAME;
            diffMachineOutputFileName = diffMachineOutputFileName.replace(TIMESTAMP_PLACEHOLDER, outputTime);
            diffMachineOutputFileName = diffMachineOutputFileName.replace(HASH_PLACEHOLDER, commitHash);

            Utils.saveMachineOutput(currentDiffOutput, diffMachineOutputFileName, outputPath, zipOutput, jsonHandler);
        }
    }

    /**
     * Utility method - formats the human readable record of file changes between commits.
     *
     * @param mapFileLines List&lt;String&gt;, holds the line mapping between the new and the old commit.
     * @return returns a String containing the formatted output.
     */
    private String formatDiffMapOutput(List<String> mapFileLines) {

        StringBuilder stringBuilder = new StringBuilder();

        // Calculate number of characters to use in formatting of line number based on number of lines in file
        // i.e. number of characters = 1 + digits in number of lines
        int digitsNo = Math.max(4, 1 + Integer.toString(mapFileLines.size()).length());
        String format = "%-" + digitsNo + "d";

        String paddingSpaces = paddingSpaces(digitsNo - 3);

        stringBuilder.append("Mapping of line changes:\n");
        stringBuilder.append("MAP" + paddingSpaces + ": NEW" + paddingSpaces + ": OLD" + paddingSpaces + "\n");

        int mapLineNo = 0;

        for (String mapLine : mapFileLines) {

            if (mapLine == null) continue;

            String mapLineIndicator = String.format(format, ++mapLineNo);

            int oldLineNo = Integer.valueOf(mapLine.substring(0, mapLine.indexOf(":")));
            String oldLineIndicator = oldLineNo == 0 ?
                    STATUS_NON_EXISTENT + paddingSpaces : String.format(format, oldLineNo);

            mapLine = mapLine.substring(mapLine.indexOf(":") + 1);

            int newLineNo = Integer.valueOf(mapLine.substring(0, mapLine.indexOf(":")));
            String newLineIndicator = newLineNo == 0 ?
                    STATUS_DELETED_SHORT + paddingSpaces : String.format(format, newLineNo);

            mapLine = mapLine.substring(mapLine.indexOf(":") + 1);

            mapLine = newLineIndicator + ": " + oldLineIndicator + ": " + mapLine;

            // add line colour, i.e. green if added line, red if deleted line
            if (oldLineNo == 0) {
                mapLine = ANSI_GREEN + mapLine + ANSI_RESET;
            } else if (newLineNo == 0) {
                mapLine = ANSI_RED + mapLine + ANSI_RESET;
            }

            mapLine = mapLineIndicator + ": " + mapLine + "\n";
            stringBuilder.append(mapLine);
        }
        stringBuilder.append("\n");

        return stringBuilder.toString();
    }

    /**
     * Primary Utility Method - business logic for PIT mutation testing and recording of results.
     *
     * @throws Exception
     */
    private void runPitMutationTesting() throws Exception {

        File tempPom = createTempPom(projectPath, POM_FILE);

        invocationRequest.setPomFile(tempPom);
        invocationRequest.setGoals(Arrays.asList(MVN_GOAL_TEST, MVN_GOAL_PITEST));

        InvocationResult result = mavenInvoker.execute(invocationRequest);

        tempPom.delete();

        if (result.getExitCode() != 0) {
            if (result.getExecutionException() != null) {
                throw new Exception("Maven invocation failed.", result.getExecutionException());
            } else {
                throw new Exception("Maven invocation failed. Exit code: " + result.getExitCode());
            }
        }

        System.out.println();

        String pitMutationsReport = Paths.get(projectPath, PIT_REPORTS_PATH, PIT_MUTATIONS_FILE).toString();

        Document xmlDoc = documentBuilder.parse(new File(pitMutationsReport));

        Files.delete(Paths.get(pitMutationsReport));

        NodeList mutationsList = xmlDoc.getElementsByTagName("mutation");

        int hashMapCapacity = (int) (mutationsList.getLength() * 1.3);
        HashMap<String, MutatedFile> mutatedFiles = new HashMap<>(hashMapCapacity);

        for (int i = 0; i < mutationsList.getLength(); ++i) {

            Element mutationElement = (Element) mutationsList.item(i);
            boolean detectedAttribute = Boolean.valueOf(mutationElement.getAttribute("detected"));
            String statusAttribute = mutationElement.getAttribute("status");

            String sourceFileElement = mutationElement.getElementsByTagName("sourceFile").item(0).getTextContent();
            String mutatedClassElement = mutationElement.getElementsByTagName("mutatedClass").item(0).getTextContent();
            String mutatedMethodElement = mutationElement.getElementsByTagName("mutatedMethod").item(0).getTextContent();
            String methodDescriptionElement = mutationElement.getElementsByTagName("methodDescription").item(0).getTextContent();
            int lineNumberElement = Integer.valueOf(mutationElement.getElementsByTagName("lineNumber").item(0).getTextContent());
            String mutatorElement = mutationElement.getElementsByTagName("mutator").item(0).getTextContent();
            int indexElement = Integer.valueOf(mutationElement.getElementsByTagName("index").item(0).getTextContent());
            String killingTestElement = mutationElement.getElementsByTagName("killingTest").item(0).getTextContent();
            String descriptionElement = mutationElement.getElementsByTagName("description").item(0).getTextContent();

            String packagePath = mutatedClassElement.substring(0, mutatedClassElement.lastIndexOf("."));
            packagePath = packagePath.replace(".", "/");

            String mutatedFileName = MAVEN_JAVA_MAIN_SRC_PATH + "/" + packagePath + "/" + sourceFileElement;

            MutatedFile mutatedFile;
            if (mutatedFiles.containsKey(mutatedFileName)) {
                mutatedFile = mutatedFiles.get(mutatedFileName);
            } else {
                mutatedFile = new MutatedFile();

                if (!isEndCommit) {
                    if (changedFiles.containsKey(mutatedFileName)) {
                        ChangedFile changedFile = changedFiles.get(mutatedFileName);
                        mutatedFile.oldFileName = changedFile.oldFileName;
                        mutatedFile.diffStatus = changedFile.diffStatus;
                    } else {
                        mutatedFile.oldFileName = mutatedFileName;
                        mutatedFile.diffStatus = STATUS_UNCHANGED;
                    }
                }
            }

            MutatedFile.MutatedClass mutatedClass;
            if (mutatedFile.mutatedClasses.containsKey(mutatedClassElement)) {
                mutatedClass = mutatedFile.mutatedClasses.get(mutatedClassElement);
            } else {
                mutatedClass = new MutatedFile.MutatedClass();
            }

            String mutatedMethodName = mutatedClassElement + "." + mutatedMethodElement;

            MutatedFile.MutatedMethod mutatedMethod;
            if (mutatedClass.mutatedMethods.containsKey(mutatedMethodName)) {
                mutatedMethod = mutatedClass.mutatedMethods.get(mutatedMethodName);
            } else {
                mutatedMethod = new MutatedFile.MutatedMethod();
                mutatedMethod.description = methodDescriptionElement;
            }

            MutatedFile.Mutation mutation = new MutatedFile.Mutation();
            mutation.currentCommitData.detected = detectedAttribute;
            mutation.currentCommitData.pitStatus = statusAttribute;
            mutation.currentCommitData.lineNo = lineNumberElement;
            mutation.currentCommitData.mutator = mutatorElement;
            mutation.currentCommitData.index = indexElement;
            mutation.currentCommitData.description = descriptionElement;

            if (mutation.currentCommitData.detected && killingTestElement.length() > 0) {
                MutatedFile.KillingTest killingTest = new MutatedFile.KillingTest(true);

                if (!killingTestElement.contains("("))
                    killingTestElement = parseMalformedKillingTestElement(killingTestElement);

                killingTest.testFile.fileName = MAVEN_JAVA_TEST_SRC_PATH + "/" +
                        killingTestElement.substring(
                                killingTestElement.lastIndexOf("(") + 1,
                                killingTestElement.length() - 1
                        ).replace(".", "/") + ".java";

                if (changedFiles.containsKey(killingTest.testFile.fileName)) {
                    ChangedFile changedFile = changedFiles.get(killingTest.testFile.fileName);
                    killingTest.testFile.fileName_old = changedFile.oldFileName;
                    killingTest.testFile.diffStatus = changedFile.diffStatus;
                } else {
                    killingTest.testFile.diffStatus = STATUS_UNCHANGED;
                }

                killingTest.testFile.testMethod = killingTestElement.substring(0, killingTestElement.lastIndexOf("("));


                mutation.currentCommitData.killingTest = killingTest;
            }


            if (!isEndCommit) {
                if (mutatedFile.diffStatus.equals(STATUS_UNCHANGED)) {
                    mutation.lineDiffStatus = STATUS_UNCHANGED;
                } else if ((DIFF_STATUS_ADDED + DIFF_STATUS_COPIED).contains(mutatedFile.diffStatus)) {
                    mutation.lineDiffStatus = STATUS_NEW;
                } else if ((DIFF_STATUS_MODIFIED + DIFF_STATUS_RENAMED).contains(mutatedFile.diffStatus)) {
                    ChangedFile changedFile = changedFiles.get(mutatedFileName);
                    int mapLineNo = changedFile.newLinesMap.get(mutation.currentCommitData.lineNo);
                    mutation.lineDiffStatus = changedFile.mergedLines.get(mapLineNo).diffStatus;
                } else {
                    // unexpected: unknown and unhandled change type
                    System.err.println("runPitMutationTesting(): unknown change type found: " + mutatedFile.diffStatus);
                    mutation.lineDiffStatus = mutatedFile.diffStatus;
                }
            }

            if (mutatedMethod.mutations.contains(mutation)) {
                throw new Exception("This shouldn't happen! It means that the mutation is duplicated.");
            } else {
                mutatedMethod.mutations.add(mutation);
            }

            mutatedClass.mutatedMethods.put(mutatedMethodName, mutatedMethod);
            mutatedFile.mutatedClasses.put(mutatedClassElement, mutatedClass);

            mutatedFiles.put(mutatedFileName, mutatedFile);

        }


        currentPitOutput = new PitOutput(hashToOutput(currentCommitHash), mutatedFiles);

        outputTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        String pitOutputFileName = PIT_MACHINE_OUTPUT_BASE_FILE_NAME;
        pitOutputFileName = pitOutputFileName.replace(TIMESTAMP_PLACEHOLDER, outputTime);
        pitOutputFileName = pitOutputFileName.replace(HASH_PLACEHOLDER, hashToFileName(currentCommitHash));

        Utils.saveMachineOutput(currentPitOutput, pitOutputFileName, outputPath, zipOutput, jsonHandler);

    }

    /**
     * Utility method - used to parse killing test with no method name output by PIT
     *
     * @param killingTestElement
     * @return
     */
    private String parseMalformedKillingTestElement(String killingTestElement) {

        String topLevelDomain = killingTestElement.substring(0, killingTestElement.indexOf("."));

        int lastIndexOfTopLevelDomain = killingTestElement.lastIndexOf(topLevelDomain);

        return killingTestElement.substring(0, lastIndexOfTopLevelDomain) + "UNKNOWN_METHOD_NAME" +
                "(" + killingTestElement.substring(lastIndexOfTopLevelDomain) + ")";
    }

    /**
     * Primary Utility Method - topmost level business logic used to generate the PIT results change matrix
     *
     * @throws IOException
     */
    private void runPitMatrixAnalysis() throws IOException {

        int[][] pitMatrix = new int[SIZE_PIT_MATRIX][SIZE_PIT_MATRIX];

        int maxMutationsNo = buildPitMatrix(hashToOutput(childCommitHash), hashToOutput(currentCommitHash), pitMatrix);

        if (maxMutationsNo == -1) return;


        String formattedPitMatrixOutput = formatPitMatrixOutput(pitMatrix, maxMutationsNo,
                hashToOutput(childCommitHash), hashToOutput(currentCommitHash));

        System.out.println(formattedPitMatrixOutput);


        outputTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN));
        String commitHash = hashToFileName(childCommitHash);


        if (humanOutput) {
            // Write human readable output file with results of matrix analysis
            String matrixHumanOutputFileName = MATRIX_HUMAN_OUTPUT_BASE_FILE_NAME;
            matrixHumanOutputFileName = matrixHumanOutputFileName.replace(TIMESTAMP_PLACEHOLDER, outputTime);
            matrixHumanOutputFileName = matrixHumanOutputFileName.replace(HASH_PLACEHOLDER, commitHash);
            Path matrixHumanOutputPath = Paths.get(outputPath, matrixHumanOutputFileName);
            try {
                Files.write(matrixHumanOutputPath, formattedPitMatrixOutput.getBytes());
            } catch (IOException e) {
                System.err.println("runPitMatrixAnalysis(): can't write matrix file for some reason");
                e.printStackTrace();
            }
        }

        // Write machine readable output file with results of matrix analysis
        MatrixOutput matrixOutput = new MatrixOutput(hashToOutput(childCommitHash),
                hashToOutput(currentCommitHash), pitMatrix);

        // noinspection Duplicates
        if (machineOutput) {
            String matrixMachineOutputFileName = MATRIX_MACHINE_OUTPUT_BASE_FILE_NAME;
            matrixMachineOutputFileName = matrixMachineOutputFileName.replace(TIMESTAMP_PLACEHOLDER, outputTime);
            matrixMachineOutputFileName = matrixMachineOutputFileName.replace(HASH_PLACEHOLDER, commitHash);

            Utils.saveMachineOutput(matrixOutput, matrixMachineOutputFileName, outputPath, zipOutput, jsonHandler);
        }

        if (changedMutations != null) {
            String changesMachineOutputFileName = CHANGES_MACHINE_OUTPUT_BASE_FILE_NAME;
            changesMachineOutputFileName = changesMachineOutputFileName.replace(TIMESTAMP_PLACEHOLDER, outputTime);
            changesMachineOutputFileName = changesMachineOutputFileName.replace(HASH_PLACEHOLDER, commitHash);

            Utils.saveMachineOutput(changedMutations, changesMachineOutputFileName, outputPath, zipOutput, jsonHandler);
        }
    }

    /**
     * Primary Utility Method - high level business logic for the PIT results change matrix
     *
     * @param childCommitHash
     * @param currentCommitHash
     * @param pitMatrix
     * @return
     */
    private int buildPitMatrix(String childCommitHash, String currentCommitHash, int[][] pitMatrix) {

        PitOutput currentPitOutput = this.currentPitOutput.getClone();

        // First run - count the mutation types (i.e. killed, survived, no coverage, etc.) by looking at each of the
        // mutations in the child commit against those in the parent commit.
        countMutations(childPitOutput, childCommitHash, true,
                currentPitOutput, currentCommitHash, pitMatrix);

        // NOTE however that while this first run looks at every mutation that exists in the child commit, it does not
        // look at the mutations in the parent commit; this is because it only looks one way - i.e. it loops through
        // each mutation from each method from each class from each file in the child commit and checks to see if the
        // same one exists in the parent commit by picking the keys from the hash map of the parent commit - therefore,
        // it DOES NOT loop through the parent commit.
        //
        // What this means is that the mutations that did exist in the parent commit but no longer exist in the child
        // commit are not picked up during this run and are not added to the statistic. As a result, a second run is
        // required where the process is repeated, this time by looking the other way, i.e. looping through each
        // mutation from the parent commit and checking to see if a corresponding one exists in the child commit.
        //
        // Yet, it immediately becomes apparent that by using this simplistic approach all the mutations that exist in
        // both commits would be looped through (and potentially counted) twice - once in the first run and then again
        // in the second run. To avoid this issue, during the first run, every mutation from the child commit that is
        // found in the parent commit is added to the statistic and then is removed from the hash map storing the data
        // for the parent commit. This way, at the end of the first run, the hash map storing the data for the parent
        // commit will now contain only those mutations that did exist in the parent commit but are no longer found in
        // the child commit. Therefore, during the second run, only these mutations are looped through and added to the
        // statistic. Furthermore, in such cases where there is no change in mutations between subsequent commits, the
        // second run will effectively be skipped as the hash map storing the data for the parent commit will have been
        // emptied during the first run.

        // Second run - count the mutation types (i.e. killed, survived, no coverage, etc.) by looking at the mutations
        // in the parent commit against those in the child commit
        countMutations(currentPitOutput, currentCommitHash, false,
                childPitOutput, childCommitHash, pitMatrix);

        for (int i = 0; i < ROW_COL_TOTAL; i++)
            for (int j = 0; j < ROW_COL_TOTAL; j++) {
                pitMatrix[i][ROW_COL_TOTAL] += pitMatrix[i][j];
                pitMatrix[ROW_COL_TOTAL][i] += pitMatrix[j][i];
            }

        int maxValue = 0;
        for (int i = 0; i < SIZE_PIT_MATRIX; i++) {
            if (maxValue < pitMatrix[ROW_COL_TOTAL][i]) maxValue = pitMatrix[ROW_COL_TOTAL][i];
            if (maxValue < pitMatrix[i][ROW_COL_TOTAL]) maxValue = pitMatrix[i][ROW_COL_TOTAL];
        }

        return maxValue;
    }

    /**
     * Primary Utility Method - low level logic for the PIT results change matrix
     *
     * @param childPitOutput
     * @param childCommitHash
     * @param isChildCommit
     * @param parentPitOutput
     * @param parentCommitHash
     * @param pitMatrix
     */
    private void countMutations(PitOutput childPitOutput, String childCommitHash, boolean isChildCommit,
                                PitOutput parentPitOutput, String parentCommitHash,
                                int[][] pitMatrix) {

        String childFileName, childClassName, childMethodName, parentFileName, parentClassName, parentMethodName;
        MutatedFile childMutatedFile, parentMutatedFile;
        MutatedFile.MutatedClass childMutatedClass, parentMutatedClass;
        MutatedFile.MutatedMethod childMutatedMethod, parentMutatedMethod;

        boolean renamedFile;

        for (Map.Entry<String, MutatedFile> childMutatedFileEntry : childPitOutput.mutatedFiles.entrySet()) {

            childFileName = childMutatedFileEntry.getKey();
            childMutatedFile = childMutatedFileEntry.getValue();

            ChangedFile diffChangedFile = childDiffOutput.changedFiles.get(childFileName);

            if (isChildCommit && diffChangedFile != null && diffChangedFile.diffStatus.equals(DIFF_STATUS_RENAMED)) {
                parentFileName = diffChangedFile.oldFileName;
                renamedFile = true;
            } else {
                parentFileName = childFileName;
                renamedFile = false;
            }

            parentMutatedFile = parentPitOutput.mutatedFiles.get(parentFileName);

            for (Map.Entry<String, MutatedFile.MutatedClass> childMutatedClassEntry : childMutatedFile.mutatedClasses.entrySet()) {

                childClassName = childMutatedClassEntry.getKey();
                childMutatedClass = childMutatedClassEntry.getValue();

                if (parentMutatedFile != null) {
                    parentClassName = renamedFile ?
                            childClassName.replace(getNameOnly(childFileName), getNameOnly(parentFileName)) :
                            childClassName;
                    parentMutatedClass = parentMutatedFile.mutatedClasses.get(parentClassName);
                } else {
                    parentClassName = null;
                    parentMutatedClass = null;
                }

                for (Map.Entry<String, MutatedFile.MutatedMethod> newMutatedMethodEntry :
                        childMutatedClass.mutatedMethods.entrySet()) {

                    childMethodName = newMutatedMethodEntry.getKey();
                    childMutatedMethod = newMutatedMethodEntry.getValue();

                    if (parentMutatedClass != null) {
                        parentMethodName = renamedFile ?
                                childMethodName.replace(getNameOnly(childFileName), getNameOnly(parentFileName)) :
                                childMethodName;
                        parentMutatedMethod = parentMutatedClass.mutatedMethods.get(parentMethodName);
                    } else {
                        parentMethodName = null;
                        parentMutatedMethod = null;
                    }

                    // For the current method, iterate through each of its mutations
                    for (MutatedFile.Mutation childMutation : childMutatedMethod.mutations) {

                        // Initially, assume that the mutation does not exist in the old commit
                        int matrixRow = ROW_COL_NON_EXISTENT;
                        MutatedFile.Mutation parentMutation = null;

                        if (isChildCommit) {
                            // Check if the same mutated file, class and method was found in the parent commit;
                            // if this condition is not met, then the above assumption holds
                            if (parentMutatedFile != null && parentMutatedClass != null && parentMutatedMethod != null) {

                                Iterator<MutatedFile.Mutation> parentMutationsIterator =
                                        parentMutatedMethod.mutations.listIterator();

                                // If the condition is met, we still need to iterate through each of the
                                // mutations from the parent commit and see if the same one is found
                                while (parentMutationsIterator.hasNext()) {
                                    MutatedFile.Mutation mutation = parentMutationsIterator.next();

                                    int childMutationOldLineNo = childMutation.currentCommitData.lineNo;

                                    if (diffChangedFile != null) {
                                        try {
                                            int mapLineNo = diffChangedFile.newLinesMap.get(childMutation.currentCommitData.lineNo);
                                            childMutationOldLineNo = diffChangedFile.mergedLines.get(mapLineNo).oldLineNo;
                                        } catch (Exception e) {
                                            System.out.println("isChildCommit: " + isChildCommit);
                                            System.out.println("childCommitHash: " + childCommitHash);
                                            System.out.println("parentCommitHash: " + parentCommitHash);

                                            System.out.println("childMutation.currentCommitData.lineNo: " + childMutation.currentCommitData.lineNo);

                                            System.out.println("childFileName: " + childFileName);
                                            System.out.println("childClassName: " + childClassName);
                                            System.out.println("childMethodName: " + childMethodName);
                                            System.out.println("parentFileName: " + parentFileName);
                                            System.out.println("parentClassName: " + parentClassName);
                                            System.out.println("parentMethodName: " + parentMethodName);
                                            e.printStackTrace();

                                            System.exit(0);
                                        }

                                    }

                                    // TODO what happens if the line has been changed? i.e. lineDiffStatus is not the same?

                                    boolean isSameMutation = childMutationOldLineNo == mutation.currentCommitData.lineNo &&
                                            childMutation.currentCommitData.index.equals(mutation.currentCommitData.index) &&
                                            childMutation.currentCommitData.mutator.equals(mutation.currentCommitData.mutator) &&
                                            childMutation.currentCommitData.description.equals(mutation.currentCommitData.description);

                                    if (isSameMutation) {
                                        // if the same mutation if found, then we need to add it to the
                                        // relevant row in the statistics matrix
                                        matrixRow = getMatrixRowCol(mutation.currentCommitData.pitStatus);

                                        // we also need to store it for potential use in the changed mutations method
                                        parentMutation = mutation;

                                        // and if we're looking at the new commit against the old commit (i.e. this is
                                        // the first run - see long explanation in buildPitMatrix method) remove the
                                        // mutation from the hash map storing the old commit
                                        parentMutationsIterator.remove();

                                        // finally, break out of the loop since we found the mutation we were looking for
                                        // NOTE: the assumption is that each mutation is unique
                                        break;
                                    }
                                }
                            }
                        }


                        // the columns in the statistics matrix correspond to the new commit and are therefore
                        // fully determined by the status of the mutation in the new commit
                        int matrixCol = getMatrixRowCol(childMutation.currentCommitData.pitStatus);

                        String parentMethodDescription =
                                parentMutatedMethod == null ? null : parentMutatedMethod.description;

                        // BIG NOTE: This method is used for both runs, i.e. new commit vs old commit AND
                        // old commit vs new commit. However, the columns of the statistics matrix correspond
                        // to the new commit, while the rows correspond to the old commit.
                        if (isChildCommit) {
                            // As such, if we are in the first run (i.e. new commit vs old commit) the variables
                            // that define the rows and columns of the statistic matrix hold the meaning given by
                            // their names:
                            pitMatrix[matrixRow][matrixCol]++;

                            // if the mutation is on the diagonal of the statistics matrix, i.e. row == col, then
                            // the status of the mutation has not changed between the old and new commit, so there
                            // is no additional information that we are interested in;
                            // if however the mutation status has changed, i.e. row != col, then we need to store
                            // additional details for further investigation
                            if (matrixRow != matrixCol)
                                addChangedMutation(childCommitHash, parentCommitHash, diffChangedFile, matrixCol,
                                        childFileName, childClassName, childMethodName, childMutatedMethod.description,
                                        childMutation.getClone(), parentMutation, parentMethodDescription);
                        } else {
                            // if we are in the second run however, we are effectively looking at the transposed
                            // statistics matrix and therefore the meaning of the row and column variables is
                            // also transposed:
                            pitMatrix[matrixCol][matrixRow]++;


                            // The changes output is built at the same time as the Pit matrix!
                            // When a mutation is found that is not on the diagonal of the Pit matrix, then a change
                            // was found so a new entry needs to be added to the changes record.

                            if (matrixRow != matrixCol)
                                addChangedMutation(childCommitHash, parentCommitHash, diffChangedFile, ROW_COL_NON_EXISTENT,
                                        childFileName, childClassName, childMethodName, childMutatedMethod.description,
                                        childMutation.getClone(), parentMutation, parentMethodDescription);
                        }


                    }
                    if (isChildCommit && parentMutatedMethod != null && parentMutatedMethod.mutations.size() == 0)
                        parentMutatedClass.mutatedMethods.remove(parentMethodName);
                }
                if (isChildCommit && parentMutatedClass != null && parentMutatedClass.mutatedMethods.size() == 0)
                    parentMutatedFile.mutatedClasses.remove(parentClassName);
            }
            if (isChildCommit && parentMutatedFile != null && parentMutatedFile.mutatedClasses.size() == 0)
                parentPitOutput.mutatedFiles.remove(parentFileName);
        }
    }

    /**
     * Primary Utility Method - low level business logic that adds changed mutation to changed mutations record.
     *
     * @param newCommitHash
     * @param oldCommitHash
     * @param diffChangedFile
     * @param mutationStatus
     * @param newFileName
     * @param newClassName
     * @param newMethodName
     * @param newMethodDescription
     * @param changedMutation
     * @param oldMutation
     * @param oldMethodDescription
     */
    private void addChangedMutation(String newCommitHash, String oldCommitHash, ChangedFile diffChangedFile,
                                    int mutationStatus, String newFileName, String newClassName, String newMethodName,
                                    String newMethodDescription, MutatedFile.Mutation changedMutation,
                                    MutatedFile.Mutation oldMutation, String oldMethodDescription) {

        // the changed mutations list is build with respect to the current commit; as such, mutations that exist in the
        // current commit will be placed in the position corresponding to their status in the current commit, i.e.
        // "KILLED", "SURVIVED", etc., whereas all mutations that no longer exist in the current commit, but did exist
        // in the parent commit, will be placed on the position defined by "ROW_COL_NON_EXISTENT" (i.e. position zero)

        // if we are in the first run of the countMutations method, and are thus looking at the current commit, the
        // position of all mutations will be greater than "ROW_COL_NON_EXISTENT" (i.e. position zero)
        boolean isCurrentCommit = mutationStatus > ROW_COL_NON_EXISTENT;

        if (changedMutations == null)
            changedMutations = isCurrentCommit ?
                    new ChangedMutations(newCommitHash, oldCommitHash) :
                    // if we are in the second run of the countMutation, i.e. are looking at the parent commit, the
                    // oldCommitHash and newCommitHash values have been swapped so we need to swap them back:
                    new ChangedMutations(oldCommitHash, newCommitHash);

        HashMap<String, MutatedFile> mutatedFiles = getHashMapForRelevantMutationStatus(mutationStatus);

        boolean isNewMutatedFile = false,
                isNewMutatedClass = false,
                isNewMutatedMethod = false;

        MutatedFile mutatedFile;
        if (mutatedFiles.containsKey(newFileName)) {
            mutatedFile = mutatedFiles.get(newFileName);
        } else {
            mutatedFile = new MutatedFile();
            isNewMutatedFile = true;
            if (diffChangedFile != null) {
                mutatedFile.oldFileName = diffChangedFile.oldFileName;
                mutatedFile.diffStatus = diffChangedFile.diffStatus;
            } else {
                // if diffChangedFile is null, then the file has not changed
                mutatedFile.oldFileName = newFileName;
                mutatedFile.diffStatus = STATUS_UNCHANGED;
            }
        }

        MutatedFile.MutatedClass mutatedClass;
        if (mutatedFile.mutatedClasses.containsKey(newClassName)) {
            mutatedClass = mutatedFile.mutatedClasses.get(newClassName);
        } else {
            mutatedClass = new MutatedFile.MutatedClass();
            isNewMutatedClass = true;
        }

        MutatedFile.MutatedMethod mutatedMethod;
        if (mutatedClass.mutatedMethods.containsKey(newMethodName)) {
            mutatedMethod = mutatedClass.mutatedMethods.get(newMethodName);
        } else {
            mutatedMethod = new MutatedFile.MutatedMethod();
            isNewMutatedMethod = true;

            if (isCurrentCommit) {
                mutatedMethod.description = newMethodDescription;
                if (oldMethodDescription != null)
                    mutatedMethod.description_old = oldMethodDescription;
            } else {
                if (oldMethodDescription == null)
                    mutatedMethod.description_old = newMethodDescription;
                else
                    mutatedMethod.description = mutatedMethod.description_old = newMethodDescription;
            }
        }

        changedMutation.mutationStatus = isCurrentCommit ?
                (oldMutation == null ? STATUS_NEW : STATUS_EXISTING) :
                // if we are not in the current commit (i.e. we are at the parent commit), then all the mutations we
                // encounter were not found when looking at the current commit, meaning they were removed
                STATUS_REMOVED;

        if (changedMutation.mutationStatus.equals(STATUS_REMOVED))
            // translate values from fields to fields_old; sets fields to null
            changedMutation = new MutatedFile.Mutation(changedMutation);

        if (changedMutation.mutationStatus.equals(STATUS_EXISTING)) {

            changedMutation.parentCommitData = oldMutation.currentCommitData.getClone();

            if (!oldMutation.currentCommitData.pitStatus.equals(PIT_STATUS_KILLED)) {

                if (changedMutation.currentCommitData.pitStatus.equals(PIT_STATUS_KILLED))
                    changedMutation.currentCommitData.killingTest.testStatus = STATUS_NEW;

            } else {

                if (changedMutation.currentCommitData.pitStatus.equals(PIT_STATUS_KILLED)) {

                    changedMutation.currentCommitData.killingTest.testFile.testMethod_old =
                            changedMutation.parentCommitData.killingTest.testFile.testMethod;
                    changedMutation.parentCommitData.killingTest.testFile.diffStatus = null;
                    changedMutation.parentCommitData.killingTest.testFile.fileName_old = null;
                    changedMutation.parentCommitData.killingTest.testFile.testMethod_old = null;

                    String newTestFileName = changedMutation.currentCommitData.killingTest.testFile.fileName;
                    String oldTestFileName = oldMutation.currentCommitData.killingTest.testFile.fileName;

                    String newTestMethod = changedMutation.currentCommitData.killingTest.testFile.testMethod;
                    String oldTestMethod = oldMutation.currentCommitData.killingTest.testFile.testMethod;

                    changedMutation.currentCommitData.killingTest.testStatus =
                            newTestFileName.equals(oldTestFileName) && newTestMethod.equals(oldTestMethod) ?
                                    STATUS_UNCHANGED : STATUS_CHANGED;

                    changedMutation.currentCommitData.killingTest.testFileStatus =
                            newTestFileName.equals(oldTestFileName) ?
                                    STATUS_UNCHANGED : STATUS_CHANGED;

                    changedMutation.currentCommitData.killingTest.testMethodStatus =
                            changedMutation.currentCommitData.killingTest.testFileStatus.equals(STATUS_UNCHANGED) ?
                                    (newTestMethod.equals(oldTestMethod) ? STATUS_UNCHANGED : STATUS_CHANGED)
                                    : STATUS_UNKNOWN;
                } else {
                    changedMutation.currentCommitData.killingTest = new MutatedFile.KillingTest();
                    changedMutation.currentCommitData.killingTest.testStatus = STATUS_REGRESSED;
                    changedMutation.currentCommitData.killingTest.regressionNote =
                            REGRESSION_NOTE.replace(HASH_PLACEHOLDER, newCommitHash);
                }
            }
        }

        mutatedMethod.mutations.add(changedMutation);

        if (isNewMutatedMethod) mutatedClass.mutatedMethods.put(newMethodName, mutatedMethod);
        if (isNewMutatedClass) mutatedFile.mutatedClasses.put(newClassName, mutatedClass);
        if (isNewMutatedFile) mutatedFiles.put(newFileName, mutatedFile);
    }


//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
// NOTE: Former lines 1499 - 1513 have been offset due to addition of JavaDoc and other comments.
//       The new corresponding line numbers are 1747 - 1767
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
// NOTE: Former line number 1521 has been offset due to addition of JavaDoc and other comments.
//       The new corresponding line number is 1785
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
// NOTE: Former line number 1542 has been offset due to addition of JavaDoc and other comments. The new line number corresponding to the old 1542 is now 1784
// NOTE: Former line number 1543 has been offset due to addition of JavaDoc and other comments. The new line number corresponding to the old 1543 is now 1785
//
// NOTE: Former line numbers 1545 - 1576 have been offset due to addition of JavaDoc and other comments.
//       The line range correspondence is as follows: OLD 1545 - 1571 corresponds to NEW 1809 - 1835
//                                                    OLD 1576 corresponds to NEW 1840
//                                                    OLD 1545 - 1566 corresponds to NEW 1809 - 1830
//                                                    OLD 1571 - 1576 corresponds to NEW 1835 - 1840
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
// NOTE: Former line numbers 1566 has been offset due to addition of JavaDoc and other comments.
// See comments above for new line correspondence.
//
//
//
// NOTE: Former line numbers 1571 has been offset due to addition of JavaDoc and other comments.
// See comments above for new line correspondence.
//
//
//
// NOTE: Former line numbers 1576 has been offset due to addition of JavaDoc and other comments. See comments above for new line correspondence.






















    /**
     * Utility method - returns the relevant changed mutations hash map based
     * on the outcome of the changed mutation in the current commit.
     *
     * @param mutationStatus
     * @return
     */
    private HashMap<String, MutatedFile> getHashMapForRelevantMutationStatus(int mutationStatus) {
        switch (mutationStatus) {
            case ROW_COL_NON_EXISTENT:
                if (changedMutations.removedMutations == null) changedMutations.removedMutations = new HashMap<>();
                return changedMutations.removedMutations;
            case ROW_COL_KILLED:
                if (changedMutations.killedMutations == null) changedMutations.killedMutations = new HashMap<>();
                return changedMutations.killedMutations;
            case ROW_COL_SURVIVED:
                if (changedMutations.survivedMutations == null) changedMutations.survivedMutations = new HashMap<>();
                return changedMutations.survivedMutations;
            case ROW_COL_NO_COVERAGE:
                if (changedMutations.noCoverageMutations == null)
                    changedMutations.noCoverageMutations = new HashMap<>();
                return changedMutations.noCoverageMutations;
            case ROW_COL_NON_VIABLE:
                if (changedMutations.nonViableMutations == null) changedMutations.nonViableMutations = new HashMap<>();
                return changedMutations.nonViableMutations;
            case ROW_COL_TIMED_OUT:
                if (changedMutations.timedOutMutations == null) changedMutations.timedOutMutations = new HashMap<>();
                return changedMutations.timedOutMutations;
            case ROW_COL_MEMORY_ERROR:
                if (changedMutations.memoryErrorMutations == null)
                    changedMutations.memoryErrorMutations = new HashMap<>();
                return changedMutations.memoryErrorMutations;
            // mutationStatus will always be one of these 8 values; to avoid returning null, use default for final case
            default:    // case ROW_COL_RUN_ERROR:
                if (changedMutations.runErrorMutations == null) changedMutations.runErrorMutations = new HashMap<>();
                return changedMutations.runErrorMutations;
        }
    }

    /**
     * Utility method - formats the human readable output for the PIT results change matrix
     *
     * @param pitMatrix
     * @param maxValue
     * @param newCommit
     * @param oldCommit
     * @return
     */
    static String formatPitMatrixOutput(int[][] pitMatrix, int maxValue, String newCommit, String oldCommit) {

        int paddingSpacesNo;

        int digitsNo = Math.max(3, 2 + Integer.toString(maxValue).length());
        String format = "%-" + digitsNo + "d";

        StringBuilder stringBuilder = new StringBuilder();

// Append description of contents
        stringBuilder.append("Pit mutations statistics matrix:\n");
        stringBuilder.append("New commit: " + newCommit + "\n");
        stringBuilder.append("Old commit: " + oldCommit + "\n");
        stringBuilder.append("\n");


// Assemble first headings row
        stringBuilder.append(paddingSpaces(12));

        stringBuilder.append(COL_HEADING_0[0]);

        // number of spaces between "New commit" and "Old commit" headings on the first headings row
        paddingSpacesNo = digitsNo * 8 - COL_HEADING_0[0].length() + 3;
        stringBuilder.append(paddingSpaces(paddingSpacesNo));

        stringBuilder.append(COL_HEADING_0[1]);

        stringBuilder.append("\n");


// Assemble second headings row
        stringBuilder.append(paddingSpaces(12));

        for (int i = 0; i < ROW_COL_TOTAL; i++) {
            paddingSpacesNo = digitsNo - 3;   // COL_HEADING_1[i].length();
            stringBuilder.append(COL_HEADING_1[i]);
            stringBuilder.append(paddingSpaces(paddingSpacesNo));
        }

        stringBuilder.append(paddingSpaces(3));
        stringBuilder.append(COL_HEADING_1[ROW_COL_TOTAL]);

        stringBuilder.append("\n");

// Add matrix rows to output

        for (int i = 0; i < SIZE_PIT_MATRIX; i++) {

            stringBuilder.append(ROW_HEADINGS[i]);

            for (int j = 0; j < ROW_COL_TOTAL; j++)
                if (i == 0 & j == 0) {
                    paddingSpacesNo = digitsNo - 1;
                    stringBuilder.append("-");
                    stringBuilder.append(paddingSpaces(paddingSpacesNo));
                } else {
                    stringBuilder.append(String.format(format, pitMatrix[i][j]));
                }

            stringBuilder.append(paddingSpaces(3));


            if (i != ROW_COL_TOTAL)
                stringBuilder.append(String.format(format, pitMatrix[i][ROW_COL_TOTAL]));


            stringBuilder.append("\n");

        }

        return stringBuilder.toString();
    }

    /**
     * Utility matrix - returns the relevant row/column number based on the mutation status.
     *
     * @param status
     * @return
     */
    private int getMatrixRowCol(String status) {
        switch (status) {
            case PIT_STATUS_KILLED:
                return ROW_COL_KILLED;
            case PIT_STATUS_SURVIVED:
                return ROW_COL_SURVIVED;
            case PIT_STATUS_NO_COVERAGE:
                return ROW_COL_NO_COVERAGE;
            case PIT_STATUS_NON_VIABLE:
                return ROW_COL_NON_VIABLE;
            case PIT_STATUS_TIMED_OUT:
                return ROW_COL_TIMED_OUT;
            case PIT_STATUS_MEMORY_ERROR:
                return ROW_COL_MEMORY_ERROR;
            case PIT_STATUS_RUN_ERROR:
                return ROW_COL_RUN_ERROR;
        }
        return -1;
    }


//    /**
//     * WORKAROUND METHOD - Fix UTF-8 error in pom.xml file for commons-dbutils
//     *
//     * @param corruptedPom
//     * @throws IOException
//     */
//    private void fixUTF_in_commons_dbutils_pom(File corruptedPom) throws IOException {
//        List<String> pomLines = Utils.readAllLines(corruptedPom.toPath());
//
//        StringBuilder fixedPom = new StringBuilder();
//
//        for (String pomLine : pomLines) {
//            if (pomLine.contains("Bagyinszki"))
//                fixedPom.append("<name>Peter Bagyinszki</name>");
//            else
//                fixedPom.append(pomLine);
//            fixedPom.append("\n");
//        }
//
//        Files.write(corruptedPom.toPath(), fixedPom.toString().getBytes());
//    }

    /**
     * Primary Utility Method - business logic to create the temporary POM filex.
     *
     * @param repoPath
     * @param pomFile
     * @return
     * @throws IOException
     * @throws SAXException
     * @throws TransformerException
     * @throws XPathExpressionException
     */
    private File createTempPom(String repoPath, String pomFile) throws IOException, SAXException, TransformerException, XPathExpressionException {

        File repositoryPom = new File(Paths.get(repoPath, pomFile).toString());

        // Fix UTF-8 error in pom.xml file for Commons-DBUtils
//        fixUTF_in_commons_dbutils_pom(repositoryPom);

        Document xmlDoc = documentBuilder.parse(repositoryPom);

        Node plugins = (Node) xPath.compile("/project/build/plugins").evaluate(xmlDoc, XPathConstants.NODE);

        if (plugins == null) {
            plugins = xmlDoc.createElement("plugins");
            ((Node) xPath.compile("/project/build").evaluate(xmlDoc, XPathConstants.NODE)).appendChild(plugins);

        } else {
            NodeList pluginList = (NodeList) xPath.compile("/project/build/plugins/plugin").evaluate(xmlDoc, XPathConstants.NODESET);

            for (int i = 0; i < pluginList.getLength(); i++) {

                Element plugin = (Element) pluginList.item(i);

                String artifactId = plugin.getElementsByTagName("artifactId").item(0).getTextContent();

                if (artifactId.equals("pitest-maven")) {
                    plugins.removeChild(plugin);
//                    i--;
                    break;
                }
//                else if (artifactId.equals("maven-surefire-plugin")) {
//
//                    NodeList configurationContainer = plugin.getElementsByTagName("configuration");
//                    Element configuration;
//
//                    if (configurationContainer.getLength() > 0) {
//                        configuration = (Element) configurationContainer.item(0);
//                    } else {
//                        configuration = xmlDoc.createElement("configuration");
//                        plugin.appendChild(configuration);
//                    }
//
//                    NodeList excludesContainer = configuration.getElementsByTagName("excludes");
//                    Element excludes;
//
//                    if (excludesContainer.getLength() > 0) {
//                        excludes = (Element) excludesContainer.item(0);
//                    } else {
//                        excludes = xmlDoc.createElement("excludes");
//                        configuration.appendChild(excludes);
//                    }
//
//                    // Exclude failing test for JFreeChart
//                    Element exclude = xmlDoc.createElement("exclude");
//                    exclude.appendChild(xmlDoc.createTextNode("**/TimeSeriesCollectionTest*"));
//                    excludes.appendChild(exclude);
//
//                    // Exclude failing test for Commons-DBUtils from commit da0135a53a7e23fee525cd3865c35b6d83e24dab onwards
//                    Element exclude = xmlDoc.createElement("exclude");
//                    exclude.appendChild(xmlDoc.createTextNode("**/TestBean.java"));
//                    excludes.appendChild(exclude);
//                }
            }
        }

        plugins.appendChild(pitPlugin(xmlDoc));

        NodeList dependencyList = (NodeList) xPath.compile("/project/dependencies/dependency").evaluate(xmlDoc, XPathConstants.NODESET);
        for (int i = 0; i < dependencyList.getLength(); i++) {
            Node dependency = dependencyList.item(i);
            String artifactId = ((Element) dependency).getElementsByTagName("artifactId").item(0).getTextContent();
            if (artifactId.equals("junit")) {
                Node version = ((Element) dependency).getElementsByTagName("version").item(0);
                version.setTextContent("4.12");
                break;
            }
        }

        File tempPom = Files.createTempFile(Paths.get(repoPath), "pit-pom", ".xml").toFile();

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new DOMSource(xmlDoc), new StreamResult(tempPom));

        return tempPom;
    }

    /**
     * Utility method - generates the PIT plugin XML element that needs to be injected into the temporary POM file
     *
     * @param xmlDoc
     * @return
     */
    private Element pitPlugin(Document xmlDoc) {
        Element pitPlugin = xmlDoc.createElement("plugin");

        Element pitGroupId = xmlDoc.createElement("groupId");
        Element pitArtifactId = xmlDoc.createElement("artifactId");
        Element pitVersion = xmlDoc.createElement("version");
        Element pitConfiguration = xmlDoc.createElement("configuration");
        Element pitTimestampedReports = xmlDoc.createElement("timestampedReports");
        Element pitOutputFormats = xmlDoc.createElement("outputFormats");
        Element pitThreads = xmlDoc.createElement("threads");

        pitGroupId.appendChild(xmlDoc.createTextNode("org.pitest"));
        pitArtifactId.appendChild(xmlDoc.createTextNode("pitest-maven"));
        pitVersion.appendChild(xmlDoc.createTextNode("1.2.0"));

        pitTimestampedReports.appendChild(xmlDoc.createTextNode("false"));
        pitOutputFormats.appendChild(xmlDoc.createTextNode("XML"));
        pitThreads.appendChild(xmlDoc.createTextNode(String.valueOf(threadsNo)));

        pitConfiguration.appendChild(pitTimestampedReports);
        pitConfiguration.appendChild(pitOutputFormats);
        pitConfiguration.appendChild(pitThreads);

        pitPlugin.appendChild(pitGroupId);
        pitPlugin.appendChild(pitArtifactId);
        pitPlugin.appendChild(pitVersion);
        pitPlugin.appendChild(pitConfiguration);

        return pitPlugin;
    }

    /**
     * Utility method - generated commit text for staged and unstaged changes as these do not have a commit hash
     *
     * @param commitHash
     * @return
     */
    private String hashToOutput(String commitHash) {
        return hashToOutput(commitHash, false);
    }

    /**
     * Utility method - generated commit text for staged and unstaged changes as these do not have a commit hash
     *
     * @param commitHash
     * @param longOutput
     * @return
     */
    private String hashToOutput(String commitHash, boolean longOutput) {
        if (commitHash.equals(notStagedCommitHash))
            return "not staged changes" + (longOutput ? " (not committed -> no hash)" : "");
        else if (commitHash.equals(indexCommitHash))
            return "staged changes" + (longOutput ? " (not committed -> no hash)" : "");
        else
            return commitHash;
    }

    /**
     * Utility method - generates filename for staged and unstaged changes as these do not have a commit hash
     *
     * @param commitHash
     * @return
     */
    private String hashToFileName(String commitHash) {
        if (commitHash.equals(notStagedCommitHash))
            return "not-staged-changes-no-hash";
        else if (commitHash.equals(indexCommitHash))
            return "staged-changes-no-hash";
        else
            return commitHash;

    }
}