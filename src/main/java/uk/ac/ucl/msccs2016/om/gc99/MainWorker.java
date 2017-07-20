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
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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


class MainWorker implements Worker {

    private final CommandExecutor commandExecutor;
    private final Invoker mavenInvoker;
    private final InvocationRequest invocationRequest;
    private final JSONHandler jsonHandler;
    private final DocumentBuilder documentBuilder;

    private final String projectPath;
    private final String pitStatReportsPath;
    private final boolean pitStatReportsPathRelative;
    private final boolean createTimestampDirectory;
    private final boolean humanOutput;
    private final boolean zipOutput;
    private final boolean machineOutput;
    private final String startCommit;
    private final String endCommit;
    private final int threadsNo;

    private int maxRollbacks;

    private boolean resetIndex;
    private boolean resetUntracked;

    private List<String> commitsHashList;
    private String startCommitHash;
    private String endCommitHash;
    private int indexOfStartCommit;

    private String originalGitBranch;

    private String childCommitHash;
    private String currentCommitHash;
    private String parentCommitHash;

    private String startTime;
    private String outputTime;

    private String outputPath;

    private StringBuilder diffHumanOutput;

    private boolean isEndCommit;
    private HashMap<String, ChangedFile> changedFiles;
    private ChangedMutations changedMutations;

    private PitOutput childPitOutput, currentPitOutput;
    private DiffOutput childDiffOutput, currentDiffOutput;


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
        invocationRequest.setGoals(Arrays.asList(MVN_GOAL_TEST, MVN_GOAL_PITEST));
        invocationRequest.setBatchMode(true);

        documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

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


    void doWork() throws Exception {

        String pitStatBranch = GitUtils.checkoutPitStatBranch(startTime, projectPath, commandExecutor);

        validateBoundaryCommits();


        if (pitStatReportsPathRelative || createTimestampDirectory) {

            outputPath = Utils.createOutputDirectory(
                    pitStatReportsPath, pitStatReportsPathRelative,
                    startTime, createTimestampDirectory, projectPath);

            if (outputPath == null) App.systemExit(98);

        } else {
            outputPath = pitStatReportsPath;
        }


        if (indexOfStartCommit > 0) GitUtils.gitResetHardTo(startCommitHash, projectPath, commandExecutor);


        int currentCommitIndex = indexOfStartCommit;
        int parentCommitIndex = currentCommitIndex + 1;
        childCommitHash = null;
        currentCommitHash = startCommitHash;
        parentCommitHash = parentCommitIndex == commitsHashList.size() ?
                "currently at initial commit -> no parent hash" : commitsHashList.get(parentCommitIndex);

        int currentRollback = 0;

        do {
            System.out.println("\nRollback: " + currentRollback++);

            String currentCommitOutput = "Current commit hash: " + (currentCommitHash.length() > 0 ?
                    currentCommitHash : "currently at staged changes (not committed) -> no hash") + "\n";
            String parentCommitOutput = "Parent  commit hash: " + parentCommitHash + "\n\n";

            System.out.print(currentCommitOutput);
            System.out.print(parentCommitOutput);

            isEndCommit = currentCommitHash.equals(endCommitHash);

            if (isEndCommit) {
                System.out.println("Currently at end commit for this run (" +
                        currentCommitHash + ") -> skipping git diff\n");
            } else {
                diffHumanOutput = new StringBuilder();
                diffHumanOutput.append(currentCommitOutput);
                diffHumanOutput.append(parentCommitOutput);

                runGitDiff();
            }

            List<String> oldTempFiles = Utils.listTempFiles();

            Thread thread = new Thread(() -> {
                try {
                    runPitMutationTesting();
                } catch (Exception e) {
                    System.err.println("runPitMutationTesting(): An Exception was thrown");
                    e.printStackTrace();
                }
            });
            thread.start();
            thread.join();

            Utils.deleteNewTempFiles(oldTempFiles);

            if (childCommitHash != null) runPitMatrixAnalysis();

            if (!isEndCommit) {
                childCommitHash = currentCommitHash;
                currentCommitHash = commitsHashList.get(++currentCommitIndex);
                parentCommitHash = ++parentCommitIndex == commitsHashList.size() ?
                        "currently at initial commit -> no parent hash" : commitsHashList.get(parentCommitIndex);
                GitUtils.gitResetHardTo(currentCommitHash, projectPath, commandExecutor);

                childDiffOutput = currentDiffOutput;
                childPitOutput = currentPitOutput;
            }

        } while (!isEndCommit && currentRollback <= maxRollbacks);


        if (resetUntracked) GitUtils.gitResetMixedTo(GitUtils.HEAD_PARENT, projectPath, commandExecutor);
        if (resetIndex) GitUtils.gitResetSoftTo(GitUtils.HEAD_PARENT, projectPath, commandExecutor);


        GitUtils.checkoutOriginalBranch(originalGitBranch, projectPath, commandExecutor);
        GitUtils.deletePitStatBranch(pitStatBranch, projectPath, commandExecutor);
    }


