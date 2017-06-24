package uk.ac.ucl.msccs2016.om.gc99;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;


class MainWorker implements Worker {

    private final CommandExecutor commandExecutor;
    private final Invoker mvnInvoker;

    private String repoPath;
    private String pitReportsPath;
    private boolean pitReportsPathRelative;

    private String outputPath;

    private String originalGitBranch;
    private String pitStatBranch;

    private String oldCommit;
    private String newCommit;

    private String startTime;

    private JSONHandler jsonHandler;


    MainWorker(String repoPath, String oldCommit, String newCommit, String pitReportPath, boolean pitReportsPathRelative) {

        this.startTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        this.repoPath = repoPath;
        this.oldCommit = oldCommit;
        this.newCommit = newCommit;
        this.pitReportsPath = pitReportPath;
        this.pitReportsPathRelative = pitReportsPathRelative;

        this.commandExecutor = new CommandExecutor();
        this.mvnInvoker = new DefaultInvoker();

        this.jsonHandler = new JSONHandler<>();
    }


    @SuppressWarnings("unchecked")
    void doWork() throws Exception {

//        this.originalGitBranch = getGitBranch();
//        this.pitStatBranch = checkoutPitStatBranch();

//        checkoutOriginalBranch();
//        deletePitStatBranch();

        String oldCommitHash = getCommitHash(oldCommit);
        String newCommitHash = getCommitHash(newCommit);

        System.out.println("Old commit hash: " + oldCommitHash);
        System.out.println("New commit hash: " + newCommitHash);
        System.out.println();


        // git diff name-status between previous and current commit
        List<String> nameStatusList = gitDiffNameStatus();

        int createOutputFolderResult = createOutputFolder();
        if (createOutputFolderResult != 0) System.exit(createOutputFolderResult);

        int hashMapCapacity = (int) (nameStatusList.size() * 1.3);
        HashMap<String, ChangedFile> changedFiles = new HashMap<>(hashMapCapacity);

        for (String nameStatusLine : nameStatusList) {

            String diffStatus = Character.toString(nameStatusLine.charAt(0));

            String[] splitLine = nameStatusLine.split("\\s+");

            String changedFile = splitLine[1], newFile = null;

            switch (diffStatus) {
                case "A":
                    System.out.println("Added file:    " + changedFile);
                    break;
                case "D":
                    System.out.println("Deleted file:  " + changedFile);
                    break;
                case "M":
                    System.out.println("Modified file: " + changedFile);
                    break;
                case "C":
                    newFile = splitLine[2];
                    System.out.println("Copied file:   " + changedFile + " --> " + newFile);
                    break;
                case "R":
                    newFile = splitLine[2];
                    System.out.println("Renamed file:  " + changedFile + " --> " + newFile);
                    break;
                default:
                    System.out.print("Change type " + diffStatus + " unsupported: ");
                    for (int i = 1; i < splitLine.length; i++) {
                        if (i > 1) System.out.print(" --> ");
                        System.out.print(splitLine[i]);
                    }
                    System.out.println();
            }

            if (newFile == null && !diffStatus.equals("D")) newFile = changedFile;
            if (diffStatus.equals("A")) changedFile = null;

            List<LineOfCode> oldFileLines = null, newFileLines = null;

            if ("AD".contains(diffStatus)) {

                System.out.println();

            } else if ("MCR".contains(diffStatus)) {

                List<String> mapFileLines = Files.readAllLines(Paths.get(repoPath, newFile), StandardCharsets.UTF_8);
                int mapFileLinePointer = 1;

                oldFileLines = new ArrayList<>();
                newFileLines = new ArrayList<>();

                // git diff between previous and current version of the specific file
                List<String> diffOutputLines = gitDiff(changedFile, newFile);

                ListIterator<String> diffOutputIterator = diffOutputLines.listIterator();

                // Skip git diff header lines, i.e. skip until the first line starting with @@ is found
                while (diffOutputIterator.hasNext() && !diffOutputIterator.next().startsWith("@@")) ;

                // If the file was copied or renamed but not modified (100% similarity) then the while loop above
                // will have reached the end of the diff output so we need to continue with the next changed file
                if (!diffOutputIterator.hasNext()) {
                    ChangedFile changedFileEntry = new ChangedFile(changedFile, newFile, diffStatus, null, null);
                    changedFiles.put(changedFile, changedFileEntry);
                    continue;
                }

                // We consumed the "@@" line in the while loop above so we need to go back one iteration
                diffOutputIterator.previous();

                int diffOldPointer, diffOldLinesNo, diffNewPointer, diffNewLinesNo;
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


                        diffOldPointer = Integer.valueOf(split[0]);
                        diffOldLinesNo = Integer.valueOf(split[1]);
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

                                String unchangedLine = mapFileLines.get(mapFileLinePointer - 1);

                                // add the unchanged line of code to both the old and new file trackers
                                LineOfCode lineOfCode = new LineOfCode(unchangedLine, "UNCHANGED");
                                oldFileLines.add(lineOfCode);
                                newFileLines.add(lineOfCode);

                                String numberedLine = oldFileLinePointer + ":" + newFileLinePointer + ": " + unchangedLine;
                                mapFileLines.set(mapFileLinePointer - 1, numberedLine);

                                oldFileLinePointer++;
                                newFileLinePointer++;

                                mapFileLinePointer++;
                            }

                        }

                    } else if (diffLine.startsWith("-")) {

                        String oldLine = diffLine.substring(1);

                        oldFileLines.add(new LineOfCode(oldLine, "DELETED"));

                        diffLine = oldFileLinePointer + ":0: " + oldLine;

                        mapFileLines.add(mapFileLinePointer - 1, diffLine);

                        mapFileLinePointer++;
                        oldFileLinePointer++;
                        lineOffset++;

                    } else if (diffLine.startsWith("+")) {

                        String newLine = diffLine.substring(1);

                        newFileLines.add(new LineOfCode(newLine, "ADDED"));

                        diffLine = "0:" + newFileLinePointer + ": " + newLine;


                        mapFileLines.set(mapFileLinePointer - 1, diffLine);

                        mapFileLinePointer++;
                        newFileLinePointer++;

                    } else {
                        // For a copied file git diff also outputs a few lines with details about the source of the
                        // copy; we don't need this information so we stop parsing and skip over it
                        break;
                    }
                }

                // write out any remaining lines after the last changed line
                ListIterator<String> mapFileIterator = mapFileLines.listIterator(mapFileLinePointer - 1);
                while (mapFileIterator.hasNext()) {

                    String unchangedLine = mapFileIterator.next();

                    LineOfCode lineOfCode = new LineOfCode(unchangedLine, "UNCHANGED");
                    oldFileLines.add(lineOfCode);
                    newFileLines.add(lineOfCode);

                    String mapFileLine = oldFileLinePointer + ":" + newFileLinePointer + ": " + unchangedLine;

                    mapFileIterator.set(mapFileLine);

                    oldFileLinePointer++;
                    newFileLinePointer++;
                }

