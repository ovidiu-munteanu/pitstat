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
 * Loosely based on the source code of the System Utils class of Apache Commons Lang library.
 * [Online]. Available: <a href="https://github.com/apache/commons-lang/blob/master/src/main/java/org/apache/commons/lang3/SystemUtils.java" target="_blank">
 * https://github.com/apache/commons-lang/blob/master/src/main/java/org/apache/commons/lang3/SystemUtils.java</a>
 */
interface OS {
    String OS_NAME = System.getProperty("os.name").toLowerCase();

    boolean IS_AIX = OS_NAME.startsWith("aix");
    boolean IS_HP_UX = OS_NAME.startsWith("hp-ux");
    boolean IS_IRIX = OS_NAME.startsWith("irix");
    boolean IS_LINUX = OS_NAME.startsWith("linux");
    boolean IS_MAC = OS_NAME.startsWith("mac");
    boolean IS_FREE_BSD = OS_NAME.startsWith("freebsd");
    boolean IS_OPEN_BSD = OS_NAME.startsWith("openbsd");
    boolean IS_NET_BSD = OS_NAME.startsWith("netbsd");
    boolean IS_SOLARIS = OS_NAME.startsWith("solaris");
    boolean IS_SUN_OS = OS_NAME.startsWith("sunos");
    boolean IS_WINDOWS = OS_NAME.startsWith("windows");
}
