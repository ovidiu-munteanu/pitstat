package uk.ac.ucl.msccs2016.om.gc99;

import java.util.List;

class ChangedFile {

    static class LineOfCode {

        String code, diffStatus;
        int newLineNo, oldLineNo;

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
