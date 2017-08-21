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
//        processData.showChart(directory);

    }


    private final String MACHINE_OUTPUT_SUB_DIRECTORY = "machine";
    private final String ANALYSIS_SUB_DIRECTORY = "analysis";

    private final JSONHandler jsonHandler;

    private int noChangeCommits = 0;
    private int noJavaChangeCommits = 0;


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

        System.out.println("Commits with no changes in any files:  " + noChangeCommits);
        System.out.println("Commits with no changes in Java code:  " + noJavaChangeCommits);

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

        List<String> matrixFiles = getMatrixFiles(directory);

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


    @SuppressWarnings("unchecked")
    private BoxAndWhiskerCategoryDataset pitMatrixDataSet(String directory) {

        List<String> matrixFiles = getMatrixFiles(directory);

        List<Integer> removed = new ArrayList<>();
        List<Integer> killed = new ArrayList<>();
        List<Integer> survived = new ArrayList<>();
        List<Integer> noCoverage = new ArrayList<>();
        List<Integer> nonViable = new ArrayList<>();
        List<Integer> timedOut = new ArrayList<>();
        List<Integer> memError = new ArrayList<>();
        List<Integer> runError = new ArrayList<>();


        int j = 0;

        for (String matrixFile : matrixFiles) {

            MatrixOutput matrixOutput = new MatrixOutput();
            try {
                matrixOutput = (MatrixOutput) jsonHandler.loadFromJSON(zipFileInputStream(matrixFile), matrixOutput);
            } catch (IOException e) {
                e.printStackTrace();
            }

//            System.out.print(++j + "\t" + Paths.get(matrixFile).getFileName() + "\t");

            if (matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_NON_EXISTENT] > 0)
                removed.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_NON_EXISTENT]);

            killed.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_KILLED]);
            survived.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_SURVIVED]);
            noCoverage.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_NO_COVERAGE]);
            nonViable.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_NON_VIABLE]);
            timedOut.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_TIMED_OUT]);
            memError.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_MEMORY_ERROR]);
            runError.add(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_RUN_ERROR]);

//            for (int i = 0; i < ROW_COL_TOTAL; i++)
//            if (matrixOutput.pitMatrix[ROW_COL_TOTAL][0] > 0)
//            System.out.print(matrixOutput.pitMatrix[ROW_COL_TOTAL][ROW_COL_NON_EXISTENT] + "\n");

//            System.out.println();
        }


        DefaultBoxAndWhiskerCategoryDataset dataSet = new DefaultBoxAndWhiskerCategoryDataset();


        dataSet.add(removed, COL_HEADING_1[ROW_COL_NON_EXISTENT], "New Commits Statistics");
        dataSet.add(killed, COL_HEADING_1[ROW_COL_KILLED], "New Commits Statistics");
        dataSet.add(survived, COL_HEADING_1[ROW_COL_SURVIVED], "New Commits Statistics");
        dataSet.add(noCoverage, COL_HEADING_1[ROW_COL_NO_COVERAGE], "New Commits Statistics");
        dataSet.add(nonViable, COL_HEADING_1[ROW_COL_NON_VIABLE], "New Commits Statistics");
        dataSet.add(timedOut, COL_HEADING_1[ROW_COL_TIMED_OUT], "New Commits Statistics");
        dataSet.add(memError, COL_HEADING_1[ROW_COL_MEMORY_ERROR], "New Commits Statistics");
        dataSet.add(runError, COL_HEADING_1[ROW_COL_RUN_ERROR], "New Commits Statistics");


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


    private void showChart(String directory) {

//        pitMatrixDataSet(directory);

        BoxAndWhiskerChart mutationsChart =
                new BoxAndWhiskerChart("Mutations Box-and-Whisker Chart", pitMatrixDataSet(directory));

        mutationsChart.pack();

        RefineryUtilities.centerFrameOnScreen(mutationsChart);

        mutationsChart.setVisible(true);


    }


    private static String getCommit(String outputFileName) {
        return outputFileName.substring(outputFileName.lastIndexOf("-") + 1, outputFileName.lastIndexOf("."));
    }

}