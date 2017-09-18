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
 * Utility class - represents the changed mutations record machine readable output.
 */
class ChangedMutations {

    String currentCommitHash, parentCommitHash;

    HashMap<String, MutatedFile> removedMutations;
    HashMap<String, MutatedFile> killedMutations;
    HashMap<String, MutatedFile> survivedMutations;
    HashMap<String, MutatedFile> noCoverageMutations;
    HashMap<String, MutatedFile> nonViableMutations;
    HashMap<String, MutatedFile> timedOutMutations;
    HashMap<String, MutatedFile> memoryErrorMutations;
    HashMap<String, MutatedFile> runErrorMutations;

    /**
     *
     */
    ChangedMutations(){

    }

    /**
     *
     * @param currentCommitHash
     * @param parentCommitHash
     */
    ChangedMutations(String currentCommitHash, String parentCommitHash){
        this.currentCommitHash = currentCommitHash;
        this.parentCommitHash = parentCommitHash;
    }
}