    private void validateBoundaryCommits() {

        startCommitHash = null;

        if (startCommit.equals(GitUtils.INDEX))

            if (GitUtils.indexNotEmpty(projectPath, commandExecutor)) {
                startCommitHash = GitUtils.commitIndex(projectPath, commandExecutor);
                resetIndex = true;

                if (GitUtils.untrackedNotEmpty(projectPath, commandExecutor)) {
                    startCommitHash = GitUtils.commitUntracked(projectPath, commandExecutor);
                    resetUntracked = true;
                }

            } else {
                System.err.println("The index is empty - there are no staged changes.");
                App.systemExit(99);
            }

        else if (startCommit.equals(GitUtils.UNTRACKED))

            if (GitUtils.untrackedNotEmpty(projectPath, commandExecutor)) {

                if (GitUtils.indexNotEmpty(projectPath, commandExecutor)) {
                    GitUtils.commitIndex(projectPath, commandExecutor);
                    resetIndex = true;
                }

                startCommitHash = GitUtils.commitUntracked(projectPath, commandExecutor);
                resetUntracked = true;

            } else {
                System.err.println("There are no untracked changes on the working tree.");
                App.systemExit(99);
            }


        originalGitBranch = GitUtils.getGitBranch(GitUtils.HEAD, projectPath, commandExecutor);
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

            if (endCommit.equals(GitUtils.INITIAL_COMMIT)) {
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


    private void runGitDiff() throws IOException {

        // git diff name-status between previous and current commit
        List<String> nameStatusList =
                GitUtils.gitDiffNameStatus(currentCommitHash, parentCommitHash, projectPath, commandExecutor);

        int hashMapCapacity = (int) (nameStatusList.size() * 1.35);
        changedFiles = new HashMap<>(hashMapCapacity);

        if (nameStatusList.size() == 0) {

            String noDifferenceOutput = "No difference between " +
                    (currentCommitHash.equals("") ? "staged changes" : currentCommitHash) +
                    " and " + parentCommitHash + "\n";

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

                if ((DIFF_STATUS_ADDED + DIFF_STATUS_COPIED + DIFF_STATUS_DELETED).contains(diffStatus)) {

                    // If the file is added or deleted it would be superfluous to also list all its lines
                    // as added or deleted
                    diffHumanOutput.append("\n");
                    System.out.println();

                } else if ((DIFF_STATUS_MODIFIED + DIFF_STATUS_RENAMED).contains(diffStatus)) {

                    List<String> mapFileLines = Files.readAllLines(Paths.get(projectPath, newFile), StandardCharsets.UTF_8);
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
                        ChangedFile changedFileEntry =
                                new ChangedFile(newFile, changedFile, diffStatus, mergedLines, newLinesMap, oldLinesMap);
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

//                    int diffOldPointer, diffOldLinesNo;
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

                            // actualNewPointer keeps track of the starting location of changed lines in the map file
                            // lifeOffset ???
                            // TODO remember how this works and add full comments
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
                            // For a copied file git diff also outputs a few lines with details about the source of the
                            // copy; we don't need this information so we stop parsing and skip over it
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

                ChangedFile changedFileEntry =
                        new ChangedFile(newFile, changedFile, diffStatus, mergedLines, newLinesMap, oldLinesMap);

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
        String commitHash = currentCommitHash.equals("") ? "staged-changes-no-hash" : currentCommitHash;


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

        currentDiffOutput = new DiffOutput(parentCommitHash, currentCommitHash, changedFiles);

        //noinspection Duplicates
        if (machineOutput) {
            // Write git diff machine readable output file
            String diffMachineOutputFileName = DIFF_MACHINE_OUTPUT_BASE_FILE_NAME;
            diffMachineOutputFileName = diffMachineOutputFileName.replace(TIMESTAMP_PLACEHOLDER, outputTime);
            diffMachineOutputFileName = diffMachineOutputFileName.replace(HASH_PLACEHOLDER, commitHash);

            Utils.saveMachineOutput(currentDiffOutput, diffMachineOutputFileName, outputPath, zipOutput, jsonHandler);
        }
    }

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


    private void runPitMutationTesting() throws Exception {

        File tempPom = createTempPom(projectPath, POM_FILE);

        invocationRequest.setPomFile(tempPom);
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
//                    killingTest.testFile.fileName_old = killingTest.testFile.fileName;
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

        currentPitOutput = new PitOutput(currentCommitHash, mutatedFiles);

        outputTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        String pitOutputFileName = PIT_MACHINE_OUTPUT_BASE_FILE_NAME;
        pitOutputFileName = pitOutputFileName.replace(TIMESTAMP_PLACEHOLDER, outputTime);
        pitOutputFileName = pitOutputFileName.replace(HASH_PLACEHOLDER,
                currentCommitHash.equals("") ? "staged-changes-no-hash" : currentCommitHash);

        Utils.saveMachineOutput(currentPitOutput, pitOutputFileName, outputPath, zipOutput, jsonHandler);
    }


    private void runPitMatrixAnalysis() throws IOException {

        int[][] pitMatrix = new int[SIZE_PIT_MATRIX][SIZE_PIT_MATRIX];

        int maxMutationsNo = buildPitMatrix(childCommitHash, currentCommitHash, pitMatrix);

//        if (maxMutationsNo == -1) App.systemExit(99);
        if (maxMutationsNo == -1) return;


        String formattedPitMatrixOutput = formatPitMatrixOutput(pitMatrix, maxMutationsNo);

        System.out.println(formattedPitMatrixOutput);


        outputTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN));
        String commitHash = childCommitHash.equals("") ? "staged-changes-no-hash" : childCommitHash;


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
        MatrixOutput matrixOutput = new MatrixOutput(childCommitHash, currentCommitHash, pitMatrix);

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


    private int buildPitMatrix(String childCommitHash, String currentCommitHash, int[][] pitMatrix) {

        PitOutput currentPitOutput = this.currentPitOutput.getClone();

        // First run - count the mutation types (i.e. killed, survived, no coverage, etc.) by looking at each of the
        // mutations in the child commit against those in the parent commit.
        countMutations(childPitOutput, childCommitHash, true,
                currentPitOutput, currentCommitHash, pitMatrix, childDiffOutput);

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
                childPitOutput, childCommitHash, pitMatrix, childDiffOutput);

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


    private void countMutations(PitOutput childPitOutput, String childCommitHash, boolean isChildCommit,
                                PitOutput parentPitOutput, String parentCommitHash,
                                int[][] pitMatrix, DiffOutput diffOutput) {

        String childFileName, childClassName, childMethodName, parentFileName, parentClassName, parentMethodName;
        MutatedFile childMutatedFile, parentMutatedFile;
        MutatedFile.MutatedClass childMutatedClass, parentMutatedClass;
        MutatedFile.MutatedMethod childMutatedMethod, parentMutatedMethod;

        boolean renamedFile;

        for (Map.Entry<String, MutatedFile> newMutatedFileEntry : childPitOutput.mutatedFiles.entrySet()) {

            childFileName = newMutatedFileEntry.getKey();
            childMutatedFile = newMutatedFileEntry.getValue();

            ChangedFile diffChangedFile = diffOutput.changedFiles.getOrDefault(childFileName, null);

            if (isChildCommit && diffChangedFile != null && diffChangedFile.diffStatus.equals(DIFF_STATUS_RENAMED)) {
                parentFileName = diffChangedFile.oldFileName;
                renamedFile = true;
            } else {
                parentFileName = childFileName;
                renamedFile = false;
            }

            parentMutatedFile = parentPitOutput.mutatedFiles.get(parentFileName);

            for (Map.Entry<String, MutatedFile.MutatedClass> newMutatedClassEntry : childMutatedFile.mutatedClasses.entrySet()) {

                childClassName = newMutatedClassEntry.getKey();
                childMutatedClass = newMutatedClassEntry.getValue();

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
                    for (MutatedFile.Mutation newMutation : childMutatedMethod.mutations) {

                        // Initially, assume that the mutation does not exist in the old commit
                        int matrixRow = ROW_COL_NON_EXISTENT;
                        MutatedFile.Mutation oldMutation = null;

                        // Check if the same mutated file, class and method was found in the old commit;
                        // if this condition is not met, then the above assumption holds
                        if (parentMutatedFile != null && parentMutatedClass != null && parentMutatedMethod != null) {

                            Iterator<MutatedFile.Mutation> oldMutationsIterator =
                                    parentMutatedMethod.mutations.listIterator();

                            // If the condition is met, we still need to iterate through each of the
                            // mutations from the old commit and see if the same one is found
                            while (oldMutationsIterator.hasNext()) {
                                MutatedFile.Mutation mutation = oldMutationsIterator.next();

                                int newMutationOldLineNo = newMutation.currentCommitData.lineNo;

                                if (diffChangedFile != null) {
                                    int mapLineNo = diffChangedFile.newLinesMap.get(newMutation.currentCommitData.lineNo);
                                    newMutationOldLineNo = diffChangedFile.mergedLines.get(mapLineNo).oldLineNo;
                                }

                                // TODO what happens if the line has been changed? i.e. lineDiffStatus is not the same?

                                boolean isSameMutation = newMutationOldLineNo == mutation.currentCommitData.lineNo &&
                                        newMutation.currentCommitData.index.equals(mutation.currentCommitData.index) &&
                                        newMutation.currentCommitData.mutator.equals(mutation.currentCommitData.mutator) &&
                                        newMutation.currentCommitData.description.equals(mutation.currentCommitData.description);

                                if (isSameMutation) {
                                    // if the same mutation if found, then we need to add it to the
                                    // relevant row in the statistics matrix
                                    matrixRow = getMatrixRowCol(mutation.currentCommitData.pitStatus);

                                    // we also need to store it for potential use in the changed mutations method
                                    oldMutation = mutation;

                                    // and if we're looking at the new commit against the old commit (i.e. this is
                                    // the first run - see long explanation in buildPitMatrix method) remove the
                                    // mutation from the hash map storing the old commit
                                    if (isChildCommit) oldMutationsIterator.remove();

                                    // finally, break out of the loop since we found the mutation we were looking for
                                    // NOTE: the assumption is that each mutation is unique
                                    break;
                                }
                            }
                        }

                        // the columns in the statistics matrix correspond to the new commit and are therefore
                        // fully determined by the status of the mutation in the new commit
                        int matrixCol = getMatrixRowCol(newMutation.currentCommitData.pitStatus);

                        String oldMethodDescription =
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
                                        newMutation.getClone(), oldMutation, oldMethodDescription);
                        } else {
                            // if we are in the second run however, we are effectively looking at the transposed
                            // statistics matrix and therefore the meaning of the row and column variables is
                            // also transposed:
                            pitMatrix[matrixCol][matrixRow]++;

                            if (matrixRow != matrixCol)
                                addChangedMutation(childCommitHash, parentCommitHash, diffChangedFile, ROW_COL_NON_EXISTENT,
                                        childFileName, childClassName, childMethodName, childMutatedMethod.description,
                                        newMutation.getClone(), oldMutation, oldMethodDescription);
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


    private String formatPitMatrixOutput(int[][] pitMatrix, int maxValue) {

        int paddingSpacesNo;

        int digitsNo = Math.max(3, 2 + Integer.toString(maxValue).length());
        String format = "%-" + digitsNo + "d";

        StringBuilder stringBuilder = new StringBuilder();

// Append description of contents
        stringBuilder.append("Pit mutations statistics matrix:\n");
        stringBuilder.append("New commit: " + childCommitHash + "\n");
        stringBuilder.append("Old commit: " + currentCommitHash + "\n");
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


    private File createTempPom(String repoPath, String pomFile) throws IOException, SAXException, TransformerException {

        File repositoryPom = new File(Paths.get(repoPath, pomFile).toString());

        Document xmlDoc = documentBuilder.parse(repositoryPom);

        Element project = (Element) xmlDoc.getFirstChild();


        Element build = (Element) project.getElementsByTagName("build").item(0);
        Element plugins = (Element) build.getElementsByTagName("plugins").item(0);
        NodeList pluginsList = plugins.getElementsByTagName("plugin");
        for (int i = 0; i < pluginsList.getLength(); i++) {
            Node plugin = pluginsList.item(i);
            String groupId = ((Element) plugin).getElementsByTagName("groupId").item(0).getTextContent();
            if (groupId.equals("org.pitest")) {
                plugins.removeChild(plugin);
                break;
            }
        }
        plugins.appendChild(pitPlugin(xmlDoc));


        NodeList dependenciesList = project.getElementsByTagName("dependencies");
        for (int i = 0; i < dependenciesList.getLength(); i++) {
            Element dependencies = (Element) dependenciesList.item(i);
            NodeList dependencyList = dependencies.getElementsByTagName("dependency");
            for (int j = 0; j < dependencyList.getLength(); j++) {
                Node dependency = dependencyList.item(j);
                String groupId = ((Element) dependency).getElementsByTagName("groupId").item(0).getTextContent();
                if (groupId.equals("junit")) {
                    Node version = ((Element) dependency).getElementsByTagName("version").item(0);
                    version.setTextContent("4.12");
                    break;
                }
            }
        }


        File tempPom = Files.createTempFile(Paths.get(repoPath), "pit-pom", ".xml").toFile();

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new DOMSource(xmlDoc), new StreamResult(tempPom));

        return tempPom;
    }


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
}