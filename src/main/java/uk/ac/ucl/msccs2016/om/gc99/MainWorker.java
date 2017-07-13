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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;


class MainWorker implements Worker {

    private final CommandExecutor commandExecutor;
    private final Invoker mavenInvoker;
    private final InvocationRequest invocationRequest;
    private final JSONHandler jsonHandler;

    private final DocumentBuilder documentBuilder;


    private String projectPath;
    private String pitStatReportsPath;
    private boolean pitStatReportsPathRelative;
    private boolean createTimestampDirectory;
    private String startCommit;
    private String endCommit;
    private int maxRollbacks;

    private List<String> commitsHashList;

    private String startCommitHash;
    private String endCommitHash;
    private int indexOfStartCommit;

    private String originalGitBranch;
    private String pitStatBranch;

    private String childCommitHash;
    private String currentCommitHash;
    private String parentCommitHash;

    private String startTime;
    private String outputTime;

    private String outputPath;

    private StringBuilder diffHumanOutput;

    private boolean isEndCommit;
    private HashMap<String, ChangedFile> changedFiles;


    MainWorker(String projectPath,
               String pitStatReportsPath,
               boolean pitStatReportsPathRelative,
               boolean createTimestampDirectory,
               String startCommit,
               String endCommit,
               int maxRollbacks)
            throws Exception {

        startTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        commandExecutor = new CommandExecutor();
        mavenInvoker = new DefaultInvoker();

        String mavenHome = System.getenv("M2_HOME");
        if (mavenHome != null) {
            mavenInvoker.setMavenHome(new File(mavenHome));
        } else {
            System.err.println("Maven home environment variable not set.");
            System.out.println("Tip: the M2_HOME environment variable needs to point to the path of your maven installation");
            App.systemExit(98);
        }

        invocationRequest = new DefaultInvocationRequest();
        invocationRequest.setGoals(Arrays.asList(mvnGoalTest, mvnGoalPitest));
        invocationRequest.setBatchMode(true);

        documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

        jsonHandler = new JSONHandler();

        this.projectPath = projectPath;
        this.pitStatReportsPath = pitStatReportsPath;
        this.pitStatReportsPathRelative = pitStatReportsPathRelative;
        this.createTimestampDirectory = createTimestampDirectory;
        this.startCommit = startCommit;
        this.endCommit = endCommit;
        this.maxRollbacks = maxRollbacks;
    }


    boolean validStartEndCommits() {

        originalGitBranch = getGitBranch("HEAD");
        commitsHashList = getCommitsHashList();

        startCommitHash = parseCommit(startCommit);
        indexOfStartCommit = (startCommit.equals("") ? -1 : commitsHashList.indexOf(startCommitHash));

        if (maxRollbacks > 0 && endCommit == null) {

            int indexOfEndCommit = (maxRollbacks == Integer.MAX_VALUE) ?
                    (commitsHashList.size() - 1) : (indexOfStartCommit + maxRollbacks);

            if (indexOfEndCommit > (commitsHashList.size() - 1)) {
                System.err.println("The number of rollbacks is greater than the history of this branch.");
                App.systemExit(99);
            }

            endCommitHash = commitsHashList.get(indexOfEndCommit);

        } else {

            if (endCommit.equals("initial-commit")) {
                endCommitHash = commitsHashList.get(commitsHashList.size() - 1);
            } else {
                endCommitHash = parseCommit(endCommit);
            }

            maxRollbacks = commitsHashList.indexOf(endCommitHash) - indexOfStartCommit;
        }

        if (maxRollbacks < 0) {
            System.err.println("Start commit is older than end commit.");
            System.out.println("Tip: pitStat rolls back from the start commit towards the end commit");
            App.systemExit(99);
        }

        return true;
    }


    void doWork() throws Exception {

        if (pitStatReportsPathRelative || createTimestampDirectory) {
            outputPath = createOutputDirectory();
            if (outputPath == null) App.systemExit(98);
        } else {
            outputPath = pitStatReportsPath;
        }

        pitStatBranch = checkoutPitStatBranch();

        if (indexOfStartCommit > 0) rollBackTo(startCommitHash);

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
                System.out.println("Currently at end commit for this run (" + currentCommitHash + ") -> skipping git diff\n");
            } else {
                diffHumanOutput = new StringBuilder();
                diffHumanOutput.append(currentCommitOutput);
                diffHumanOutput.append(parentCommitOutput);

                runGitDiff();
            }

            runPitMutationTesting();

            if (childCommitHash != null) runPitMatrixAnalysis();

