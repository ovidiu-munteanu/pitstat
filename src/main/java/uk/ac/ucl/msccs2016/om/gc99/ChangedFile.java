package uk.ac.ucl.msccs2016.om.gc99;

import java.util.List;

class ChangedFile {

    String oldFileName;
    String newFileName;
    String changeType;
    List<LineOfCode> oldFile;
    List<LineOfCode> newFile;


    ChangedFile(String oldFileName, String newFileName, String changeType,
                       List<LineOfCode> oldFile, List<LineOfCode> newFile) {
        this.oldFileName = oldFileName;
        this.newFileName = newFileName;
        this.changeType = changeType;
        this.oldFile = oldFile;
        this.newFile = newFile;
    }

}
