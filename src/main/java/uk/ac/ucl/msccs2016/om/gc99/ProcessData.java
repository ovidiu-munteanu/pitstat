package uk.ac.ucl.msccs2016.om.gc99;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.BoxAndWhiskerToolTipGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.data.statistics.BoxAndWhiskerCategoryDataset;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;

import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static uk.ac.ucl.msccs2016.om.gc99.MainWorker.formatPitMatrixOutput;
import static uk.ac.ucl.msccs2016.om.gc99.Utils.paddingSpaces;
import static uk.ac.ucl.msccs2016.om.gc99.Utils.zipFileInputStream;


public class ProcessData implements Worker {

    public static void main(String[] args) throws IOException {
        String directory = args[0];

        ProcessData processData = new ProcessData();

        processData.countIdenticalCommits(directory);

        processData.listSkippedOutput(directory);
        processData.addMatrices(directory);

        processData.createSeries(directory);


//        processData.showChart(directory);

    }


    private final String MACHINE_OUTPUT_SUB_DIRECTORY = "machine";
    private final String ANALYSIS_SUB_DIRECTORY = "analysis";

    private final JSONHandler jsonHandler;

    private int noChangeCommits = 0;
    private int noJavaChangeCommits = 0;

    private List<String> matrixFiles;

    private List<Integer> newTotalRemoved, newTotalKilled, newTotalSurvived, newTotalNoCoverage, newTotalNonViable, newTotalTimedOut, newTotalMemError, newTotalRunError;
    private List<Integer> oldTotalRemoved, oldTotalKilled, oldTotalSurvived, oldTotalNoCoverage, oldTotalNonViable, oldTotalTimedOut, oldTotalMemError, oldTotalRunError;
    private List<Integer> unchangedKilled, unchangedSurvived, unchangedNoCoverage, unchangedNonViable, unchangedTimedOut, unchangedMemError, unchangedRunError;


    private ProcessData() {
        jsonHandler = new JSONHandler(true);
    }


