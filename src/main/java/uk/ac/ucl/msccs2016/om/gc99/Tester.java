package uk.ac.ucl.msccs2016.om.gc99;


import org.apache.commons.lang3.SystemUtils;

public class Tester {

    public static void main(String[] args) throws Exception {
        String shutdownCommand = null;

        int timeout = 30;

        if (SystemUtils.IS_OS_AIX)
            shutdownCommand = "shutdown -Fh " + timeout;
        else if (SystemUtils.IS_OS_FREE_BSD ||
                SystemUtils.IS_OS_LINUX ||
                SystemUtils.IS_OS_MAC ||
                SystemUtils.IS_OS_MAC_OSX ||
                SystemUtils.IS_OS_NET_BSD ||
                SystemUtils.IS_OS_OPEN_BSD)
            shutdownCommand = "shutdown -h " + timeout;
        else if (SystemUtils.IS_OS_HP_UX)
            shutdownCommand = "shutdown -hy " + timeout;
        else if (SystemUtils.IS_OS_IRIX)
            shutdownCommand = "shutdown -y -g " + timeout;
        else if (SystemUtils.IS_OS_SOLARIS ||
                SystemUtils.IS_OS_SUN_OS)
            shutdownCommand = "shutdown -y -i5 -g" + timeout;
        else if (SystemUtils.IS_OS_WINDOWS)
            shutdownCommand = "shutdown -s -t " + timeout;


        System.out.println(shutdownCommand);

    }


}