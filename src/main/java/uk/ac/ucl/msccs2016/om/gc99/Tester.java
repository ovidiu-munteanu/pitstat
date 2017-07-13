package uk.ac.ucl.msccs2016.om.gc99;


import static uk.ac.ucl.msccs2016.om.gc99.Worker.ANSI_RED;
import static uk.ac.ucl.msccs2016.om.gc99.Worker.ANSI_RESET;

public class Tester {

    public static void main(String[] args) throws Exception {

        String test = ANSI_RED + "Some Red Text" + ANSI_RESET;

        test = test.replaceAll("(\\\u001B\\[0m)|(\\\u001B\\[31m)|(\\\u001B\\[32m)", " ");

        System.out.println(test);



    }


}