            if (!isEndCommit) {
                childCommitHash = currentCommitHash;
                currentCommitHash = commitsHashList.get(++currentCommitIndex);
                parentCommitHash = ++parentCommitIndex == commitsHashList.size() ?
                        "currently at initial commit -> no parent hash" : commitsHashList.get(parentCommitIndex);
                rollBackTo(currentCommitHash);
            }

        } while (!isEndCommit && currentRollback <= maxRollbacks);

        checkoutOriginalBranch();
        deletePitStatBranch();

    }


    @SuppressWarnings("unchecked")
    private void runGitDiff() throws Exception {

        // git diff name-status between previous and current commit
        List<String> nameStatusList = gitDiffNameStatus();

        int hashMapCapacity = (int) (nameStatusList.size() * 1.3);
        changedFiles = new HashMap<>(hashMapCapacity);

        if (nameStatusList.size() == 0) {
            String noDifferenceOutput = "No difference between " + parentCommitHash + " and " +
                    (currentCommitHash.equals("") ? "staged changes" : currentCommitHash) + "\n";

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
                    case "A":
                        diffStatusOutput.append("Added file:    ");
                        break;
                    case "D":
                        diffStatusOutput.append("Deleted file:  ");
                        break;
                    case "M":
                        diffStatusOutput.append("Modified file: ");
                        break;
                    case "C":
                        newFile = splitLine[2];
                        diffStatusOutput.append("Copied file:   ");
                        break;
                    case "R":
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

                if (newFile == null && !diffStatus.equals("D")) newFile = changedFile;
                if (diffStatus.equals("A")) changedFile = null;

                List<LineOfCode> mergedLines = null;
                List<Integer> newLinesMap = null, oldLinesMap = null;

                if ("ACD".contains(diffStatus)) {

                    // If the file is added or deleted it would be superfluous to also list all its lines
                    // as added or deleted
                    diffHumanOutput.append("\n");
                    System.out.println();

                } else if ("MR".contains(diffStatus)) {

                    List<String> mapFileLines = Files.readAllLines(Paths.get(projectPath, newFile), StandardCharsets.UTF_8);
                    mapFileLines.add(0, null);
                    int mapFileLinePointer = 1;

                    // git diff between previous and current version of the specific file
                    List<String> diffOutputLines = gitDiff(changedFile, newFile);

                    ListIterator<String> diffOutputIterator = diffOutputLines.listIterator();

                    // Skip git diff header lines, i.e. skip until the first line starting with @@ is found
                    while (diffOutputIterator.hasNext() && !diffOutputIterator.next().startsWith("@@")) ;

                    // If the file was copied or renamed but not modified (100% similarity) then the while loop above
                    // will have reached the end of the diff output so we need to continue with the next changed file
                    if (!diffOutputIterator.hasNext()) {
                        ChangedFile changedFileEntry = new ChangedFile(newFile, changedFile, diffStatus, mergedLines, newLinesMap, oldLinesMap);
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
                                    LineOfCode lineOfCode = new LineOfCode(unchangedLine, "UNCHANGED", newFileLinePointer, oldFileLinePointer);

                                    mergedLines.add(lineOfCode);
                                    newLinesMap.add(mapFileLinePointer);
                                    oldLinesMap.add(mapFileLinePointer);

                                    String numberedLine = oldFileLinePointer + ":" + newFileLinePointer + ": " + unchangedLine;
                                    mapFileLines.set(mapFileLinePointer, numberedLine);

                                    newFileLinePointer++;
                                    oldFileLinePointer++;
                                    mapFileLinePointer++;
                                }

                            }

                        } else if (diffLine.startsWith("-")) {

                            String oldLine = diffLine.substring(1);
                            LineOfCode lineOfCode = new LineOfCode(oldLine, "DELETED", 0, oldFileLinePointer);

                            mergedLines.add(lineOfCode);
                            oldLinesMap.add(mapFileLinePointer);

                            diffLine = oldFileLinePointer + ":0: " + oldLine;
                            mapFileLines.add(mapFileLinePointer, diffLine);

                            mapFileLinePointer++;
                            oldFileLinePointer++;
                            lineOffset++;

                        } else if (diffLine.startsWith("+")) {

                            String newLine = diffLine.substring(1);
                            LineOfCode lineOfCode = new LineOfCode(newLine, "ADDED", newFileLinePointer, 0);

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
                        LineOfCode lineOfCode = new LineOfCode(unchangedLine, "UNCHANGED", newFileLinePointer, oldFileLinePointer);

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

                    diffHumanOutput.append(mapFileLinesOutput.replaceAll("(\\\u001B\\[0m)|(\\\u001B\\[31m)|(\\\u001B\\[32m)", ""));
                    System.out.print(mapFileLinesOutput);

                } else {

                    // TODO handle other file types of file changes?
                    // At the moment, we only handle cases where files that are added, deleted, modified,
                    // copied or renamed
                }

                ChangedFile changedFileEntry = new ChangedFile(newFile, changedFile, diffStatus, mergedLines, newLinesMap, oldLinesMap);

                if (!diffStatus.equals("D")) {
                    changedFiles.put(newFile, changedFileEntry);
                } else {
                    changedFiles.put(changedFile, changedFileEntry);
                }
            }
        }

        String modifiedFilesOutput = "Modified files: " + nameStatusList.size() + "\n";

        diffHumanOutput.append(modifiedFilesOutput);
        System.out.println(modifiedFilesOutput);

        outputTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String commitHash = currentCommitHash.equals("") ? "staged-changes-no-hash" : currentCommitHash;


        // Write git diff human readable output
        String diffHumanOutputFileName = diffHumanOutputBaseFileName;
        diffHumanOutputFileName = diffHumanOutputFileName.replace(timestamp, outputTime);
        diffHumanOutputFileName = diffHumanOutputFileName.replace(hash, commitHash);
        Path diffHumanOutputPath = Paths.get(outputPath, diffHumanOutputFileName);
        try {
            Files.write(diffHumanOutputPath, diffHumanOutput.toString().getBytes());
        } catch (IOException e) {
            System.err.println("runGitDiff(): can't write diff output file for some reason");
            e.printStackTrace();
        }

        // Write git diff machine readable output file
        DiffOutput diffOutput = new DiffOutput(parentCommitHash, currentCommitHash, changedFiles);
        String diffOutputFileName = diffMachineOutputBaseFileName;
        diffOutputFileName = diffOutputFileName.replace(timestamp, outputTime);
        diffOutputFileName = diffOutputFileName.replace(hash, commitHash);
        String diffOutputPath = Paths.get(outputPath, diffOutputFileName).toString();
        jsonHandler.saveDifToJSON(diffOutput, diffOutputPath);
    }

    private String formatDiffMapOutput(List<String> mapFileLines) {

        StringBuilder stringBuilder = new StringBuilder();

        // Calculate number of characters to use in formatting of line number based on number of lines in file
        // i.e. number of characters = 1 + digits in number of lines
        int digitsNo = Math.max(4, 1 + Integer.toString(mapFileLines.size()).length());
        String format = "%-" + digitsNo + "d";

        String paddingSpaces = this.paddingSpaces(digitsNo - 3);

        stringBuilder.append("Mapping of line changes:\n");
        stringBuilder.append("MAP" + paddingSpaces + ": NEW" + paddingSpaces + ": OLD" + paddingSpaces + "\n");

        int mapLineNo = 0;

        for (String mapLine : mapFileLines) {

            if (mapLine == null) continue;

            String mapLineIndicator = String.format(format, ++mapLineNo);

            int oldLineNo = Integer.valueOf(mapLine.substring(0, mapLine.indexOf(":")));
            String oldLineIndicator = oldLineNo == 0 ? "N/E" + paddingSpaces : String.format(format, oldLineNo);

            mapLine = mapLine.substring(mapLine.indexOf(":") + 1);

            int newLineNo = Integer.valueOf(mapLine.substring(0, mapLine.indexOf(":")));
            String newLineIndicator = newLineNo == 0 ? "DEL" + paddingSpaces : String.format(format, newLineNo);

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


    @SuppressWarnings("unchecked")
    private void runPitMutationTesting() throws Exception {

        File tempPom = createTempPom(projectPath, pomFile);

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

        // Path latestPitReportPath = getLatestPitReportPath(pitReportsPath, pitReportsPathRelative);
        String pitMutationsReport = Paths.get(projectPath, pitReportsPath, pitMutationsFile).toString();

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

            String mutatedFileName = mavenJavaMainSrcPath + "/" + packagePath + "/" + sourceFileElement;

            MutatedFile mutatedFile;
            if (mutatedFiles.containsKey(mutatedFileName)) {
                mutatedFile = mutatedFiles.get(mutatedFileName);
            } else {
                mutatedFile = new MutatedFile();

                if (!isEndCommit) {
                    if (changedFiles.containsKey(mutatedFileName)) {
                        mutatedFile.changeStatus = changedFiles.get(mutatedFileName).changeType;
                    } else {
                        mutatedFile.changeStatus = "UNCHANGED";
                    }
                } else {
                    mutatedFile.changeStatus = "N/A";
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
                mutatedMethod = new MutatedFile.MutatedMethod(methodDescriptionElement);
            }

            MutatedFile.Mutation mutation = new MutatedFile.Mutation();
            mutation.detected = detectedAttribute;
            mutation.status = statusAttribute;
            mutation.lineNo = lineNumberElement;
            mutation.mutator = mutatorElement;
            mutation.index = indexElement;
            mutation.description = descriptionElement;

            if (mutation.detected && killingTestElement.length() > 0) {
                MutatedFile.KillingTest killingTest = new MutatedFile.KillingTest();
                killingTest.testMethod = killingTestElement.substring(0, killingTestElement.lastIndexOf("("));

                killingTest.testFileName = mavenJavaTestSrcPath + "/" +
                        killingTestElement.substring(
                                killingTestElement.lastIndexOf("(") + 1,
                                killingTestElement.length() - 1
                        ).replace(".", "/") + ".java";

                if (!isEndCommit) {
                    if (changedFiles.containsKey(killingTest.testFileName)) {
                        killingTest.testFileChangeStatus = changedFiles.get(killingTest.testFileName).changeType;
                    } else {
                        killingTest.testFileChangeStatus = "UNCHANGED";
                    }
                } else {
                    killingTest.testFileChangeStatus = "N/A";
                }

                mutation.killingTest = killingTest;
            } else {
                mutation.killingTest = null;
            }

            if (!isEndCommit) {
                if (mutatedFile.changeStatus.equals("UNCHANGED")) {
                    mutation.lineStatus = "UNCHANGED";
                } else if ("AC".contains(mutatedFile.changeStatus)) {
                    mutation.lineStatus = "ADDED";
                } else if ("MR".contains(mutatedFile.changeStatus)) {

                    int mapLineNo = changedFiles.get(mutatedFileName).newLinesMap.get(mutation.lineNo);

                    mutation.lineStatus = changedFiles.get(mutatedFileName).mergedLines.get(mapLineNo).status;

                } else {
                    // unexpected: unknown and unhandled change type
                    System.err.println("runPitMutationTesting(): unknown change type found: " + mutatedFile.changeStatus);
                    mutation.lineStatus = mutatedFile.changeStatus;
                }
            } else {
                mutation.lineStatus = "N/A";
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

        PitOutput pitOutput = new PitOutput(currentCommitHash, mutatedFiles);

        outputTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        String pitOutputFileName = pitOutputBaseFileName;
        pitOutputFileName = pitOutputFileName.replace(timestamp, outputTime);
        pitOutputFileName = pitOutputFileName.replace(hash, currentCommitHash.equals("") ? "staged-changes-no-hash" : currentCommitHash);

        String pitOutputPath = Paths.get(outputPath, pitOutputFileName).toString();

        jsonHandler.savePitToJSON(pitOutput, pitOutputPath);
    }


    private void runPitMatrixAnalysis() throws Exception {

        int[][] pitMatrix = new int[pitMatrixSize][pitMatrixSize];

        int maxMutationsNo = buildPitMatrix(childCommitHash, currentCommitHash, pitMatrix);

        if (maxMutationsNo == -1) App.systemExit(99);


        String formattedPitMatrixOutput = formatPitMatrixOutput(pitMatrix, maxMutationsNo);

        System.out.println(formattedPitMatrixOutput);


        outputTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String commitHash = childCommitHash.equals("") ? "staged-changes-no-hash" : childCommitHash;


        // Write human readable output file with results of matrix analysis
        String matrixHumanOutputFileName = matrixHumanOutputBaseFileName;
        matrixHumanOutputFileName = matrixHumanOutputFileName.replace(timestamp, outputTime);
        matrixHumanOutputFileName = matrixHumanOutputFileName.replace(hash, commitHash);
        Path matrixHumanOutputPath = Paths.get(outputPath, matrixHumanOutputFileName);
        try {
            Files.write(matrixHumanOutputPath, formattedPitMatrixOutput.getBytes());
        } catch (IOException e) {
            System.err.println("runPitMatrixAnalysis(): can't write matrix file for some reason");
            e.printStackTrace();
        }


        MatrixOutput matrixOutput = new MatrixOutput(childCommitHash, currentCommitHash, pitMatrix);

        // Write machine readable output file with results of matrix analysis
        String matrixMachineOutputFileName = matrixMachineOutputBaseFileName;
        matrixMachineOutputFileName = matrixMachineOutputFileName.replace(timestamp, outputTime);
        matrixMachineOutputFileName = matrixMachineOutputFileName.replace(hash, commitHash);

        String matrixMachineOutputPath = Paths.get(outputPath, matrixMachineOutputFileName).toString();

        jsonHandler.saveMatrixToJSON(matrixOutput, matrixMachineOutputPath);

    }


    private int buildPitMatrix(String newCommit, String oldCommit, int[][] pitMatrix) {

        PitOutput newCommitPitOutput = loadPitOutput(newCommit);
        PitOutput oldCommitPitOutput = loadPitOutput(oldCommit);

        if (newCommitPitOutput == null || oldCommitPitOutput == null) return -1;

        DiffOutput diffOutput = loadDiffOutput(newCommit);

        // First run - count the mutation types (i.e. killed, survived, no coverage, etc.) by looking at
        // each of the mutations in the new commit against those in the old commit.
        countMutations(newCommitPitOutput, true, oldCommitPitOutput, pitMatrix, diffOutput);

        // NOTE however that while this first run looks at every mutation that exists in the new commit,
        // it does not look at the mutations in the old commit; this is because it only looks one way - i.e.
        // it loops through each mutation from each method from each class from each file in the new commit
        // and checks to see if the same one exists in the old commit by picking the keys from the
        // hash map of the old commit - therefore, it DOES NOT loop through the old commit.
        // What this means is that the mutations that did exist in the old commit but no longer exist in the
        // new commit are not picked up during this run and are not added to the statistic.
        // As a result, a second run is required where the process is repeated, this time by looking the
        // other way, i.e. looping through each mutation from the old commit and checking to see if a
        // corresponding one exists in the new commit.
        // Yet, it immediately becomes apparent that by using this simplistic approach all the mutations that
        // exist in both commits would be looped through (and potentially counted) twice - once in the first
        // run and then again in the second run. To avoid this issue, during the first run, every mutation
        // from the new commit that is found in the old commit is added to the statistic and then is removed
        // from the hash map storing the data for the old commit. This way, at the end of the first run, the
        // hash map storing the data for the old commit will now contain only those mutations that did exist
        // in the old commit but are no longer found in the new commit. Therefore, during the second run, only
        // these mutations are looped through and added to the statistic. Furthermore, in such cases where
        // there is no change in mutations between subsequent commits, the second run will effectively be
        // skipped as the hash map storing the data for the old commit will have been emptied during the first
        // run.

        // Second run - count the mutation types (i.e. killed, survived, no coverage, etc.) by looking at
        // the mutations in the old commit against those in the new commit
        countMutations(oldCommitPitOutput, false, newCommitPitOutput, pitMatrix, diffOutput);


        int maxValue = 0;

        for (int i = 0; i < totalRowCol; i++)
            for (int j = 0; j < totalRowCol; j++) {
                pitMatrix[i][totalRowCol] += pitMatrix[i][j];
                pitMatrix[totalRowCol][i] += pitMatrix[j][i];
            }


        for (int i = 0; i < pitMatrixSize; i++) {
            if (maxValue < pitMatrix[totalRowCol][i]) maxValue = pitMatrix[totalRowCol][i];
            if (maxValue < pitMatrix[i][totalRowCol]) maxValue = pitMatrix[i][totalRowCol];
        }

        return maxValue;
    }


    private void countMutations(PitOutput newCommitPitOutput, boolean isNewCommit,
                                PitOutput oldCommitPitOutput, int[][] pitMatrix, DiffOutput diffOutput) {

        String newFileName, newClassName, newMethodName, oldFileName, oldClassName, oldMethodName;
        MutatedFile newMutatedFile, oldMutatedFile;
        MutatedFile.MutatedClass newMutatedClass, oldMutatedClass;
        MutatedFile.MutatedMethod newMutatedMethod, oldMutatedMethod;

        boolean renamedFile;

        for (Map.Entry<String, MutatedFile> newMutatedFileEntry : newCommitPitOutput.mutatedFiles.entrySet()) {

            newFileName = newMutatedFileEntry.getKey();
            newMutatedFile = newMutatedFileEntry.getValue();

            if (isNewCommit && diffOutput.changedFiles.containsKey(newFileName) &&
                    diffOutput.changedFiles.get(newFileName).changeType.equals("R")) {
                oldFileName = diffOutput.changedFiles.get(newFileName).oldFileName;
                renamedFile = true;
            } else {
                oldFileName = newFileName;
                renamedFile = false;
            }

            oldMutatedFile = oldCommitPitOutput.mutatedFiles.get(oldFileName);

            for (Map.Entry<String, MutatedFile.MutatedClass> newMutatedClassEntry : newMutatedFile.mutatedClasses.entrySet()) {

                newClassName = newMutatedClassEntry.getKey();
                newMutatedClass = newMutatedClassEntry.getValue();

                if (oldMutatedFile != null) {
                    oldClassName = renamedFile ? newClassName.replace(extractNameOnly(newFileName), extractNameOnly(oldFileName)) : newClassName;
                    oldMutatedClass = oldMutatedFile.mutatedClasses.get(oldClassName);
                } else {
                    oldClassName = null;
                    oldMutatedClass = null;
                }

                for (Map.Entry<String, MutatedFile.MutatedMethod> newMutatedMethodEntry : newMutatedClass.mutatedMethods.entrySet()) {

                    newMethodName = newMutatedMethodEntry.getKey();
                    newMutatedMethod = newMutatedMethodEntry.getValue();

                    if (oldMutatedClass != null) {
                        oldMethodName = renamedFile ? newMethodName.replace(extractNameOnly(newFileName), extractNameOnly(oldFileName)) : newMethodName;
                        oldMutatedMethod = oldMutatedClass.mutatedMethods.get(oldMethodName);
                    } else {
                        oldMethodName = null;
                        oldMutatedMethod = null;
                    }

                    // For the current method, iterate through each of its mutations
                    for (MutatedFile.Mutation newMutation : newMutatedMethod.mutations) {

                        // Initially, assume that the mutation does not exist in the old commit
                        int matrixRow = nonExistentRowCol;

                        // Check if the same mutated file, class and method was found in the old commit;
                        // if this condition is not met, then the above assumption holds
                        if (oldMutatedFile != null && oldMutatedClass != null && oldMutatedMethod != null) {

                            Iterator<MutatedFile.Mutation> oldMutationsIterator = oldMutatedMethod.mutations.listIterator();

                            // If the condition is met, we still need to iterate through each of the
                            // mutations from the old commit and see if the same one is found
                            while (oldMutationsIterator.hasNext()) {
                                MutatedFile.Mutation oldMutation = oldMutationsIterator.next();

                                int newMutationOldLineNo = newMutation.lineNo;

                                if (diffOutput.changedFiles.containsKey(newFileName)) {
                                    ChangedFile changedFile = diffOutput.changedFiles.get(newFileName);
                                    int mapLineNo = changedFile.newLinesMap.get(newMutation.lineNo);
                                    newMutationOldLineNo = changedFile.mergedLines.get(mapLineNo).oldLineNo;
                                }

                                boolean isSameMutation = newMutationOldLineNo == oldMutation.lineNo &&
                                        newMutation.index == oldMutation.index &&
                                        newMutation.mutator.equals(oldMutation.mutator) &&
                                        newMutation.description.equals(oldMutation.description);

                                if (isSameMutation) {
                                    // if the same mutation if found, then we need to add it to the
                                    // relevant row in the statistics matrix
                                    matrixRow = getMatrixRowCol(oldMutation.status);

                                    // and if we're looking at the new commit against the old commit (i.e. this is
                                    // the first run - see long explanation in buildPitMatrix method) remove the
                                    // mutation from the hash map storing the old commit
                                    if (isNewCommit) oldMutationsIterator.remove();

                                    // then break out of the while loop as we've found the mutation we were looking for
                                    break;
                                }
                            }
                        }

                        // the columns in the statistics matrix correspond to the new commit and are therefore
                        // fully determined by the status of the mutation in the new commit
                        int matrixCol = getMatrixRowCol(newMutation.status);


                        // BIG NOTE: This method is used for both runs, i.e. new commit vs old commit AND
                        // old commit vs new commit. However, the columns of the statistics matrix correspond
                        // to the new commit, while the rows correspond to the old commit.
                        if (isNewCommit)
                            // As such, if we are in the first run (i.e. new commit vs old commit) the variables
                            // that define the rows and columns of the statistic matrix hold the meaning given by
                            // their names:
                            pitMatrix[matrixRow][matrixCol]++;

                        else
                            // if we are in the second run however, we are effectively looking the transposed
                            // statistics matrix and therefore the meaning of the row and column variables is
                            // also transposed:
                            pitMatrix[matrixCol][matrixRow]++;

                    }
                    if (isNewCommit && oldMutatedMethod != null && oldMutatedMethod.mutations.size() == 0)
                        oldMutatedClass.mutatedMethods.remove(oldMethodName);
                }
                if (isNewCommit && oldMutatedClass != null && oldMutatedClass.mutatedMethods.size() == 0)
                    oldMutatedFile.mutatedClasses.remove(oldClassName);
            }
            if (isNewCommit && oldMutatedFile != null && oldMutatedFile.mutatedClasses.size() == 0)
                oldCommitPitOutput.mutatedFiles.remove(oldFileName);
        }
    }


    private String extractNameOnly(String qualifiedFileName) {
        return qualifiedFileName.substring(qualifiedFileName.lastIndexOf("/") + 1, qualifiedFileName.lastIndexOf("."));
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

        stringBuilder.append(colHeading0[0]);

        // number of spaces between "New commit" and "Old commit" headings on the first headings row
        paddingSpacesNo = digitsNo * 8 - colHeading0[0].length() + 3;
        stringBuilder.append(paddingSpaces(paddingSpacesNo));

        stringBuilder.append(colHeading0[1]);

        stringBuilder.append("\n");


// Assemble second headings row
        stringBuilder.append(paddingSpaces(12));

        for (int i = 0; i < totalRowCol; i++) {
            paddingSpacesNo = digitsNo - 3;   // colHeading1[i].length();
            stringBuilder.append(colHeading1[i]);
            stringBuilder.append(paddingSpaces(paddingSpacesNo));
        }

        stringBuilder.append(paddingSpaces(3));
        stringBuilder.append(colHeading1[totalRowCol]);

        stringBuilder.append("\n");

// Add matrix rows to output

        for (int i = 0; i < pitMatrixSize; i++) {

            stringBuilder.append(rowHeadings[i]);

            for (int j = 0; j < totalRowCol; j++)
                if (i == 0 & j == 0) {
                    paddingSpacesNo = digitsNo - 1;
                    stringBuilder.append("-");
                    stringBuilder.append(paddingSpaces(paddingSpacesNo));
                } else {
                    stringBuilder.append(String.format(format, pitMatrix[i][j]));
                }

            stringBuilder.append(paddingSpaces(3));


            if (i != totalRowCol)
                stringBuilder.append(String.format(format, pitMatrix[i][totalRowCol]));


            stringBuilder.append("\n");

        }

        return stringBuilder.toString();
    }


    private int getMatrixRowCol(String status) {
        switch (status) {
            case "KILLED":
                return killedRowCol;
            case "SURVIVED":
                return survivedRowCol;
            case "NO_COVERAGE":
                return noCoverageRowCol;
            case "NON_VIABLE":
                return nonViableRowCol;
            case "TIMED_OUT":
                return timedOutRowCol;
            case "MEMORY_ERROR":
                return memoryErrorRowCol;
            case "RUN_ERROR":
                return runErrorRowCol;
        }
        return -1;
    }


    private DiffOutput loadDiffOutput(String commitHash) {
        return (DiffOutput) loadOutput(commitHash, typeDiffMachineOutput);
    }


    private PitOutput loadPitOutput(String commitHash) {
        return (PitOutput) loadOutput(commitHash, typePitOutput);
    }


    private Object loadOutput(String commitHash, String outputType) {
        String outputFileName = getOutputFileName(commitHash, outputType, outputPath);
        try {
            switch (outputType) {
                case typeDiffMachineOutput:
                    return jsonHandler.loadDifFromJSON(outputFileName);
                case typePitOutput:
                    return jsonHandler.loadPitFromJSON(outputFileName);
            }
        } catch (Exception e) {
            System.err.println("The " + outputType + " output file for commit " + commitHash + " could not be loaded.");
        }
        return null;
    }


    private String getOutputFileName(String commit, String type, String directory) {
        List<String> fileList = filesInDirectory(directory);

        if (fileList == null) return null;

        for (String qualifiedName : fileList) {
            String fileName = Paths.get(qualifiedName).getFileName().toString();
            if (fileName.startsWith(type) && fileName.contains(commit)) return qualifiedName;
        }

        return null;
    }


    private List<String> filesInDirectory(String directory) {
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


    private File createTempPom(String repoPath, String pomFile) throws Exception {

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
        pitThreads.appendChild(xmlDoc.createTextNode("4"));

        pitConfiguration.appendChild(pitTimestampedReports);
        pitConfiguration.appendChild(pitOutputFormats);
        pitConfiguration.appendChild(pitThreads);

        pitPlugin.appendChild(pitGroupId);
        pitPlugin.appendChild(pitArtifactId);
        pitPlugin.appendChild(pitVersion);
        pitPlugin.appendChild(pitConfiguration);

        return pitPlugin;
    }


    private String createOutputDirectory() {
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


//    private Path getLatestPitReportPath(String pitReportPath, boolean pitReportPathRelative) {
//
//        Path latestPitReportPath = Paths.get((pitReportPathRelative ? projectPath : ""), pitReportPath);
//
//        try {
//            latestPitReportPath = Files.list(latestPitReportPath).filter(Files::isDirectory).max(Comparator.naturalOrder()).get();
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (NoSuchElementException e) {
//            e.printStackTrace();
//        }
//
//        System.out.println("\nLatest Pit Report Path: " + latestPitReportPath + "\n");
//
//        return latestPitReportPath;
//    }


    private String parseCommit(String commit) {

        if (commit.equals("") || commit.equals("HEAD") || commit.equals(originalGitBranch))
            return getCommitHash(commit);

        if (commit.startsWith("HEAD") || commit.startsWith(originalGitBranch)) {

            String tail = commit.substring(4);

            if (tail.equals("~")) return getCommitHash(commit);

            if (!tail.startsWith("~")) {

                System.out.println("The revision you specified is invalid.");
                System.out.println("Tip: enter the revision in form <refname>~<n>");
                App.systemExit(99);

            } else {

                String generationString = tail.substring(1);
                int generation = 0;

                try {
                    generation = Integer.valueOf(generationString);
                    if (generation < 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    System.out.println("The generation you specified is invalid: " + generationString);
                    System.out.println("Tip: the generation should be a positive integer");
                    App.systemExit(99);
                }

                if (generation > (commitsHashList.size() - 1)) {
                    System.out.println("The generation you specified exceeds the history of the branch.");
                    System.out.println("Tip: this branch has " + (commitsHashList.size() - 1) + " past commits");
                    App.systemExit(99);
                }
            }

            return getCommitHash(commit);
        }

        if (commit.length() == 7 && shortRevExists(commit)) return getCommitHash(commit);

        if (commit.length() == 40 && commitsHashList.contains(commit)) return commit;

        return null;
    }


    private boolean shortRevExists(String shortRev) {
        for (String commit : commitsHashList)
            if (commit.startsWith(shortRev)) return true;
        return false;
    }


    private String getGitBranch(String commit) {

        String gitOptions = gitOptionPath + projectPath;

        String command = gitRevParseCommand.replace(gitOptionsPlaceholder, gitOptions);
        command = command.replace("<revParseOptions>", revParseOptionAbbrevRef);
        command = command + commit;


        //commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));

        return String.join("", commandExecutor.getStandardOutput());
    }


    private String checkoutPitStatBranch() {

        String pitStatBranch = "pitstat" + startTime;

        String gitOptions = gitOptionPath + projectPath;

        String command = gitCheckoutCommand.replace(gitOptionsPlaceholder, gitOptions);
        command = command.replace("<checkoutOptions>", checkoutOptionNewBranch);
        command = command + pitStatBranch;

        //commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));

        return pitStatBranch;
    }


    private void checkoutOriginalBranch() {
        String gitOptions = gitOptionPath + projectPath;

        String command = gitCheckoutCommand.replace(gitOptionsPlaceholder, gitOptions);
        command = command.replace("<checkoutOptions>", "");
        command = command + originalGitBranch;

//        commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));
    }


    private void deletePitStatBranch() {
        String gitOptions = gitOptionPath + projectPath;

        String command = gitBranchCommand.replace(gitOptionsPlaceholder, gitOptions);
        command = command.replace("<branchOptions>", branchDeleteOption + branchForceOption);
        command = command + pitStatBranch;

//        commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));
    }


    private void rollBackTo(String commit) {

        String gitOptions = gitOptionPath + projectPath;

        String command = gitResetCommand.replace(gitOptionsPlaceholder, gitOptions);
        command = command.replace("<resetOptions>", resetHardOption);
        command = command + commit;

//        commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));
    }


    private List<String> getCommitsHashList() {
        String gitOptions = gitOptionNoPager + gitOptionPath + projectPath;

        String command = gitRevListCommand.replace(gitOptionsPlaceholder, gitOptions);
        command = command.replace("<revListOptions>", revListAllOption);

//        commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));

        return commandExecutor.getStandardOutput();
    }


    private String getCommitHash(String commit) {

        if (commit.length() == 0) return "";

        String gitOptions = gitOptionPath + projectPath;

        String command = gitRevParseCommand.replace(gitOptionsPlaceholder, gitOptions);
        command = command.replace("<revParseOptions>", "");
        command = command + commit;

//        commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));

        return commandExecutor.getStandardOutput().get(0);
    }


    private List<String> gitDiffNameStatus() {

        String gitOptions = gitOptionNoPager + gitOptionPath + projectPath;
        String diffOptions = diffOptionNameStatus + diffOptionFindCopiesHarder;
        String gitOldFile = "", gitNewFile = "";

        String command = buildGitDiffCommand(
                gitOptions,
                diffOptions,
                parentCommitHash,
                gitOldFile,
                currentCommitHash,
                gitNewFile
        );

//        commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));

        return commandExecutor.getStandardOutput();
    }

    private List<String> gitDiff(String changedFile, String newFile) {

        String gitOptions = gitOptionNoPager + gitOptionPath + projectPath;
        String diffOptions = diffOptionFindCopiesHarder + diffOptionNoContext;
        String gitOldFile = " -- " + changedFile;
        String gitNewFile = " -- " + newFile;

        String command = buildGitDiffCommand(gitOptions, diffOptions, parentCommitHash, gitOldFile, currentCommitHash, gitNewFile);

        //commandExecutor.execute(command, true);
        commandExecutor.execute(command);

//        System.out.println("Standard output:\n" + String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println("Standard error:\n" + String.join("\n", commandExecutor.getStandardError()));

        return commandExecutor.getStandardOutput();
    }

    private String buildGitDiffCommand(String gitOptions, String diffOptions, String oldCommit, String gitOldFile, String newCommit, String gitNewFile) {

        String command = gitDiffCommand;

        command = command.replace(gitOptionsPlaceholder, gitOptions);
        command = command.replace("<diffOptions>", diffOptions);
        command = command.replace("<oldCommit>", oldCommit);
        command = command.replace("<oldFile>", gitOldFile);
        command = command.replace("<newCommit>", newCommit);
        command = command.replace("<newFileName>", gitNewFile);

        return command;
    }


    private String paddingSpaces(int n) {
        return String.join("", Collections.nCopies(n, " "));
    }

}
