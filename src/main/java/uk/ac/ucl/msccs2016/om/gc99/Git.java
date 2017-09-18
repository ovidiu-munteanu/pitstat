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

/**
 * Centralised location for constant string values that are used
 * throughout the classes that implement this interface.
 * <p>
 * <b>References:</b><br>
 * Git - Reference Manual.
 * [Online]. Available: <a href="https://git-scm.com/docs" target="_blank">
 * https://git-scm.com/docs</a>
 */
interface Git {

    String INDEX = "index";
    String NOT_STAGED = "not-staged";
    String HEAD = "HEAD";
    String HEAD_PARENT = HEAD + "~";

    String INITIAL_COMMIT = "initial-commit";
    String MAX_VALUE = "max";

    String GIT = "git ";
    String GIT_OPTIONS_PLACEHOLDER = "<gitOptions>";

    String GIT_COMMAND = GIT + GIT_OPTIONS_PLACEHOLDER;

    String GIT_OPTION_NO_PAGER = " --no-pager ";
    String GIT_OPTION_PATH = " -C ";

    String GIT_REV_LIST_COMMAND = GIT_COMMAND + " rev-list <revListOptions> ";
    String REV_LIST_ALL_OPTION = " --all ";

    String GIT_REV_PARSE_COMMAND = GIT_COMMAND + " rev-parse <revParseOptions> ";
    String REV_PARSE_OPTION_ABBREV_REF = " --abbrev-ref ";

    String GIT_CHECKOUT_COMMAND = GIT_COMMAND + " checkout <checkoutOptions> ";
    String CHECKOUT_OPTION_NEW_BRANCH = " -b ";
    String CHECKOUT_OPTION_FORCE = " -f ";

    String GIT_BRANCH_COMMAND = GIT_COMMAND + " branch <branchOptions> ";
    String BRANCH_DELETE_OPTION = " -D ";
    String BRANCH_FORCE_OPTION = " -f ";

    String GIT_RESET_COMMAND = GIT_COMMAND + " reset <resetOptions> ";
    String RESET_SOFT_OPTION = " --soft ";
    String RESET_MIXED_OPTION = " --mixed ";
    String RESET_HARD_OPTION = " --hard ";

    String GIT_DIFF_COMMAND = GIT_COMMAND + " diff <diffOptions> <oldCommit> <oldFile> <newCommit> <newFileName>";
    String DIFF_OPTION_NAME_STATUS = " --name-status ";
    String DIFF_OPTION_NAME_ONLY = " --name-only ";
    String DIFF_OPTION_CACHED = " --cached ";
    String DIFF_OPTION_NO_CONTEXT = " -U0 ";
    String DIFF_OPTION_FIND_COPIES = " -C ";
    String DIFF_OPTION_FIND_COPIES_HARDER = " -C -C ";

    String GIT_ADD_COMMAND = GIT_COMMAND + " add <addOptions> ";
    String ADD_OPTION_ALL = " -A ";
    String ADD_OPTION_DRY_RUN = " -n ";

    String GIT_COMMIT_COMMAND = GIT_COMMAND + " commit <commitOptions> ";
    String COMMIT_MESSAGE_OPTION = " -m ";
    String COMMIT_MESSAGE_PITSTAT_INDEX = "temp_commit_pitstat_index";
    String COMMIT_MESSAGE_PITSTAT_UNTRACKED = "temp_commit_pitstat_untracked";
}