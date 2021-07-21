package com.rhombus.api.examples;

import com.rhombus.ApiClient;

public class method {
    // this has to be defined before the "public static void main thing"
    static int Add (int number) {
        int new_number = number + 5;
        return new_number;
    }
    public static void main(String[] args) {
        int the_num = Add(10);
        System.out.println(the_num);
    }
}
//
//    long millis=System.currentTimeMillis();
//    java.util.Date date=new java.util.Date(millis);
//    System.out.println(date);
//    these three lines will print out the current time
