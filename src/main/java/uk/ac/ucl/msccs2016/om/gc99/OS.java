package uk.ac.ucl.msccs2016.om.gc99;

// Loosely based on System Utils class of Apache Commons Lang library

/**
 *
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