//  The following section of code deals only with printing out the map file

                // Calculate number of characters to use in formatting of line number based on number of lines in file
                // i.e. number of characters = 1 + digits in number of lines
                int digitsNo = Math.max(4, 1 + Integer.toString(mapFileLines.size()).length());
                String format = "%-" + digitsNo + "d";

                String paddingSpaces = String.join("", Collections.nCopies(digitsNo - 3, " "));

                System.out.println("Mapping of line changes:");
                System.out.println("OLD" + paddingSpaces + ": NEW" + paddingSpaces + ":");

//                int lineNo = 0;

                for (String mapLine : mapFileLines) {

                    int oldLineNo = Integer.valueOf(mapLine.substring(0, mapLine.indexOf(":")));
                    String oldLineIndicator = oldLineNo == 0 ? "N/E" + paddingSpaces : String.format(format, oldLineNo);

                    mapLine = mapLine.substring(mapLine.indexOf(":") + 1);

                    int newLineNo = Integer.valueOf(mapLine.substring(0, mapLine.indexOf(":")));
                    String newLineIndicator = newLineNo == 0 ? "DEL" + paddingSpaces : String.format(format, newLineNo);

                    mapLine = mapLine.substring(mapLine.indexOf(":") + 1);

                    mapLine = oldLineIndicator + ": " + newLineIndicator + ": " + mapLine;

                    // add line colour, i.e. green if added line, red if deleted line
                    if (oldLineNo == 0) {
                        mapLine = ANSI_GREEN + mapLine + ANSI_RESET;
                    } else if (newLineNo == 0) {
                        mapLine = ANSI_RED + mapLine + ANSI_RESET;
                    }

                    // TODO Decide whether to include map output line numbers
//                    String mapLineIndicator = String.format(format, ++lineNo);
//                    mapLine = mapLineIndicator + ": " + mapLine;

                    System.out.println(mapLine);
                }
                System.out.println();

// End of printing out section of code

            } else {

                // TODO handle other file types of file changes?
            }

            ChangedFile changedFileEntry = new ChangedFile(changedFile, newFile, diffStatus, oldFileLines, newFileLines);

            if (!diffStatus.equals("D")) {
                changedFiles.put(newFile, changedFileEntry);
            } else {
                changedFiles.put(changedFile, changedFileEntry);
            }

        }

        System.out.println("Modified files: " + nameStatusList.size() + "\n");


