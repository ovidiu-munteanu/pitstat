package uk.ac.ucl.msccs2016.om.gc99;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static uk.ac.ucl.msccs2016.om.gc99.MainWorker.formatPitMatrixOutput;
import static uk.ac.ucl.msccs2016.om.gc99.Utils.paddingSpaces;
import static uk.ac.ucl.msccs2016.om.gc99.Utils.zipFileInputStream;

/**
 *
 */
public class FurtherDataProcessing implements Worker {
    /**
     *
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        String directory = args[0];

        FurtherDataProcessing furtherDataProcessing = new FurtherDataProcessing();

        furtherDataProcessing.countCommitsChangeType(directory, false);
        furtherDataProcessing.listSkippedOutput(directory);
        furtherDataProcessing.addMatrices(directory);
        furtherDataProcessing.createSeries(directory);

    }


    private final String
            MACHINE_OUTPUT_SUB_DIRECTORY = "machine",
            ANALYSIS_SUB_DIRECTORY = "analysis";

    private final JSONHandler jsonHandler;

    private int
            commitsWithNoChanges = 0,
            commitsWithOnlyJavaChanges = 0,
            commitsWithAnyFileChanges = 0,
            commitsWithNoJavaChanges = 0;

    private List<String>
            commitsWithNoChangesList,
            commitsWithOnlyJavaChangesList,
            commitsWithAnyFileChangesList,
            commitsWithNoJavaChangesList;

    private List<String> matrixFiles;

    /**
     *
     */
    private FurtherDataProcessing() {
        jsonHandler = new JSONHandler(true);
    }

