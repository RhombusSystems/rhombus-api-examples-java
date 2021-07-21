package com.rhombus.api.examples;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class time {
    public static void main(String args[])
    {
        // remember to add "L" to the end of long value
        long milliSec = 1623178375437L;

        // Create data format
        DateFormat simple = new SimpleDateFormat("dd MMM yyyy HH:mm:ss");

        // using Date() constructor to create a date from milliseconds
        Date result = new Date(milliSec);

        // Formatting the data
        System.out.println(simple.format(result));
    }
}