    private void countIdenticalCommits(String directory) {

        try {
            Files.list(Paths.get(directory, MACHINE_OUTPUT_SUB_DIRECTORY))
                    .forEach(file -> {
                        if (file.getFileName().toString().contains(TYPE_DIFF_MACHINE_OUTPUT))
                            checkDiff(file.toString());
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }

        StringBuilder identicalCommitsOutput = new StringBuilder();

        identicalCommitsOutput.append("Commits with no changes in any files:  " + noChangeCommits);
        identicalCommitsOutput.append("\n");
        identicalCommitsOutput.append("Commits with no changes in Java code:  " + noJavaChangeCommits);

        String identicalCommitsOutputFileName = "identical-commits.txt";
        Path identicalCommitsOutputPath = Paths.get(directory, ANALYSIS_SUB_DIRECTORY, identicalCommitsOutputFileName);
        try {
            Files.write(identicalCommitsOutputPath, identicalCommitsOutput.toString().getBytes());
        } catch (IOException e) {
            System.err.println("ProcessData: can't write skipped output file for some reason");
            e.printStackTrace();
        }

        System.out.println(identicalCommitsOutput);
    }


    private void checkDiff(String diffFile) {
        DiffOutput diffOutput = new DiffOutput();
        try {

            diffOutput = (DiffOutput) jsonHandler.loadFromJSON(zipFileInputStream(diffFile), diffOutput);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (diffOutput.changedFiles.isEmpty())
            noChangeCommits++;
        else {
            boolean identicalSourceCode = true;
            for (Map.Entry<String, ChangedFile> changedFileEntry : diffOutput.changedFiles.entrySet())
                if (changedFileEntry.getKey().endsWith(".java")) {
                    identicalSourceCode = false;
                    break;
                }
            if (identicalSourceCode) noJavaChangeCommits++;
        }
    }


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

        int rollbackNo = 0, noPit = 0, noPitMatrix = 0, noPitChange = 0;

        StringBuilder skippedOutput = new StringBuilder();

        for (String diffCommit : diffCommits) {

            boolean pitExists = true;
            if (!pitCommits.contains(diffCommit)) {
                pitExists = false;
                skippedOutput.append("Rollback  " + rollbackNo + " \t:    " + diffCommit);
                skippedOutput.append("    no pit file");
                noPit++;
            }

            boolean mtxExists = true;
            if (!matrixCommits.contains(diffCommit)) {
                mtxExists = false;
                if (pitExists)
                    skippedOutput.append("Rollback  " + rollbackNo + " \t:    " + diffCommit + paddingSpaces(15));
                skippedOutput.append("    no mtx file");
                noPitMatrix++;
            }

            boolean chgExists = true;
            if (!changesCommits.contains(diffCommit)) {
                chgExists = false;
                if (mtxExists)
                    if (pitExists)
                        skippedOutput.append("Rollback  " + rollbackNo + " \t:    " + diffCommit + paddingSpaces(30));
                    else
                        skippedOutput.append(paddingSpaces(15));
                skippedOutput.append("    no chg file");
                noPitChange++;
            }

            if (!(pitExists && mtxExists && chgExists)) skippedOutput.append("\n");

            rollbackNo++;
        }

        skippedOutput.append("\n" + "Skipped pit mutation tests:   " + noPit);
        skippedOutput.append("\n" + "Skipped results matrix files: " + noPitMatrix);
        skippedOutput.append("\n" + "No change pit mutation tests: " + noPitChange);


        String skippedOutputFileName = "skipped-output.txt";
        Path skippedOutputPath = Paths.get(directory, ANALYSIS_SUB_DIRECTORY, skippedOutputFileName);
        try {
            Files.write(skippedOutputPath, skippedOutput.toString().getBytes());
        } catch (IOException e) {
            System.err.println("ProcessData: can't write skipped output file for some reason");
            e.printStackTrace();
        }

        System.out.println(skippedOutput);
        System.out.println();
    }


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

    private void addMatrices(String directory) {

        if (matrixFiles == null) matrixFiles = getMatrixFiles(directory);

        int[][] pitMatrix = new int[SIZE_PIT_MATRIX][SIZE_PIT_MATRIX];

        for (String matrixFile : matrixFiles) {

            MatrixOutput matrixOutput = new MatrixOutput();
            try {
                matrixOutput = (MatrixOutput) jsonHandler.loadFromJSON(zipFileInputStream(matrixFile), matrixOutput);
            } catch (IOException e) {
                e.printStackTrace();
            }

            for (int i = 0; i < SIZE_PIT_MATRIX; i++)
                for (int j = 0; j < SIZE_PIT_MATRIX; j++)
                    pitMatrix[i][j] += matrixOutput.pitMatrix[i][j];
        }

        int maxMutations = 0;
        for (int i = 0; i < SIZE_PIT_MATRIX; i++)
            for (int j = 0; j < SIZE_PIT_MATRIX; j++)
                if (maxMutations < pitMatrix[i][j]) maxMutations = pitMatrix[i][j];

        String formattedPitMatrixOutput = formatPitMatrixOutput(pitMatrix, maxMutations, "N/A", "N/A");


        String pitMatrixSumFileName = "pit-matrix-sum.txt";
        Path pitMatrixSumPath = Paths.get(directory, ANALYSIS_SUB_DIRECTORY, pitMatrixSumFileName);
        try {
            Files.write(pitMatrixSumPath, formattedPitMatrixOutput.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }


        System.out.println(formattedPitMatrixOutput);
    }


    private void createSeries(String directory) {
        if (matrixFiles == null) matrixFiles = getMatrixFiles(directory);

        newTotalRemoved = new ArrayList<>();
        newTotalKilled = new ArrayList<>();
        newTotalSurvived = new ArrayList<>();
        newTotalNoCoverage = new ArrayList<>();
        newTotalNonViable = new ArrayList<>();
        newTotalTimedOut = new ArrayList<>();
        newTotalMemError = new ArrayList<>();
        newTotalRunError = new ArrayList<>();

        oldTotalRemoved = new ArrayList<>();
        oldTotalKilled = new ArrayList<>();
        oldTotalSurvived = new ArrayList<>();
        oldTotalNoCoverage = new ArrayList<>();
        oldTotalNonViable = new ArrayList<>();
        oldTotalTimedOut = new ArrayList<>();
        oldTotalMemError = new ArrayList<>();
        oldTotalRunError = new ArrayList<>();

        unchangedKilled = new ArrayList<>();
        unchangedSurvived = new ArrayList<>();
        unchangedNoCoverage = new ArrayList<>();
        unchangedNonViable = new ArrayList<>();
        unchangedTimedOut = new ArrayList<>();
        unchangedMemError = new ArrayList<>();
        unchangedRunError = new ArrayList<>();

        int j = 0;

        for (String matrixFile : matrixFiles) {

            MatrixOutput matrixOutput = new MatrixOutput();
            try {
                matrixOutput = (MatrixOutput) jsonHandler.loadFromJSON(zipFileInputStream(matrixFile), matrixOutput);
            } catch (IOException e) {
                e.printStackTrace();
            }

//            System.out.print(++j + "\t" + Paths.get(matrixFile).getFileName() + "\t");

//            if (matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_NON_EXISTENT] > 0)
                newTotalRemoved.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_NON_EXISTENT]);
            newTotalKilled.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_KILLED]);
            newTotalSurvived.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_SURVIVED]);
            newTotalNoCoverage.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_NO_COVERAGE]);
            newTotalNonViable.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_NON_VIABLE]);
            newTotalTimedOut.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_TIMED_OUT]);
            newTotalMemError.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_MEMORY_ERROR]);
            newTotalRunError.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_RUN_ERROR]);