// Write output file with results of git diff

        String outputTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        String diffOutputFileName = diffOutputBaseFileName.replace("<date-hash>", outputTime + "-" + newCommitHash);

        Path diffOutputPath = Paths.get(outputPath, diffOutputFileName);
        DiffOutput diffOutput = new DiffOutput(oldCommitHash, newCommitHash, changedFiles);

        jsonHandler.saveToJSON(diffOutput, diffOutputPath.toString());


// run Pit Mutation Testing on current commit

        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(new File(repoPath + "/pom.xml"));
        request.setGoals(Arrays.asList(mvnGoalTest, mvnGoalPitest));
        request.setBatchMode(true);

        InvocationResult result = mvnInvoker.execute(request);

        if (result.getExitCode() != 0)
            if (result.getExecutionException() != null) {
                throw new Exception("Maven invocation failed.", result.getExecutionException());
            } else {
                throw new Exception("Maven invocation failed. Exit code: " + result.getExitCode());
            }


        Path latestPitReportPath = getLatestPitReportPath(pitReportsPath, pitReportsPathRelative);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(latestPitReportPath.resolve("mutations.xml").toString()));
        NodeList mutationsList = doc.getElementsByTagName("mutation");

        hashMapCapacity = (int) (mutationsList.getLength() * 1.3);
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

            String mutatedFileName = mvnSrcPath + "/" + packagePath + "/" + sourceFileElement;

            MutatedFile mutatedFile;
            if (mutatedFiles.containsKey(mutatedFileName)) {
                mutatedFile = mutatedFiles.get(mutatedFileName);
            } else {
                mutatedFile = new MutatedFile();

                if (changedFiles.containsKey(mutatedFileName)) {
                    mutatedFile.changeType = changedFiles.get(mutatedFileName).changeType;
                } else {
                    mutatedFile.changeType = "UNCHANGED";
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
                killingTest.testFile = killingTestElement.substring(killingTestElement.lastIndexOf("(") + 1, killingTestElement.length() - 1) + ".java";
                mutation.killingTest = killingTest;
            } else {
                mutation.killingTest = null;
            }


            if (mutatedFile.changeType.equals("UNCHANGED")) {

                mutation.changeStatus = "UNCHANGED";

            } else if ("AC".contains(mutatedFile.changeType)) {

                mutation.changeStatus = "ADDED";

            } else if ("MR".contains(mutatedFile.changeType)) {

                mutation.changeStatus = changedFiles.get(mutatedFileName).newFile.get(mutation.lineNo).status;

            } else {
                // unexpected
                System.out.println("SOMETHING'S BROKEN!!!");
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

        String pitOutputFileName = pitOutputBaseFileName.replace("<date-hash>", outputTime + "-" + newCommitHash);

        Path pitOutputPath = Paths.get(outputPath, pitOutputFileName);
        PitOutput pitOutput = new PitOutput(newCommitHash, mutatedFiles);

        jsonHandler.saveToJSON(pitOutput, pitOutputPath.toString());


    }


    private int createOutputFolder() {

        Path outputPath = Paths.get(repoPath, pitStatReportsPath);

        try {
            // create all output folders and sub-folders if they don't already exist
            Files.createDirectories(outputPath);
        } catch (FileAlreadyExistsException e) {
            e.printStackTrace();

            // file exists but is not a folder
            return 99;
        } catch (IOException e) {
            e.printStackTrace();

            // some other IO Exception has occurred
            return 1;
        }

        outputPath = Paths.get(outputPath.toString(), startTime);

        try {
            // create sub-folder for current run -> current date & time
            this.outputPath = Files.createDirectory(outputPath).toString();
        } catch (FileAlreadyExistsException e) {
            e.printStackTrace();

            // file exists
            return 98;
        } catch (IOException e) {
            e.printStackTrace();

            // some other IO Exception has occurred
            return 1;
        }

        return 0;
    }


    private Path getLatestPitReportPath(String pitReportPath, boolean pitReportPathRelative) {

        Path latestPitReportPath = Paths.get((pitReportPathRelative ? repoPath : ""), pitReportPath);

        try {
            latestPitReportPath = Files.list(latestPitReportPath).filter(Files::isDirectory).max(Comparator.naturalOrder()).get();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchElementException e) {
            e.printStackTrace();
        }

//        System.out.println("\nLatest Pit Report Path: " + latestPitReportPath + "\n");

        return latestPitReportPath;
    }


    private String getGitBranch() {
        String gitOptions = gitOptionPath + wrapInvCommas(repoPath);
        String command = gitGetCurrentBranchCommand.replace("<gitOptions>", gitOptions);

        //commandExecutor.executeCommand(command, true);
        commandExecutor.executeCommand(command);

//        System.out.println(String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println(String.join("\n", commandExecutor.getStandardError());

        return String.join("", commandExecutor.getStandardOutput());
    }


    private String checkoutPitStatBranch() {

        String pitStatBranch = "pitstat" + startTime;

        String gitOptions = gitOptionPath + wrapInvCommas(repoPath);

        String command = gitCheckoutBranchCommand.replace("<gitOptions>", gitOptions);
        command = command.replace("<checkoutOptions>", checkoutOptionNewBranch);
        command = command + pitStatBranch;

        //commandExecutor.executeCommand(command, true);
        commandExecutor.executeCommand(command);

//        System.out.println(String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println(String.join("\n", commandExecutor.getStandardError());

        return pitStatBranch;
    }


    private void checkoutOriginalBranch() {
        String gitOptions = gitOptionPath + wrapInvCommas(repoPath);

        String command = gitCheckoutBranchCommand.replace("<gitOptions>", gitOptions);
        command = command.replace("<checkoutOptions>", "");
        command = command + originalGitBranch;

//        commandExecutor.executeCommand(command, true);
        commandExecutor.executeCommand(command);

//        System.out.println(String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println(String.join("\n", commandExecutor.getStandardError()));
    }


    private void deletePitStatBranch() {
        String gitOptions = gitOptionPath + wrapInvCommas(repoPath);

        String command = gitBranchCommand.replace("<gitOptions>", gitOptions);
        command = command.replace("<branchOptions>", branchDeleteOption + branchForceOption);
        command = command + pitStatBranch;

//        commandExecutor.executeCommand(command, true);
        commandExecutor.executeCommand(command);

//        System.out.println(String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println(String.join("\n", commandExecutor.getStandardError()));
    }


    private String getCommitHash(String commit) {

        if (commit.length() == 0) return "no hash : staged changes not committed";

        String gitOptions = gitOptionPath + wrapInvCommas(repoPath);
        String command = gitRevParseCommand.replace("<gitOptions>", gitOptions) + commit;

        //commandExecutor.executeCommand(command, true);
        commandExecutor.executeCommand(command);

//        System.out.println(String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println(String.join("\n", commandExecutor.getStandardError());

        return String.join("", commandExecutor.getStandardOutput());
    }


    private List<String> gitDiffNameStatus() {

        String gitOptions = gitOptionNoPager + gitOptionPath + wrapInvCommas(repoPath);
        String diffOptions = diffOptionNameStatus + diffOptionFindCopiesHarder;
        String gitOldFile = "", gitNewFile = "";

        String command = buildGitDiffCommand(gitOptions, diffOptions, oldCommit, gitOldFile, newCommit, gitNewFile);

//        commandExecutor.executeCommand(command, true);
        commandExecutor.executeCommand(command);

        String commandStandardOutput = String.join("\n", commandExecutor.getStandardOutput());
//        String commandStandardError = String.join("\n", commandExecutor.getStandardError());

        if (commandStandardOutput.length() == 0) {
            System.out.println("PitStat: No difference between " + oldCommit + " and " +
                    (newCommit.equals("") ? "staged changes (maybe forgot to stage changes?)" : newCommit));
            System.exit(0);
        }

//        System.out.println("Standard output:\n" + commandStandardOutput);
//        System.out.println("Standard error:\n" + commandStandardError);

        return commandExecutor.getStandardOutput();
    }

    private List<String> gitDiff(String changedFile, String newFile) {

        String gitOptions = gitOptionNoPager + gitOptionPath + wrapInvCommas(repoPath);
        String diffOptions = diffOptionFindCopiesHarder + diffOptionNoContext;
        String gitOldFile = " -- " + wrapInvCommas(changedFile);
        String gitNewFile = " -- " + wrapInvCommas(newFile);

        String command = buildGitDiffCommand(gitOptions, diffOptions, oldCommit, gitOldFile, newCommit, gitNewFile);

        //commandExecutor.executeCommand(command, true);
        commandExecutor.executeCommand(command);

//        System.out.println(String.join("\n", commandExecutor.getStandardOutput()));
//        System.out.println(String.join("\n", commandExecutor.getStandardError());

        return commandExecutor.getStandardOutput();
    }

    private String buildGitDiffCommand(String gitOptions, String diffOptions, String oldCommit, String gitOldFile, String newCommit, String gitNewFile) {

        String command = gitDiffCommand;

        command = command.replace("<gitOptions>", gitOptions);
        command = command.replace("<diffOptions>", diffOptions);
        command = command.replace("<oldCommit>", oldCommit);
        command = command.replace("<oldFile>", gitOldFile);
        command = command.replace("<newCommit>", newCommit);
        command = command.replace("<newFileName>", gitNewFile);

        return command;
    }

    private String wrapInvCommas(String s) {
        return "\"" + s + "\"";
    }

}
