package uk.ac.ucl.msccs2016.om.gc99;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Tester {

    public static void main (String[] args) {

        LocalDateTime currentTime = LocalDateTime.now();

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

        System.out.println(currentTime.format(dateTimeFormatter));



    }
}