    /**
     *
     * @param directory
     * @param allCommits
     */
    private void countCommitsChangeType(String directory, boolean allCommits) {

        if (!allCommits) {
            commitsWithNoChangesList = new ArrayList<>();
            commitsWithOnlyJavaChangesList = new ArrayList<>();
            commitsWithAnyFileChangesList = new ArrayList<>();
            commitsWithNoJavaChangesList = new ArrayList<>();
        }


        try {
            Files.list(Paths.get(directory, MACHINE_OUTPUT_SUB_DIRECTORY))
                    .forEach(file -> {
                        if (file.getFileName().toString().contains(TYPE_DIFF_MACHINE_OUTPUT))
                            checkDiff(file.toString(), allCommits);
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }


        StringBuilder commitsChangeTypeOutput = new StringBuilder();

        commitsChangeTypeOutput.append("Commits with no file changes:        " + commitsWithNoChanges);
        commitsChangeTypeOutput.append("\nCommits with Java changes only:      " + commitsWithOnlyJavaChanges);
        commitsChangeTypeOutput.append("\nCommits with Java and other changes: " + commitsWithAnyFileChanges);
        commitsChangeTypeOutput.append("\nCommits with other changes only:     " + commitsWithNoJavaChanges);
        commitsChangeTypeOutput.append("\n\n");


        if (!allCommits) {

            List<String> pitFiles = new ArrayList<>();
            List<String> pitFilesCommits = new ArrayList<>();
            List<String> changeFiles = new ArrayList<>();
            List<String> changeFilesCommits = new ArrayList<>();
            try {
                Files.list(Paths.get(directory))
                        .forEach(file -> {
                            if (file.getFileName().toString().contains(TYPE_PIT_MACHINE_OUTPUT)) {
                                pitFiles.add(file.toString());
                                pitFilesCommits.add(getCommit(file.getFileName().toString()));
                            } else if (file.getFileName().toString().contains(TYPE_CHANGES_MACHINE_OUTPUT)) {
                                changeFiles.add(file.toString());
                                changeFilesCommits.add(getCommit(file.getFileName().toString()));
                            }
                        });
            } catch (IOException e) {
                e.printStackTrace();
            }

            List<String> matrixFiles = new ArrayList<>();
            List<String> matrixFilesCommits = new ArrayList<>();

            try {
                Files.list(Paths.get(directory, MACHINE_OUTPUT_SUB_DIRECTORY))
                        .forEach(file -> {
                            if (file.getFileName().toString().contains(TYPE_MATRIX_MACHINE_OUTPUT)) {
                                matrixFiles.add(file.toString());
                                matrixFilesCommits.add(getCommit(file.getFileName().toString()));
                            }
                        });
            } catch (IOException e) {
                e.printStackTrace();
            }

            int
                    noChangeWithPitChange = 0,
                    noChangeWithPitChangeTimeOutOnly_Chg = 0,
                    noChangeWithPitChangeTimeOutOnly_Mtx = 0,
                    noChangeNOPit = 0,
                    noChangeNOPitChange = 0,

                    javaChangeWithPitChange = 0,
                    javaChangeWithPitChangeTimeOutOnly_Chg = 0,
                    javaChangeWithPitChangeTimeOutOnly_Mtx = 0,
                    javaChangeNOPit = 0,
                    javaChangeNOPitChange = 0,

                    anyChangeWithPitChange = 0,
                    anyChangeWithPitChangeTimeOutOnly_Chg = 0,
                    anyChangeWithPitChangeTimeOutOnly_Mtx = 0,
                    anyChangeNOPit = 0,
                    anyChangeNOPitChange = 0,

                    otherChangeWithPitChange = 0,
                    otherChangeWithPitChangeTimeOutOnly_Chg = 0,
                    otherChangeWithPitChangeTimeOutOnly_Mtx = 0,
                    otherChangeNOPit = 0,
                    otherChangeNOPitChange = 0;


            for (String commitWithNoChange : commitsWithNoChangesList) {

                commitsChangeTypeOutput.append(commitWithNoChange + "    no file change");

                int indexPit = pitFilesCommits.indexOf(commitWithNoChange);
                if (indexPit > -1)
                    commitsChangeTypeOutput.append("    Pitest successful    ");
                else {
                    commitsChangeTypeOutput.append("    Pitest NOT successful");
                    noChangeNOPit++;
                }


                int indexChg = changeFilesCommits.indexOf(commitWithNoChange);
                // noinspection Duplicates
                if (indexChg > -1) {
                    commitsChangeTypeOutput.append("    pit change output produced");
                    noChangeWithPitChange++;

                    int indexMatrix = matrixFilesCommits.indexOf(commitWithNoChange);
                    if (changesOnlyInTimedOutMutationTests_Mtx(matrixFiles.get(indexMatrix))) {
                        commitsChangeTypeOutput.append("  ->  timed out tests only (based on mtx file)");
                        noChangeWithPitChangeTimeOutOnly_Mtx++;
                    }

                    if (changesOnlyInTimedOutMutationTests_Chg(changeFiles.get(indexChg))) {
                        commitsChangeTypeOutput.append("    timed out tests only (based on chg file)");
                        noChangeWithPitChangeTimeOutOnly_Chg++;
                    }

                    commitsChangeTypeOutput.append("\n");

                } else {
                    commitsChangeTypeOutput.append("    NO pit change output\n");
                    noChangeNOPitChange++;
                }
            }

            commitsChangeTypeOutput.append("\n");


            for (String commitWithOnlyJavaChanges : commitsWithOnlyJavaChangesList) {

                commitsChangeTypeOutput.append(commitWithOnlyJavaChanges + "    only Java change");

                int indexPit = pitFilesCommits.indexOf(commitWithOnlyJavaChanges);
                if (indexPit > -1)
                    commitsChangeTypeOutput.append("    Pitest successful    ");
                else {
                    commitsChangeTypeOutput.append("    Pitest NOT successful");
                    javaChangeNOPit++;
                }


                int index = changeFilesCommits.indexOf(commitWithOnlyJavaChanges);
                // noinspection Duplicates
                if (index > -1) {
                    commitsChangeTypeOutput.append("    pit change output produced");
                    javaChangeWithPitChange++;

                    int indexMatrix = matrixFilesCommits.indexOf(commitWithOnlyJavaChanges);
                    if (changesOnlyInTimedOutMutationTests_Mtx(matrixFiles.get(indexMatrix))) {
                        commitsChangeTypeOutput.append("  ->  timed out tests only (based on mtx file)");
                        javaChangeWithPitChangeTimeOutOnly_Mtx++;
                    }

                    if (changesOnlyInTimedOutMutationTests_Chg(changeFiles.get(index))) {
                        commitsChangeTypeOutput.append("    timed out tests only (based on chg file)");
                        javaChangeWithPitChangeTimeOutOnly_Chg++;
                    }

                    commitsChangeTypeOutput.append("\n");

                } else {
                    commitsChangeTypeOutput.append("    NO pit change output\n");
                    javaChangeNOPitChange++;
                }
            }

            commitsChangeTypeOutput.append("\n");


            for (String commitWithAnyFileChanges : commitsWithAnyFileChangesList) {

                commitsChangeTypeOutput.append(commitWithAnyFileChanges + "    any file change");

                int indexPit = pitFilesCommits.indexOf(commitWithAnyFileChanges);
                if (indexPit > -1)
                    commitsChangeTypeOutput.append("    Pitest successful    ");
                else {
                    commitsChangeTypeOutput.append("    Pitest NOT successful");
                    anyChangeNOPit++;
                }

                int index = changeFilesCommits.indexOf(commitWithAnyFileChanges);
                // noinspection Duplicates
                if (index > -1) {
                    commitsChangeTypeOutput.append("    pit change output produced");
                    anyChangeWithPitChange++;

                    int indexMatrix = matrixFilesCommits.indexOf(commitWithAnyFileChanges);
                    if (changesOnlyInTimedOutMutationTests_Mtx(matrixFiles.get(indexMatrix))) {
                        commitsChangeTypeOutput.append("  ->  timed out tests only (based on mtx file)");
                        anyChangeWithPitChangeTimeOutOnly_Mtx++;
                    }

                    if (changesOnlyInTimedOutMutationTests_Chg(changeFiles.get(index))) {
                        commitsChangeTypeOutput.append("    timed out tests only (based on chg file)");
                        anyChangeWithPitChangeTimeOutOnly_Chg++;
                    }

                    commitsChangeTypeOutput.append("\n");

                } else {
                    commitsChangeTypeOutput.append("    NO pit change output\n");
                    anyChangeNOPitChange++;
                }
            }

            commitsChangeTypeOutput.append("\n");


            for (String commitWithNoJavaChange : commitsWithNoJavaChangesList) {

                commitsChangeTypeOutput.append(commitWithNoJavaChange + "    other file change");

                int indexPit = pitFilesCommits.indexOf(commitWithNoJavaChange);
                if (indexPit > -1)
                    commitsChangeTypeOutput.append("    Pitest successful    ");
                else {
                    commitsChangeTypeOutput.append("    Pitest NOT successful");
                    otherChangeNOPit++;
                }

                int index = changeFilesCommits.indexOf(commitWithNoJavaChange);
                // noinspection Duplicates
                if (index > -1) {
                    commitsChangeTypeOutput.append("    pit change output produced");
                    otherChangeWithPitChange++;

                    int indexMatrix = matrixFilesCommits.indexOf(commitWithNoJavaChange);
                    if (changesOnlyInTimedOutMutationTests_Mtx(matrixFiles.get(indexMatrix))) {
                        commitsChangeTypeOutput.append("  ->  timed out tests only (based on mtx file)");
                        otherChangeWithPitChangeTimeOutOnly_Mtx++;
                    }

                    if (changesOnlyInTimedOutMutationTests_Chg(changeFiles.get(index))) {
                        commitsChangeTypeOutput.append("    timed out tests only (based on chg file)");
                        otherChangeWithPitChangeTimeOutOnly_Chg++;
                    }

                    commitsChangeTypeOutput.append("\n");

                } else {
                    commitsChangeTypeOutput.append("    NO pit change output\n");
                    otherChangeNOPitChange++;
                }

            }

            commitsChangeTypeOutput.append("\n\nNo file change commits:");
            commitsChangeTypeOutput.append("\n\tWith pit change output: " + noChangeWithPitChange +
                    "\t(changes in timed out mutation tests only: " + noChangeWithPitChangeTimeOutOnly_Mtx + " / " + noChangeWithPitChangeTimeOutOnly_Chg + ")");
            commitsChangeTypeOutput.append("\n\tNO   pit change output: " + noChangeNOPitChange);
            commitsChangeTypeOutput.append("\n\tPitests NOT successful: " + noChangeNOPit);

            commitsChangeTypeOutput.append("\n\nOnly Java change commits:");
            commitsChangeTypeOutput.append("\n\tWith pit change output: " + javaChangeWithPitChange +
                    "\t(changes in timed out mutation tests only: " + javaChangeWithPitChangeTimeOutOnly_Mtx + " / " + javaChangeWithPitChangeTimeOutOnly_Chg + ")");
            commitsChangeTypeOutput.append("\n\tNO   pit change output: " + javaChangeNOPitChange);
            commitsChangeTypeOutput.append("\n\tPitests NOT successful: " + javaChangeNOPit);

            commitsChangeTypeOutput.append("\n\nAny file change commits:");
            commitsChangeTypeOutput.append("\n\tWith pit change output: " + anyChangeWithPitChange +
                    "\t(changes in timed out mutation tests only: " + anyChangeWithPitChangeTimeOutOnly_Mtx + " / " + anyChangeWithPitChangeTimeOutOnly_Chg + ")");
            commitsChangeTypeOutput.append("\n\tNO   pit change output: " + anyChangeNOPitChange);
            commitsChangeTypeOutput.append("\n\tPitests NOT successful: " + anyChangeNOPit);

            commitsChangeTypeOutput.append("\n\nOther file change commits:");
            commitsChangeTypeOutput.append("\n\tWith pit change output: " + otherChangeWithPitChange +
                    "\t(changes in timed out mutation tests only: " + otherChangeWithPitChangeTimeOutOnly_Mtx + " / " + otherChangeWithPitChangeTimeOutOnly_Chg + ")");
            commitsChangeTypeOutput.append("\n\tNO   pit change output: " + otherChangeNOPitChange);
            commitsChangeTypeOutput.append("\n\tPitests NOT successful: " + otherChangeNOPit);
        }

        String commitsChangeTypeOutputFileName = "commits-change-types" + (allCommits ? "-full-history" : "") + ".txt";
        Path commitsChangeTypeOutputPath = Paths.get(directory, ANALYSIS_SUB_DIRECTORY, commitsChangeTypeOutputFileName);
        try {
            Files.write(commitsChangeTypeOutputPath, commitsChangeTypeOutput.toString().getBytes());
        } catch (IOException e) {
            System.err.println("FurtherDataProcessing: can't write skipped output file for some reason");
            e.printStackTrace();
        }

        System.out.println(commitsChangeTypeOutput);
    }

    /**
     *
     * @param diffFile
     * @param allCommits
     */
    private void checkDiff(String diffFile, boolean allCommits) {

        DiffOutput diffOutput = new DiffOutput();
        try {
            diffOutput = (DiffOutput) jsonHandler.loadFromJSON(zipFileInputStream(diffFile), diffOutput);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (diffOutput.changedFiles == null || diffOutput.changedFiles.isEmpty()) {
            commitsWithNoChanges++;
            if (!allCommits)
                commitsWithNoChangesList.add(getCommit(Paths.get(diffFile).getFileName().toString()));
        } else {
            boolean sourceCodeChanges = false, otherFileChanges = false;
            for (Map.Entry<String, ChangedFile> changedFileEntry : diffOutput.changedFiles.entrySet()) {
                if (changedFileEntry.getKey().endsWith(".java"))
                    sourceCodeChanges = true;
                else
                    otherFileChanges = true;
                if (sourceCodeChanges && otherFileChanges) break;
            }
            if (sourceCodeChanges && otherFileChanges) {
                commitsWithAnyFileChanges++;
                if (!allCommits)
                    commitsWithAnyFileChangesList.add(getCommit(Paths.get(diffFile).getFileName().toString()));
            } else if (sourceCodeChanges) {
                commitsWithOnlyJavaChanges++;
                if (!allCommits)
                    commitsWithOnlyJavaChangesList.add(getCommit(Paths.get(diffFile).getFileName().toString()));
            } else {
                commitsWithNoJavaChanges++;
                if (!allCommits)
                    commitsWithNoJavaChangesList.add(getCommit(Paths.get(diffFile).getFileName().toString()));
            }
        }
    }

    /**
     *
     * @param matrixFile
     * @return
     */
    private boolean changesOnlyInTimedOutMutationTests_Mtx(String matrixFile) {
        MatrixOutput matrixOutput = new MatrixOutput();
        try {
            matrixOutput = (MatrixOutput) jsonHandler.loadFromJSON(zipFileInputStream(matrixFile), matrixOutput);
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = ROW_COL_NON_EXISTENT; i < ROW_COL_TOTAL; i++)
            for (int j = ROW_COL_NON_EXISTENT; j < ROW_COL_TOTAL; j++)
                if (i != j && i != ROW_COL_TIMED_OUT && j != ROW_COL_TIMED_OUT && matrixOutput.pitMatrix[i][j] > 0)
                    return false;

        return true;
    }

    /**
     *
     * @param pitChangeFile
     * @return
     */
    private boolean changesOnlyInTimedOutMutationTests_Chg(String pitChangeFile) {

        ChangedMutations changedMutations = new ChangedMutations();
        try {
            changedMutations = (ChangedMutations) jsonHandler.loadFromJSON(zipFileInputStream(pitChangeFile), changedMutations);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (changedMutations.removedMutations != null && !changedMutations.removedMutations.isEmpty() &&
                !allChangedFromTimedOutParent(changedMutations.removedMutations))
            return false;

        if (changedMutations.killedMutations != null && !changedMutations.killedMutations.isEmpty() &&
                !allChangedFromTimedOutParent(changedMutations.killedMutations))
            return false;

        if (changedMutations.survivedMutations != null && !changedMutations.survivedMutations.isEmpty() &&
                !allChangedFromTimedOutParent(changedMutations.survivedMutations))
            return false;

        if (changedMutations.noCoverageMutations != null && !changedMutations.noCoverageMutations.isEmpty() &&
                !allChangedFromTimedOutParent(changedMutations.noCoverageMutations))
            return false;

        if (changedMutations.nonViableMutations != null && !changedMutations.nonViableMutations.isEmpty() &&
                !allChangedFromTimedOutParent(changedMutations.nonViableMutations))
            return false;


        if (changedMutations.memoryErrorMutations != null && !changedMutations.memoryErrorMutations.isEmpty() &&
                !allChangedFromTimedOutParent(changedMutations.memoryErrorMutations))
            return false;

        if (changedMutations.runErrorMutations != null && !changedMutations.runErrorMutations.isEmpty() &&
                !allChangedFromTimedOutParent(changedMutations.runErrorMutations))
            return false;

        return true;
    }

    /**
     *
     * @param mutatedFiles
     * @return
     */
    private boolean allChangedFromTimedOutParent(HashMap<String, MutatedFile> mutatedFiles) {

        for (Map.Entry<String, MutatedFile> fileEntry :
                mutatedFiles.entrySet())

            for (Map.Entry<String, MutatedFile.MutatedClass> classEntry :
                    fileEntry.getValue().mutatedClasses.entrySet())

                for (Map.Entry<String, MutatedFile.MutatedMethod> methodEntry :
                        classEntry.getValue().mutatedMethods.entrySet())

                    for (MutatedFile.Mutation mutation :
                            methodEntry.getValue().mutations)

                        if (mutation.parentCommitData == null ||
                                !mutation.parentCommitData.pitStatus.equals(PIT_STATUS_TIMED_OUT))
                            return false;

        return true;
    }

    /**
     *
     * @param directory
     */
    private void listSkippedOutput(String directory) {

        List<String> diffCommits = new ArrayList<>();
        List<String> pitCommits = new ArrayList<>();
        List<String> matrixCommits = new ArrayList<>();
        List<String> changesCommits = new ArrayList<>();

        try {
            Files.list(Paths.get(directory))
                    .forEach(file -> {
                        String fileName = file.getFileName().toString();
                        if (fileName.contains(TYPE_DIFF_HUMAN_OUTPUT))
                            diffCommits.add(getCommit(fileName));
                        else if (fileName.contains(TYPE_PIT_MACHINE_OUTPUT))
                            pitCommits.add(getCommit(fileName));
                        else if (fileName.contains(TYPE_MATRIX_HUMAN_OUTPUT))
                            matrixCommits.add(getCommit(fileName));
                        else if (fileName.contains(TYPE_CHANGES_MACHINE_OUTPUT))
                            changesCommits.add(getCommit(fileName));
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }

        int rollbackNo = 0, yesPit = 0, noPit = 0, yesMatrix = 0, noMatrix = 0, yesChange = 0, noPitChange = 0, noPitTest = 0;

        StringBuilder skippedOutput = new StringBuilder();

        for (String diffCommit : diffCommits) {

            boolean pitExists = true;
            if (!pitCommits.contains(diffCommit)) {
                pitExists = false;
                skippedOutput.append("Rollback  " + rollbackNo + " \t:    " + diffCommit);
                skippedOutput.append("    no pit file");
                noPit++;
            } else
                yesPit++;


            boolean mtxExists = true;
            if (!matrixCommits.contains(diffCommit)) {
                mtxExists = false;
                if (pitExists)
                    skippedOutput.append("Rollback  " + rollbackNo + " \t:    " + diffCommit + paddingSpaces(15));
                skippedOutput.append("    no mtx file");
                noMatrix++;
            } else
                yesMatrix++;

            boolean chgExists = true;
            if (!changesCommits.contains(diffCommit)) {
                chgExists = false;
                if (pitExists) {
                    if (mtxExists) {
                        skippedOutput.append("Rollback  " + rollbackNo + " \t:    " + diffCommit + paddingSpaces(30));
                        noPitChange++;
                    } else
                        noPitTest++;
                } else
                    noPitTest++;
                skippedOutput.append("    no chg file");
            } else
                yesChange++;


            if (!(pitExists && mtxExists && chgExists)) skippedOutput.append("\n");

            rollbackNo++;
        }

        skippedOutput.append("\n\nGenerated pit mutation tests:  " + yesPit);
        skippedOutput.append("\nSkipped pit mutation tests:    " + noPit);

        skippedOutput.append("\n\nGenerated pit matrix files:    " + yesMatrix);
        skippedOutput.append("\nSkipped pit matrix files:      " + noMatrix);

        skippedOutput.append("\n\nGenerated change record files: " + yesChange);
        skippedOutput.append("\nSkipped change record files due to:");
        skippedOutput.append("\n    Missing pit mutation test:    " + noPitTest);
        skippedOutput.append("\n    No change in pit test matrix: " + noPitChange);


        String skippedOutputFileName = "skipped-output.txt";
        Path skippedOutputPath = Paths.get(directory, ANALYSIS_SUB_DIRECTORY, skippedOutputFileName);
        try {
            Files.write(skippedOutputPath, skippedOutput.toString().getBytes());
        } catch (IOException e) {
            System.err.println("FurtherDataProcessing: can't write skipped output file for some reason");
            e.printStackTrace();
        }

        System.out.println(skippedOutput);
        System.out.println();
    }

    /**
     *
     * @param directory
     * @return
     */
    private List<String> getMatrixFiles(String directory) {

        List<String> matrixFiles = new ArrayList<>();

        try {
            Files.list(Paths.get(directory, MACHINE_OUTPUT_SUB_DIRECTORY))
                    .forEach(file -> {
                        if (file.getFileName().toString().contains(TYPE_MATRIX_MACHINE_OUTPUT))
                            matrixFiles.add(file.toString());
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }

        return matrixFiles;

    }

    /**
     *
     * @param directory
     */
    private void addMatrices(String directory) {

        if (matrixFiles == null) matrixFiles = getMatrixFiles(directory);

        int[][] pitMatrix = new int[SIZE_PIT_MATRIX][SIZE_PIT_MATRIX];


        List<String>
                survived_Killed_commits_list = new ArrayList<>(),
                noCoverage_Killed_commits_list = new ArrayList<>(),
                survivedOrNoCoverage_Killed_commits_list = new ArrayList<>(),
                timedOut_Killed_commits_list = new ArrayList<>(),
                other_Killed_commits_list = new ArrayList<>(),
                noCoverage_Survived_commits_list = new ArrayList<>(),
                timedOut_Survived_commits_list = new ArrayList<>(),
                other_Survived_commits_list = new ArrayList<>(),
                timedOut_NoCoverage_commits_list = new ArrayList<>(),
                other_NoCoverage_commits_list = new ArrayList<>();

        List<String>
                killed_survived_commits_list = new ArrayList<>(),
                killed_noCoverage_commits_list = new ArrayList<>(),
                killed_survivedOrNoCoverage_commits_list = new ArrayList<>(),
                killed_timedOut_commits_list = new ArrayList<>(),
                killed_other_commits_list = new ArrayList<>(),
                survived_noCoverage_commits_list = new ArrayList<>(),
                survived_timedOut_commits_list = new ArrayList<>(),
                survived_other_commits_list = new ArrayList<>(),
                noCoverage_timedOut_commits_list = new ArrayList<>(),
                noCoverage_other_commits_list = new ArrayList<>();

        List<String>
                unlikelyOccurrence_commits_list = new ArrayList<>();


        int
                survived_Killed_commits = 0,
                noCoverage_Killed_commits = 0,
                survivedOrNoCoverage_Killed_commits = 0,
                timedOut_Killed_commits = 0,
                other_Killed_commits = 0,
                noCoverage_Survived_commits = 0,
                timedOut_Survived_commits = 0,
                other_Survived_commits = 0,
                timedOut_NoCoverage_commits = 0,
                other_NoCoverage_commits = 0;

        int
                killed_survived_commits = 0,
                killed_noCoverage_commits = 0,
                killed_survivedOrNoCoverage_commits = 0,
                killed_timedOut_commits = 0,
                killed_other_commits = 0,
                survived_noCoverage_commits = 0,
                survived_timedOut_commits = 0,
                survived_other_commits = 0,
                noCoverage_timedOut_commits = 0,
                noCoverage_other_commits = 0;

        int
                unlikelyOccurrence_commits = 0;

        for (String matrixFile : matrixFiles) {

            MatrixOutput matrixOutput = new MatrixOutput();
            try {
                matrixOutput = (MatrixOutput) jsonHandler.loadFromJSON(zipFileInputStream(matrixFile), matrixOutput);
            } catch (IOException e) {
                e.printStackTrace();
            }

            boolean
                    survived_Killed = false,
                    noCoverage_Killed = false,
                    survivedOrNoCoverage_Killed = false,
                    other_Killed = false,
                    other_Survived = false,
                    other_NoCoverage = false;

            boolean
                    killed_survived = false,
                    killed_noCoverage = false,
                    killed_survivedOrNoCoverage = false,
                    killed_other = false;
            boolean survived_other = false;
            boolean noCoverage_other = false;

            boolean unlikelyOccurrence = false;

            for (int i = 0; i < SIZE_PIT_MATRIX; i++) {
                for (int j = 0; j < SIZE_PIT_MATRIX; j++) {
                    pitMatrix[i][j] += matrixOutput.pitMatrix[i][j];

                    // noinspection Duplicates
                    if (j > ROW_COL_NON_EXISTENT && j < ROW_COL_TOTAL && j < i && i < ROW_COL_TOTAL) {
                        if (matrixOutput.pitMatrix[i][j] > 0) {
                            switch (j) {
                                case ROW_COL_KILLED:
                                    switch (i) {
                                        case ROW_COL_SURVIVED:
                                            if (!survived_Killed) {
                                                survived_Killed = true;
                                                survived_Killed_commits++;
                                                survived_Killed_commits_list.add(matrixOutput.currentCommitHash);
                                            }
                                            if (!survivedOrNoCoverage_Killed) {
                                                survivedOrNoCoverage_Killed = true;
                                                survivedOrNoCoverage_Killed_commits++;
                                                survivedOrNoCoverage_Killed_commits_list.add(matrixOutput.currentCommitHash);
                                            }
                                            break;
                                        case ROW_COL_NO_COVERAGE:
                                            if (!noCoverage_Killed) {
                                                noCoverage_Killed = true;
                                                noCoverage_Killed_commits++;
                                                noCoverage_Killed_commits_list.add(matrixOutput.currentCommitHash);
                                            }
                                            if (!survivedOrNoCoverage_Killed) {
                                                survivedOrNoCoverage_Killed = true;
                                                survivedOrNoCoverage_Killed_commits++;
                                                survivedOrNoCoverage_Killed_commits_list.add(matrixOutput.currentCommitHash);
                                            }
                                            break;
                                        case ROW_COL_TIMED_OUT:
                                            timedOut_Killed_commits++;
                                            timedOut_Killed_commits_list.add(matrixOutput.currentCommitHash);
                                            break;
                                        default:
                                            if (!other_Killed) {
                                                other_Killed = true;
                                                other_Killed_commits++;
                                                other_Killed_commits_list.add(matrixOutput.currentCommitHash);
                                            }
                                            break;
                                    }
                                    break;
                                case ROW_COL_SURVIVED:
                                    switch (i) {
                                        case ROW_COL_NO_COVERAGE:
                                            noCoverage_Survived_commits++;
                                            noCoverage_Survived_commits_list.add(matrixOutput.currentCommitHash);
                                            break;
                                        case ROW_COL_TIMED_OUT:
                                            timedOut_Survived_commits++;
                                            timedOut_Survived_commits_list.add(matrixOutput.currentCommitHash);
                                            break;
                                        default:
                                            if (!other_Survived) {
                                                other_Survived = true;
                                                other_Survived_commits++;
                                                other_Survived_commits_list.add(matrixOutput.currentCommitHash);
                                            }
                                            break;
                                    }
                                    break;
                                case ROW_COL_NO_COVERAGE:
                                    switch (i) {
                                        case ROW_COL_TIMED_OUT:
                                            timedOut_NoCoverage_commits++;
                                            timedOut_NoCoverage_commits_list.add(matrixOutput.currentCommitHash);
                                            break;
                                        default:
                                            if (!other_NoCoverage) {
                                                other_NoCoverage = true;
                                                other_NoCoverage_commits++;
                                                other_NoCoverage_commits_list.add(matrixOutput.currentCommitHash);
                                            }
                                            break;
                                    }
                                    break;
                                default:
                                    if (!unlikelyOccurrence) {
                                        unlikelyOccurrence = true;
                                        unlikelyOccurrence_commits++;
                                        unlikelyOccurrence_commits_list.add(matrixOutput.currentCommitHash);
                                    }
                                    break;
                            }
                        }
                    }

                    // noinspection Duplicates
                    if (i > ROW_COL_NON_EXISTENT && i < ROW_COL_TOTAL && i < j && j < ROW_COL_TOTAL) {
                        if (matrixOutput.pitMatrix[i][j] > 0) {
                            switch (i) {
                                case ROW_COL_KILLED:
                                    switch (j) {
                                        case ROW_COL_SURVIVED:
                                            if (!killed_survived) {
                                                killed_survived = true;
                                                killed_survived_commits++;
                                                killed_survived_commits_list.add(matrixOutput.currentCommitHash);

                                            }
                                            if (!killed_survivedOrNoCoverage) {
                                                killed_survivedOrNoCoverage = true;
                                                killed_survivedOrNoCoverage_commits++;
                                                killed_survivedOrNoCoverage_commits_list.add(matrixOutput.currentCommitHash);
                                            }
                                            break;
                                        case ROW_COL_NO_COVERAGE:
                                            if (!killed_noCoverage) {
                                                killed_noCoverage = true;
                                                killed_noCoverage_commits++;
                                                killed_noCoverage_commits_list.add(matrixOutput.currentCommitHash);
                                            }
                                            if (!killed_survivedOrNoCoverage) {
                                                killed_survivedOrNoCoverage = true;
                                                killed_survivedOrNoCoverage_commits++;
                                                killed_survivedOrNoCoverage_commits_list.add(matrixOutput.currentCommitHash);
                                            }
                                            break;
                                        case ROW_COL_TIMED_OUT:
                                            killed_timedOut_commits++;
                                            killed_timedOut_commits_list.add(matrixOutput.currentCommitHash);
                                            break;
                                        default:
                                            if (!killed_other) {
                                                killed_other = true;
                                                killed_other_commits++;
                                                killed_other_commits_list.add(matrixOutput.currentCommitHash);
                                            }
                                            break;
                                    }
                                    break;
                                case ROW_COL_SURVIVED:
                                    switch (j) {
                                        case ROW_COL_NO_COVERAGE:
                                            survived_noCoverage_commits++;
                                            survived_noCoverage_commits_list.add(matrixOutput.currentCommitHash);
                                            break;
                                        case ROW_COL_TIMED_OUT:
                                            survived_timedOut_commits++;
                                            survived_timedOut_commits_list.add(matrixOutput.currentCommitHash);
                                            break;
                                        default:
                                            if (!survived_other) {
                                                survived_other = true;
                                                survived_other_commits++;
                                                survived_other_commits_list.add(matrixOutput.currentCommitHash);
                                            }
                                            break;
                                    }
                                    break;
                                case ROW_COL_NO_COVERAGE:
                                    switch (j) {
                                        case ROW_COL_TIMED_OUT:
                                            noCoverage_timedOut_commits++;
                                            noCoverage_timedOut_commits_list.add(matrixOutput.currentCommitHash);
                                            break;
                                        default:
                                            if (!noCoverage_other) {
                                                noCoverage_other = true;
                                                noCoverage_other_commits++;
                                                noCoverage_other_commits_list.add(matrixOutput.currentCommitHash);
                                            }
                                            break;
                                    }
                                    break;
                                default:
                                    if (!unlikelyOccurrence) {
                                        unlikelyOccurrence = true;
                                        unlikelyOccurrence_commits++;
                                        unlikelyOccurrence_commits_list.add(matrixOutput.currentCommitHash);
                                    }
                                    break;
                            }
                        }
                    }
                }
            }
        }

        int maxMutations = 0;
        for (int i = 0; i < SIZE_PIT_MATRIX; i++)
            for (int j = 0; j < SIZE_PIT_MATRIX; j++)
                if (maxMutations < pitMatrix[i][j]) maxMutations = pitMatrix[i][j];

        String formattedPitMatrixOutput = formatPitMatrixOutput(pitMatrix, maxMutations, "N/A", "N/A");
        StringBuilder matrixSummationStatisticsOutput = new StringBuilder(formattedPitMatrixOutput);

        matrixSummationStatisticsOutput.append("\n\nNEW killed       OLD survived    " + survived_Killed_commits + "\tcommits");
        if (!survived_Killed_commits_list.isEmpty())
            for (String commit : survived_Killed_commits_list)
                matrixSummationStatisticsOutput.append("\n" + commit);

        matrixSummationStatisticsOutput.append("\n\nNEW killed       OLD no coverage    " + noCoverage_Killed_commits + "\tcommits");
        if (!noCoverage_Killed_commits_list.isEmpty())
            for (String commit : noCoverage_Killed_commits_list)
                matrixSummationStatisticsOutput.append("\n" + commit);

        matrixSummationStatisticsOutput.append("\n\nNEW killed       OLD survived or no coverage    " + survivedOrNoCoverage_Killed_commits + "\tcommits");
        if (!survivedOrNoCoverage_Killed_commits_list.isEmpty())
            for (String commit : survivedOrNoCoverage_Killed_commits_list)
                matrixSummationStatisticsOutput.append("\n" + commit);



        matrixSummationStatisticsOutput.append("\n\nNEW killed       OLD timed out                  " + timedOut_Killed_commits + "\tcommits");
        if (!timedOut_Killed_commits_list.isEmpty())
            for (String commit : timedOut_Killed_commits_list) matrixSummationStatisticsOutput.append("\n" + commit);

        matrixSummationStatisticsOutput.append("\n\nNEW killed       OLD other                      " + other_Killed_commits + "\tcommits");
        if (!other_Killed_commits_list.isEmpty())
            for (String commit : other_Killed_commits_list) matrixSummationStatisticsOutput.append("\n" + commit);

        matrixSummationStatisticsOutput.append("\n");

        matrixSummationStatisticsOutput.append("\n\nNEW survived     OLD no coverage                " + noCoverage_Survived_commits + "\tcommits");
        if (!noCoverage_Survived_commits_list.isEmpty())
            for (String commit : noCoverage_Survived_commits_list)
                matrixSummationStatisticsOutput.append("\n" + commit);

        matrixSummationStatisticsOutput.append("\n\nNEW survived     OLD timed out                  " + timedOut_Survived_commits + "\tcommits");
        if (!timedOut_Survived_commits_list.isEmpty())
            for (String commit : timedOut_Survived_commits_list) matrixSummationStatisticsOutput.append("\n" + commit);

        matrixSummationStatisticsOutput.append("\n\nNEW survived     OLD other                      " + other_Survived_commits + "\tcommits");
        if (!other_Survived_commits_list.isEmpty())
            for (String commit : other_Survived_commits_list) matrixSummationStatisticsOutput.append("\n" + commit);

        matrixSummationStatisticsOutput.append("\n");

        matrixSummationStatisticsOutput.append("\n\nNEW no coverage  OLD timed out                  " + timedOut_NoCoverage_commits + "\tcommits");
        if (!timedOut_NoCoverage_commits_list.isEmpty())
            for (String commit : timedOut_NoCoverage_commits_list)
                matrixSummationStatisticsOutput.append("\n" + commit);

        matrixSummationStatisticsOutput.append("\n\nNEW no coverage  OLD timed out                  " + other_NoCoverage_commits + "\tcommits");
        if (!other_NoCoverage_commits_list.isEmpty())
            for (String commit : other_NoCoverage_commits_list) matrixSummationStatisticsOutput.append("\n" + commit);


        matrixSummationStatisticsOutput.append("\n\n");


        matrixSummationStatisticsOutput.append("\n\nOLD killed       NEW survived    " + killed_survived_commits + "\tcommits");
        if (!killed_survived_commits_list.isEmpty())
            for (String commit : killed_survived_commits_list)
                matrixSummationStatisticsOutput.append("\n" + commit);

        matrixSummationStatisticsOutput.append("\n\nOLD killed       NEW no coverage    " + killed_noCoverage_commits + "\tcommits");
        if (!killed_noCoverage_commits_list.isEmpty())
            for (String commit : killed_noCoverage_commits_list)
                matrixSummationStatisticsOutput.append("\n" + commit);

        matrixSummationStatisticsOutput.append("\n\nOLD killed       NEW survived or no coverage    " + killed_survivedOrNoCoverage_commits + "\tcommits");
        if (!killed_survivedOrNoCoverage_commits_list.isEmpty())
            for (String commit : killed_survivedOrNoCoverage_commits_list)
                matrixSummationStatisticsOutput.append("\n" + commit);


        matrixSummationStatisticsOutput.append("\n\nOLD killed       NEW timed out                  " + killed_timedOut_commits + "\tcommits");
        if (!killed_timedOut_commits_list.isEmpty())
            for (String commit : killed_timedOut_commits_list) matrixSummationStatisticsOutput.append("\n" + commit);

        matrixSummationStatisticsOutput.append("\n\nOLD killed       NEW other                      " + killed_other_commits + "\tcommits");
        if (!killed_other_commits_list.isEmpty())
            for (String commit : killed_other_commits_list) matrixSummationStatisticsOutput.append("\n" + commit);

        matrixSummationStatisticsOutput.append("\n");

        matrixSummationStatisticsOutput.append("\n\nOLD survived     NEW no coverage                " + survived_noCoverage_commits + "\tcommits");
        if (!survived_noCoverage_commits_list.isEmpty())
            for (String commit : survived_noCoverage_commits_list)
                matrixSummationStatisticsOutput.append("\n" + commit);

        matrixSummationStatisticsOutput.append("\n\nOLD survived     NEW timed out                  " + survived_timedOut_commits + "\tcommits");
        if (!survived_timedOut_commits_list.isEmpty())
            for (String commit : survived_timedOut_commits_list) matrixSummationStatisticsOutput.append("\n" + commit);

        matrixSummationStatisticsOutput.append("\n\nOLD survived     NEW other                      " + survived_other_commits + "\tcommits");
        if (!survived_other_commits_list.isEmpty())
            for (String commit : survived_other_commits_list) matrixSummationStatisticsOutput.append("\n" + commit);

        matrixSummationStatisticsOutput.append("\n");

        matrixSummationStatisticsOutput.append("\n\nOLD no coverage  NEW timed out                  " + noCoverage_timedOut_commits + "\tcommits");
        if (!noCoverage_timedOut_commits_list.isEmpty())
            for (String commit : noCoverage_timedOut_commits_list)
                matrixSummationStatisticsOutput.append("\n" + commit);

        matrixSummationStatisticsOutput.append("\n\nOLD no coverage  NEW other                      " + noCoverage_other_commits + "\tcommits");
        if (!noCoverage_other_commits_list.isEmpty())
            for (String commit : noCoverage_other_commits_list) matrixSummationStatisticsOutput.append("\n" + commit);

        matrixSummationStatisticsOutput.append("\n\n");

        matrixSummationStatisticsOutput.append("\n\nUnlikely occurrences                            " + unlikelyOccurrence_commits + "\tcommits");
        if (!unlikelyOccurrence_commits_list.isEmpty())
            for (String commit : unlikelyOccurrence_commits_list) matrixSummationStatisticsOutput.append("\n" + commit);

        String pitMatrixSumFileName = "pit-matrix-sum.txt";
        Path pitMatrixSumPath = Paths.get(directory, ANALYSIS_SUB_DIRECTORY, pitMatrixSumFileName);
        try {
            Files.write(pitMatrixSumPath, matrixSummationStatisticsOutput.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }


        System.out.println(matrixSummationStatisticsOutput);
    }

    /**
     *
     * @param directory
     */
    private void createSeries(String directory) {
        if (matrixFiles == null) matrixFiles = getMatrixFiles(directory);

        List<Integer>
                newTotalRemoved = new ArrayList<>(),
                newTotalKilled = new ArrayList<>(),
                newTotalSurvived = new ArrayList<>(),
                newTotalNoCoverage = new ArrayList<>(),
                newTotalNonViable = new ArrayList<>(),
                newTotalTimedOut = new ArrayList<>(),
                newTotalMemError = new ArrayList<>(),
                newTotalRunError = new ArrayList<>();

        List<Integer>
                oldTotalRemoved = new ArrayList<>(),
                oldTotalKilled = new ArrayList<>(),
                oldTotalSurvived = new ArrayList<>(),
                oldTotalNoCoverage = new ArrayList<>(),
                oldTotalNonViable = new ArrayList<>(),
                oldTotalTimedOut = new ArrayList<>(),
                oldTotalMemError = new ArrayList<>(),
                oldTotalRunError = new ArrayList<>();

        List<Integer>
                unchangedKilled = new ArrayList<>(),
                unchangedSurvived = new ArrayList<>(),
                unchangedNoCoverage = new ArrayList<>(),
                unchangedNonViable = new ArrayList<>(),
                unchangedTimedOut = new ArrayList<>(),
                unchangedMemError = new ArrayList<>(),
                unchangedRunError = new ArrayList<>();


        for (String matrixFile : matrixFiles) {

            MatrixOutput matrixOutput = new MatrixOutput();
            try {
                matrixOutput = (MatrixOutput) jsonHandler.loadFromJSON(zipFileInputStream(matrixFile), matrixOutput);
            } catch (IOException e) {
                e.printStackTrace();
            }

            newTotalRemoved.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_NON_EXISTENT]);
            newTotalKilled.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_KILLED]);
            newTotalSurvived.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_SURVIVED]);
            newTotalNoCoverage.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_NO_COVERAGE]);
            newTotalNonViable.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_NON_VIABLE]);
            newTotalTimedOut.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_TIMED_OUT]);
            newTotalMemError.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_MEMORY_ERROR]);
            newTotalRunError.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_RUN_ERROR]);


            oldTotalRemoved.add(matrixOutput.pitMatrix[ROW_COL_NON_EXISTENT][ROW_COL_TOTAL]);
            oldTotalKilled.add(matrixOutput.pitMatrix[ROW_COL_KILLED][ROW_COL_TOTAL]);
            oldTotalSurvived.add(matrixOutput.pitMatrix[ROW_COL_SURVIVED][ROW_COL_TOTAL]);
            oldTotalNoCoverage.add(matrixOutput.pitMatrix[ROW_COL_NO_COVERAGE][ROW_COL_TOTAL]);
            oldTotalNonViable.add(matrixOutput.pitMatrix[ROW_COL_NON_VIABLE][ROW_COL_TOTAL]);
            oldTotalTimedOut.add(matrixOutput.pitMatrix[ROW_COL_TIMED_OUT][ROW_COL_TOTAL]);
            oldTotalMemError.add(matrixOutput.pitMatrix[ROW_COL_MEMORY_ERROR][ROW_COL_TOTAL]);
            oldTotalRunError.add(matrixOutput.pitMatrix[ROW_COL_RUN_ERROR][ROW_COL_TOTAL]);


            unchangedKilled.add(matrixOutput.pitMatrix[ROW_COL_KILLED][ROW_COL_KILLED]);
            unchangedSurvived.add(matrixOutput.pitMatrix[ROW_COL_SURVIVED][ROW_COL_SURVIVED]);
            unchangedNoCoverage.add(matrixOutput.pitMatrix[ROW_COL_NO_COVERAGE][ROW_COL_NO_COVERAGE]);
            unchangedNonViable.add(matrixOutput.pitMatrix[ROW_COL_NON_VIABLE][ROW_COL_NON_VIABLE]);
            unchangedTimedOut.add(matrixOutput.pitMatrix[ROW_COL_TIMED_OUT][ROW_COL_TIMED_OUT]);
            unchangedMemError.add(matrixOutput.pitMatrix[ROW_COL_MEMORY_ERROR][ROW_COL_MEMORY_ERROR]);
            unchangedRunError.add(matrixOutput.pitMatrix[ROW_COL_RUN_ERROR][ROW_COL_RUN_ERROR]);

        }

        StringBuilder seriesOutput = new StringBuilder();

        seriesOutput.append("New total removed:      ");
        seriesOutput.append(Arrays.toString(newTotalRemoved.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("New total killed:       ");
        seriesOutput.append(Arrays.toString(newTotalKilled.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("New total survived:     ");
        seriesOutput.append(Arrays.toString(newTotalSurvived.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("New total no Coverage:  ");
        seriesOutput.append(Arrays.toString(newTotalNoCoverage.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("New total non-viable:   ");
        seriesOutput.append(Arrays.toString(newTotalNonViable.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("New total timed Out:    ");
        seriesOutput.append(Arrays.toString(newTotalTimedOut.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("New total memory Error: ");
        seriesOutput.append(Arrays.toString(newTotalMemError.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("New total run Error:    ");
        seriesOutput.append(Arrays.toString(newTotalRunError.toArray()));
        seriesOutput.append("\n");

        seriesOutput.append("\n");

        seriesOutput.append("Old total removed:      ");
        seriesOutput.append(Arrays.toString(oldTotalRemoved.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("Old total killed:       ");
        seriesOutput.append(Arrays.toString(oldTotalKilled.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("Old total survived:     ");
        seriesOutput.append(Arrays.toString(oldTotalSurvived.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("Old total no Coverage:  ");
        seriesOutput.append(Arrays.toString(oldTotalNoCoverage.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("Old total non-viable:   ");
        seriesOutput.append(Arrays.toString(oldTotalNonViable.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("Old total timed Out:    ");
        seriesOutput.append(Arrays.toString(oldTotalTimedOut.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("Old total memory Error: ");
        seriesOutput.append(Arrays.toString(oldTotalMemError.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("Old total run Error:    ");
        seriesOutput.append(Arrays.toString(oldTotalRunError.toArray()));
        seriesOutput.append("\n");

        seriesOutput.append("\n");

        seriesOutput.append("Unchanged killed:       ");
        seriesOutput.append(Arrays.toString(unchangedKilled.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("Unchanged survived:     ");
        seriesOutput.append(Arrays.toString(unchangedSurvived.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("Unchanged no Coverage:  ");
        seriesOutput.append(Arrays.toString(unchangedNoCoverage.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("Unchanged non-viable:   ");
        seriesOutput.append(Arrays.toString(unchangedNonViable.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("Unchanged timed Out:    ");
        seriesOutput.append(Arrays.toString(unchangedTimedOut.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("Unchanged memory Error: ");
        seriesOutput.append(Arrays.toString(unchangedMemError.toArray()));
        seriesOutput.append("\n");
        seriesOutput.append("Unchanged run Error:    ");
        seriesOutput.append(Arrays.toString(unchangedRunError.toArray()));
        seriesOutput.append("\n");


        seriesOutput.append("\n");
        seriesOutput.append("\n");


        seriesOutput.append("\nNew total removed:      \n");
        seriesOutput.append(Arrays.toString(newTotalRemoved.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nNew total killed:       \n");
        seriesOutput.append(Arrays.toString(newTotalKilled.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nNew total survived:     \n");
        seriesOutput.append(Arrays.toString(newTotalSurvived.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nNew total no coverage:  \n");
        seriesOutput.append(Arrays.toString(newTotalNoCoverage.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nNew total non-viable:   \n");
        seriesOutput.append(Arrays.toString(newTotalNonViable.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nNew total timed out:    \n");
        seriesOutput.append(Arrays.toString(newTotalTimedOut.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nNew total memory error: \n");
        seriesOutput.append(Arrays.toString(newTotalMemError.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nNew total run error:    \n");
        seriesOutput.append(Arrays.toString(newTotalRunError.toArray()).replace(", ", "\n"));

        seriesOutput.append("\n");
        seriesOutput.append("\n");

        seriesOutput.append("\nOld total removed:      \n");
        seriesOutput.append(Arrays.toString(oldTotalRemoved.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nOld total killed:       \n");
        seriesOutput.append(Arrays.toString(oldTotalKilled.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nOld total survived:     \n");
        seriesOutput.append(Arrays.toString(oldTotalSurvived.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nOld total no coverage:  \n");
        seriesOutput.append(Arrays.toString(oldTotalNoCoverage.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nOld total non-viable:   \n");
        seriesOutput.append(Arrays.toString(oldTotalNonViable.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nOld total timed out:    \n");
        seriesOutput.append(Arrays.toString(oldTotalTimedOut.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nOld total memory error: \n");
        seriesOutput.append(Arrays.toString(oldTotalMemError.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nOld total run error:    \n");
        seriesOutput.append(Arrays.toString(oldTotalRunError.toArray()).replace(", ", "\n"));

        seriesOutput.append("\n");
        seriesOutput.append("\n");

        seriesOutput.append("\nUnchanged killed:       \n");
        seriesOutput.append(Arrays.toString(unchangedKilled.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nUnchanged survived:     \n");
        seriesOutput.append(Arrays.toString(unchangedSurvived.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nUnchanged no coverage:  \n");
        seriesOutput.append(Arrays.toString(unchangedNoCoverage.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nUnchanged non-viable:   \n");
        seriesOutput.append(Arrays.toString(unchangedNonViable.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nUnchanged timed out:    \n");
        seriesOutput.append(Arrays.toString(unchangedTimedOut.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nUnchanged memory error: \n");
        seriesOutput.append(Arrays.toString(unchangedMemError.toArray()).replace(", ", "\n"));
        seriesOutput.append("\n\nUnchanged run error:    \n");
        seriesOutput.append(Arrays.toString(unchangedRunError.toArray()).replace(", ", "\n"));


        String seriesOutputString = seriesOutput.toString();

        String seriesOutputFileName = "mutation-series.txt";
        Path seriesOutputPath = Paths.get(directory, ANALYSIS_SUB_DIRECTORY, seriesOutputFileName);
        try {
            Files.write(seriesOutputPath, seriesOutputString.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println(seriesOutputString);
    }

    /**
     *
     * @param outputFileName
     * @return
     */
    private static String getCommit(String outputFileName) {
        return outputFileName.substring(outputFileName.lastIndexOf("-") + 1, outputFileName.lastIndexOf("."));
    }

}