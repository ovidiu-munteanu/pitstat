package uk.ac.ucl.msccs2016.om.gc99;

import java.util.List;

class ChangedFile {

    String newFileName;
    String oldFileName;
    String changeType;

    List<LineOfCode> mergedLines;
    List<Integer> newLinesMap, oldLinesMap;

    ChangedFile(String newFileName, String oldFileName, String changeType,
                List<LineOfCode> mergedLines, List<Integer> newLinesMap, List<Integer> oldLinesMap) {
        this.newFileName = newFileName;
        this.oldFileName = oldFileName;
        this.changeType = changeType;
        this.mergedLines = mergedLines;
        this.newLinesMap = newLinesMap;
        this.oldLinesMap = oldLinesMap;
    }
}