//            if (matrixOutput.pitMatrix[ROW_COL_NON_EXISTENT][ROW_COL_TOTAL] > 0)
                oldTotalRemoved.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_NON_EXISTENT]);
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


//            for (int i = 0; i < ROW_COL_TOTAL; i++)
//            if (matrixOutput.pitMatrix[ROW_COL_TOTAL][0] > 0)
//            System.out.print(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_NON_EXISTENT] + "\n");

//            System.out.println();
        }

        StringBuilder seriesOutput = new StringBuilder();

        seriesOutput.append("New total removed:      "); seriesOutput.append(Arrays.toString(newTotalRemoved.toArray())); seriesOutput.append("\n");
        seriesOutput.append("New total killed:       "); seriesOutput.append(Arrays.toString(newTotalKilled.toArray())); seriesOutput.append("\n");
        seriesOutput.append("New total survived:     "); seriesOutput.append(Arrays.toString(newTotalSurvived.toArray())); seriesOutput.append("\n");
        seriesOutput.append("New total no Coverage:  "); seriesOutput.append(Arrays.toString(newTotalNoCoverage.toArray())); seriesOutput.append("\n");
        seriesOutput.append("New total non-viable:   "); seriesOutput.append(Arrays.toString(newTotalNonViable.toArray())); seriesOutput.append("\n");
        seriesOutput.append("New total timed Out:    "); seriesOutput.append(Arrays.toString(newTotalTimedOut.toArray())); seriesOutput.append("\n");
        seriesOutput.append("New total memory Error: "); seriesOutput.append(Arrays.toString(newTotalMemError.toArray())); seriesOutput.append("\n");
        seriesOutput.append("New total run Error:    "); seriesOutput.append(Arrays.toString(newTotalRunError.toArray())); seriesOutput.append("\n");

        seriesOutput.append("\n");

        seriesOutput.append("Old total removed:      "); seriesOutput.append(Arrays.toString(oldTotalRemoved.toArray())); seriesOutput.append("\n");
        seriesOutput.append("Old total killed:       "); seriesOutput.append(Arrays.toString(oldTotalKilled.toArray())); seriesOutput.append("\n");
        seriesOutput.append("Old total survived:     "); seriesOutput.append(Arrays.toString(oldTotalSurvived.toArray())); seriesOutput.append("\n");
        seriesOutput.append("Old total no Coverage:  "); seriesOutput.append(Arrays.toString(oldTotalNoCoverage.toArray())); seriesOutput.append("\n");
        seriesOutput.append("Old total non-viable:   "); seriesOutput.append(Arrays.toString(oldTotalNonViable.toArray())); seriesOutput.append("\n");
        seriesOutput.append("Old total timed Out:    "); seriesOutput.append(Arrays.toString(oldTotalTimedOut.toArray())); seriesOutput.append("\n");
        seriesOutput.append("Old total memory Error: "); seriesOutput.append(Arrays.toString(oldTotalMemError.toArray())); seriesOutput.append("\n");
        seriesOutput.append("Old total run Error:    "); seriesOutput.append(Arrays.toString(oldTotalRunError.toArray())); seriesOutput.append("\n");

        seriesOutput.append("\n");

        seriesOutput.append("Unchanged killed:       "); seriesOutput.append(Arrays.toString(unchangedKilled.toArray())); seriesOutput.append("\n");
        seriesOutput.append("Unchanged survived:     "); seriesOutput.append(Arrays.toString(unchangedSurvived.toArray())); seriesOutput.append("\n");
        seriesOutput.append("Unchanged no Coverage:  "); seriesOutput.append(Arrays.toString(unchangedNoCoverage.toArray())); seriesOutput.append("\n");
        seriesOutput.append("Unchanged non-viable:   "); seriesOutput.append(Arrays.toString(unchangedNonViable.toArray())); seriesOutput.append("\n");
        seriesOutput.append("Unchanged timed Out:    "); seriesOutput.append(Arrays.toString(unchangedTimedOut.toArray())); seriesOutput.append("\n");
        seriesOutput.append("Unchanged memory Error: "); seriesOutput.append(Arrays.toString(unchangedMemError.toArray())); seriesOutput.append("\n");
        seriesOutput.append("Unchanged run Error:    "); seriesOutput.append(Arrays.toString(unchangedRunError.toArray())); seriesOutput.append("\n");


        seriesOutput.append("\n"); seriesOutput.append("\n");

        
        seriesOutput.append("\nNew total removed:      \n"); seriesOutput.append(Arrays.toString(newTotalRemoved.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nNew total killed:       \n"); seriesOutput.append(Arrays.toString(newTotalKilled.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nNew total survived:     \n"); seriesOutput.append(Arrays.toString(newTotalSurvived.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nNew total no coverage:  \n"); seriesOutput.append(Arrays.toString(newTotalNoCoverage.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nNew total non-viable:   \n"); seriesOutput.append(Arrays.toString(newTotalNonViable.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nNew total timed out:    \n"); seriesOutput.append(Arrays.toString(newTotalTimedOut.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nNew total memory error: \n"); seriesOutput.append(Arrays.toString(newTotalMemError.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nNew total run error:    \n"); seriesOutput.append(Arrays.toString(newTotalRunError.toArray()).replace(", ","\n"));

        seriesOutput.append("\n"); seriesOutput.append("\n");
        
        seriesOutput.append("\nOld total removed:      \n"); seriesOutput.append(Arrays.toString(oldTotalRemoved.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nOld total killed:       \n"); seriesOutput.append(Arrays.toString(oldTotalKilled.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nOld total survived:     \n"); seriesOutput.append(Arrays.toString(oldTotalSurvived.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nOld total no coverage:  \n"); seriesOutput.append(Arrays.toString(oldTotalNoCoverage.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nOld total non-viable:   \n"); seriesOutput.append(Arrays.toString(oldTotalNonViable.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nOld total timed out:    \n"); seriesOutput.append(Arrays.toString(oldTotalTimedOut.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nOld total memory error: \n"); seriesOutput.append(Arrays.toString(oldTotalMemError.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nOld total run error:    \n"); seriesOutput.append(Arrays.toString(oldTotalRunError.toArray()).replace(", ","\n"));

        seriesOutput.append("\n"); seriesOutput.append("\n");

        seriesOutput.append("\nUnchanged killed:       \n"); seriesOutput.append(Arrays.toString(unchangedKilled.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nUnchanged survived:     \n"); seriesOutput.append(Arrays.toString(unchangedSurvived.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nUnchanged no coverage:  \n"); seriesOutput.append(Arrays.toString(unchangedNoCoverage.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nUnchanged non-viable:   \n"); seriesOutput.append(Arrays.toString(unchangedNonViable.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nUnchanged timed out:    \n"); seriesOutput.append(Arrays.toString(unchangedTimedOut.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nUnchanged memory error: \n"); seriesOutput.append(Arrays.toString(unchangedMemError.toArray()).replace(", ","\n"));
        seriesOutput.append("\n\nUnchanged run error:    \n"); seriesOutput.append(Arrays.toString(unchangedRunError.toArray()).replace(", ","\n"));


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


    private void showChart(String directory) {

        BoxAndWhiskerChart mutationsChart =
                new BoxAndWhiskerChart("Mutations Box-and-Whisker Chart", pitMatrixDataSet(directory));

        mutationsChart.pack();

        RefineryUtilities.centerFrameOnScreen(mutationsChart);

        mutationsChart.setVisible(true);

    }


    @SuppressWarnings("unchecked")
    private BoxAndWhiskerCategoryDataset pitMatrixDataSet(String directory) {

        createSeries(directory);

        DefaultBoxAndWhiskerCategoryDataset dataSet = new DefaultBoxAndWhiskerCategoryDataset();

        dataSet.add(newTotalRemoved, COL_HEADING_1[ROW_COL_NON_EXISTENT], "New Commits Statistics");
        dataSet.add(newTotalKilled, COL_HEADING_1[ROW_COL_KILLED], "New Commits Statistics");
        dataSet.add(newTotalSurvived, COL_HEADING_1[ROW_COL_SURVIVED], "New Commits Statistics");
        dataSet.add(newTotalNoCoverage, COL_HEADING_1[ROW_COL_NO_COVERAGE], "New Commits Statistics");
        dataSet.add(newTotalNonViable, COL_HEADING_1[ROW_COL_NON_VIABLE], "New Commits Statistics");
        dataSet.add(newTotalTimedOut, COL_HEADING_1[ROW_COL_TIMED_OUT], "New Commits Statistics");
        dataSet.add(newTotalMemError, COL_HEADING_1[ROW_COL_MEMORY_ERROR], "New Commits Statistics");
        dataSet.add(newTotalRunError, COL_HEADING_1[ROW_COL_RUN_ERROR], "New Commits Statistics");

        return dataSet;
    }


    private class BoxAndWhiskerChart extends ApplicationFrame {

        BoxAndWhiskerChart(String title, BoxAndWhiskerCategoryDataset pitMatrixDataSet) {
            super(title);


            CategoryAxis xAxis = new CategoryAxis("This is a test");
            NumberAxis yAxis = new NumberAxis("Mutations");

            yAxis.setAutoRangeIncludesZero(false);

            BoxAndWhiskerRenderer renderer = new BoxAndWhiskerRenderer();
            renderer.setFillBox(false);
            renderer.setBaseToolTipGenerator(new BoxAndWhiskerToolTipGenerator());


            CategoryPlot plot = new CategoryPlot(pitMatrixDataSet, xAxis, yAxis, renderer);


            JFreeChart chart = new JFreeChart(
                    "Box-and-Whisker Demo",
                    new Font("SansSerif", Font.BOLD, 14),
                    plot,
                    true
            );


            ChartPanel chartPanel = new ChartPanel(chart);
            chartPanel.setPreferredSize(new java.awt.Dimension(1000, 500));
            setContentPane(chartPanel);

        }

    }


    private static String getCommit(String outputFileName) {
        return outputFileName.substring(outputFileName.lastIndexOf("-") + 1, outputFileName.lastIndexOf("."));
    }

}