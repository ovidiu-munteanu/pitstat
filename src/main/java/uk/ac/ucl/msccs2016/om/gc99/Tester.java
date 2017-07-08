package uk.ac.ucl.msccs2016.om.gc99;


import java.util.Arrays;

public class Tester {

    public static void main(String[] args) throws Exception {

        Tester tester = new Tester();

        tester.masterMethod();

    }


    private void masterMethod() {

        int[] test = new int[4];

        System.out.println("Before:");
        System.out.println(Arrays.toString(test));

        testMethod(test);


        System.out.println();
        System.out.println("After:");

        System.out.println(Arrays.toString(test));



    }


    private void testMethod(int[] test) {

        test[0] = 99;
        test[2] = 98;


    }



}