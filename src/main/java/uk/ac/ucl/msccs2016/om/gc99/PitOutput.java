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

import java.util.HashMap;

/**
 * Utility class - represents the PIT mutation test result record machine readable output.
 */
class PitOutput {
    String commitHash;
    HashMap<String, MutatedFile> mutatedFiles;

    /**
     *
     * @param commitHash
     * @param mutatedFiles
     */
    PitOutput(String commitHash, HashMap<String, MutatedFile> mutatedFiles) {
        this.commitHash = commitHash;
        this.mutatedFiles = mutatedFiles;
    }

    /**
     *
     * @return
     */
    PitOutput getClone() {
        return (PitOutput) JSONHandler.cloneObject(this);
    }
}
