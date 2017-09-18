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

import java.util.List;

/**
 * Utility class - represents a machine readable individual changed file record.
 */
class ChangedFile {

    /**
     * Utility class - represents a line of code.
     */
    static class LineOfCode {

        String code, diffStatus;
        int newLineNo, oldLineNo;

        /**
         *
         * @param code
         * @param diffStatus
         * @param newLineNo
         * @param oldLineNo
         */
        LineOfCode(String code, String diffStatus, int newLineNo, int oldLineNo) {
            this.code = code;
            this.diffStatus = diffStatus;
            this.newLineNo = newLineNo;
            this.oldLineNo = oldLineNo;
        }
    }

    String newFileName, oldFileName, diffStatus;

    List<LineOfCode> mergedLines;
    List<Integer> newLinesMap, oldLinesMap;

    /**
     *
     * @param newFileName
     * @param oldFileName
     * @param diffStatus
     * @param mergedLines
     * @param newLinesMap
     * @param oldLinesMap
     */
    ChangedFile(String newFileName, String oldFileName, String diffStatus,
                List<LineOfCode> mergedLines, List<Integer> newLinesMap, List<Integer> oldLinesMap) {
        this.newFileName = newFileName;
        this.oldFileName = oldFileName;
        this.diffStatus = diffStatus;
        this.mergedLines = mergedLines;
        this.newLinesMap = newLinesMap;
        this.oldLinesMap = oldLinesMap;
    }
}
