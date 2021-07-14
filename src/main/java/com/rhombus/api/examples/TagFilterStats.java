package com.rhombus.api.examples;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rhombus.ApiClient;
import com.rhombus.sdk.ProximityWebserviceApi;
import com.rhombus.sdk.domain.*;
import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.ws.rs.client.ClientBuilder;
import java.io.*;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Date;
import java.io.FileWriter;
import java.io.IOException;

public class TagFilterStats {
    private static ApiClient _apiClient;
    private static ProximityWebserviceApi _proximityWebservice;

    public static void main(String[] args) throws Exception {
        final Options options = new Options();
        // command line arguments for user
        options.addRequiredOption("a", "apiKey", true, "API Key");
        options.addRequiredOption("n", "tagName", true, "Name of the tag");
        options.addRequiredOption("t", "tagStats", true, "Receive Statistics (True / False)");
        options.addOption("s", "startTime", true, "Start time of data collection yyyy-mm-dd 00:00:00");
        options.addOption("e", "endTime", true, "End time of data collection yyyy-mm-dd 00:00:00");
        options.addOption("m", "movement", true, "Filter by the type of movement");
        options.addOption( "csv", true, "Name of the CSV file");
        options.addOption("text", true, "Name of the text file");
//        options.addOption("folder", true, "Name of folder for text and CSV files");

        // specifies what is displayed if help command is given
        final CommandLine commandLine;
        try {
            commandLine = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.err.println(e.getMessage());

            new HelpFormatter().printHelp(
                    "java -cp rhombus-api-examples-all.jar com.rhombus.api.examples.TagFilterStats",
                    options);
            return;
        }

        String apiKey = commandLine.getOptionValue("apiKey");
        String tagName = commandLine.getOptionValue("tagName");
        String startTime = commandLine.getOptionValue("startTime");
        String endTime = commandLine.getOptionValue("endTime");
        String stats = commandLine.getOptionValue("tagStats");

        String csv;
        String real_csv;
        String text;
        String real_text;
        String movement;

        long startTimeMilli;
        long endTimeMilli;

        String path = System.getProperty("user.dir"); // path for CSV and text files

        // verifying CSV file name input
        if (commandLine.hasOption("csv")) {
            csv = commandLine.getOptionValue("csv");
            if (csv.contains(".csv")) { // checking for ".csv" in CSV file name
                real_csv = csv;
            } else {
                real_csv = csv + ".csv"; // adding ".csv" if not present in CSV file name
            }
        } else {
            real_csv = path + "/tags.csv";
        }

        // verifying text file name input
        if (commandLine.hasOption("text")) {
            text = commandLine.getOptionValue("text");
            if (text.contains(".txt")) { // checking for ".txt" in text file name
                real_text = text;
            } else {
                real_text = text + ".txt"; // adding ".txt" if not present in text file name
            }
        } else {
            real_text = path + "/tags.txt";
        }

        // verifying movement filer input
        if (commandLine.hasOption("movement")) {
            movement = commandLine.getOptionValue("movement");
            // if command line input is invalid
            if (!movement.equals("ARRIVAL") && !movement.equals("DEPARTURE") && !movement.equals("MOVED_SIGNIFICANTLY") && !movement.equals("UNKNOWN")) {
                System.out.println("-m [ARRIVAL, DEPARTURE, MOVED_SIGNIFICANTLY, UNKNOWN]"); // these are the options for the movement filter
                System.exit(0); //
            }
        } else { // if there is no command line input for movement
            movement = " ";
        }

        if (commandLine.hasOption("startTime")) {
            // converting string startTime argument to milliseconds
            DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
            dateFormat.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
            startTimeMilli = dateFormat.parse(startTime).getTime();
        } else {
            // if no command line argument, startTime defaults to 240 hours ago (milliseconds)
            startTimeMilli = ZonedDateTime.now().toInstant().toEpochMilli() - (1000 * 60 * 60 * 240);
        }
        if (commandLine.hasOption("endTime")) {
            // converting string endTime argument to milliseconds
            DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
            dateFormat.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
            endTimeMilli = dateFormat.parse(endTime).getTime();
        } else {
            // if no command line argument, endTime defaults to now (milliseconds)
            endTimeMilli = ZonedDateTime.now().toInstant().toEpochMilli();
        }

        _initialize(apiKey);

        String tagUuid = uuidConvert(tagName);
        // input validation for the tag name
        if (tagUuid.equals(" ")) {
            System.out.println("Invalid tag name");
            System.exit(0);
        }

        File csvFile = CSVCreate(real_csv);   // creating the CSV file
        FileCreate(tagUuid, tagName, csvFile, startTimeMilli, endTimeMilli, stats, movement, real_text);
        // writing to the CSV file (and the text file, if option chosen)
    }

    public static void _initialize(String apiKey) {
        /*
         * API CLIENT
         */
        {
            // creating headers for API calls
            final List<Header> defaultHeaders = new ArrayList<>();
            defaultHeaders.add(new BasicHeader("x-auth-scheme", "api-token"));
            defaultHeaders.add(new BasicHeader("x-auth-apikey", apiKey));

            final ClientBuilder clientBuilder = ClientBuilder.newBuilder();
            clientBuilder.register(new JacksonJaxbJsonProvider().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));

            final HostnameVerifier hostnameVerifier = new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };
            clientBuilder.hostnameVerifier(hostnameVerifier);

            _apiClient = new ApiClient();
            _apiClient.setHttpClient(clientBuilder.build());
            _apiClient.addDefaultHeader("x-auth-scheme", "api-token");
            _apiClient.addDefaultHeader("x-auth-apikey", apiKey);

            _proximityWebservice = new ProximityWebserviceApi(_apiClient);
        }
    }

    // creates CSV file
    private static File CSVCreate(String csv) {
        File csvFile = new File(csv);
        return csvFile;
    }

    // method gets seconds from HH:MM:SS format
    public static int getSec(String time_str) throws Exception {
        String h = time_str.split(":")[0]; // number of hours
        String m = time_str.split(":")[1]; // number of minutes
        String s = time_str.split(":")[2]; // number of seconds
        return Integer.parseInt(h) * 3600 + Integer.parseInt(m) * 60 + Integer.parseInt(s); // convert to seconds
    }

    // method converts Tag name to uuid
    public static String uuidConvert(String tagName) throws Exception {
        final ProximityGetMinimalProximityStatesWSRequest tagDataRequest = new ProximityGetMinimalProximityStatesWSRequest();
        final ProximityGetMinimalProximityStatesWSResponse tagDataResponse = _proximityWebservice.getMinimalProximityStateList(tagDataRequest);
        final List<ProximityMinimalProximityStateType> list = tagDataResponse.getProximityStates();
        // getting data from "Minimal Door State List" API

        // running through the data
        for (ProximityMinimalProximityStateType event : list) {
            String name = event.getName();
            String uuid = event.getTagUuid();
            if (tagName.equals(name)) {
                return uuid;  // converting the sensor name to uuid
            }
        }
        // this invalid return value allows for input validation
        return " ";
    }

    // method calculates the average seconds from a list of HH:MM:SS timestamps
    public static int avgCalc(List<String> some_list, int count) throws Exception {
        int total_seconds = 0;
        int avg_seconds = 0;
        for (int i = 0; i < some_list.size(); i++) {
            String timestamp = some_list.get(i); // establish timestamp
            int ms_time = getSec(timestamp);  // convert timestamp to milliseconds
            total_seconds += ms_time;  // add ms to total seconds
            i++;
            count++;
        }
        if (count == 0) {  // count would be equal to 0 if there were no events
            System.out.print("There are no arrival or departure times during this period");
        } else {
            avg_seconds = total_seconds / count;
        }
        return avg_seconds;
    }

    // method converts milliseconds to HH:MM:SS
    private static String getTime(long initial_seconds) throws Exception {
        float hours = ((float) initial_seconds) / 60 / 60; // converts total seconds to hours (float)
        int real_hours = (int) hours;  // rounds float to get integer number of hours (real hours)
        float minutes = (hours - real_hours) * 60; // converts partial hour to minutes (float)
        int real_minutes = (int) minutes; // rounds float to get integer number of minutes (real minutes)
        float seconds = (minutes - (int) minutes) * 60; // converts partial minute into seconds
        int real_seconds = (int) seconds; // round float to get integer number of seconds (real seconds)
        String time = (String) (String.valueOf(real_hours) + ":" + String.valueOf(real_minutes) + ":" + String.valueOf(real_seconds));
        // creates timestamp
        return time;
    }

    private static void FileCreate(String tagUuid, String tagName, File csvFile, Long startTimeMilli, Long endTimeMilli, String stats, String movement, String real_text) throws Exception {
        // collecting data for locomotion events
        final ProximityGetLocomotionEventsForTagWSRequest tagRequest = new ProximityGetLocomotionEventsForTagWSRequest();
        // setting the parameters for the API call
        tagRequest.setCreatedAfterMs(startTimeMilli);
        tagRequest.setCreatedBeforeMs(endTimeMilli);
        tagRequest.setTagUuid(tagUuid);
        final ProximityGetLocomotionEventsForTagWSResponse tagResponse = _proximityWebservice.getLocomotionEventsForTag(tagRequest);

        final List<ProximityTagLocomotionEventType> list = tagResponse.getLocomotionEvents();

        List<String> arrival_times = new ArrayList();
        List<String> departure_times = new ArrayList();
        List<String> duration_times = new ArrayList();
        List<Long> milli_list = new ArrayList();
        List time_list = new ArrayList();
        int j = 0;

        ProximityTagLocomotionEventType.MovementEnum arrival = ProximityTagLocomotionEventType.MovementEnum.ARRIVAL;
        ProximityTagLocomotionEventType.MovementEnum departure = ProximityTagLocomotionEventType.MovementEnum.DEPARTURE;

        // if the user wants to create a text file with the stats
        if (stats.equals("True")) {
            for (ProximityTagLocomotionEventType event : list) {
                if (event.getMovement().equals(arrival)) {
                    Long time = event.getTimestampMs();  // millisecond time
                    Timestamp arrival_time = new Timestamp(time); // create an entire timestamp (this includes year, month, day)
                    String arrival_timestamp = arrival_time.toString().split("\\.")[0]; // eliminating milliseconds from
                    String new_time = arrival_timestamp.split(" ")[1]; // using only time (not date) part of timestamp
                    arrival_times.add(new_time); // add HH:MM:SS time to list of arrival times
                    milli_list.add(time); // add ms time to list of ms times
                }
                if (event.getMovement().equals(departure)) {
                    Long time = event.getTimestampMs();  // millisecond time
                    Timestamp departure_time = new Timestamp(time); // create an entire timestamp (this includes year, month, day)
                    String departure_timestamp = departure_time.toString().split("\\.")[0]; // eliminating milliseconds from
                    String new_time = departure_timestamp.split(" ")[1]; // using only time (not date) part of timestamp
                    departure_times.add(new_time); // add HH:MM:SS time to list of arrival times
                    milli_list.add(time); // add ms time to list of ms times
                }
            }

            for (int i = 0; i < milli_list.size(); i++) {
                Long milliSec = milli_list.get(i);
                Date date = new Date(milliSec); // converting ms time to date
                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles"));
                cal.setTime(date);
                int day = cal.get(Calendar.DAY_OF_MONTH); // day is the number of the day of the month
                time_list.add(day);  // adding that number to a list of days
            }

            while (j < time_list.size() - 1) {  // running through list of day numbers
                // arrivals and departures will be next to each other in the data
                if (time_list.get(j).equals(time_list.get(j + 1))) {  // if two times are from the same day
                    // difference in seconds from start time to end time
                    long duration_secs = (milli_list.get(j) - milli_list.get(j + 1)) / 1000;
                    // converting seconds to timestamp
                    String duration_time = getTime(duration_secs);
                    duration_times.add(duration_time); // appending timestamp
                    j++;
                } else {
                    j++;
                }
            }
            // sorting times in the lists (earliest time to latest time)
            Collections.sort(arrival_times);
            Collections.sort(departure_times);
            Collections.sort(duration_times);

            int total_seconds = 0;

            // creating three separate counts
            int count_1 = 0;
            int count_2 = 0;
            int count_3 = 0;

            // average seconds for arrivals, departures, and durations
            long arrive_avg_seconds = avgCalc(arrival_times, count_1);
            long depart_avg_seconds = avgCalc(departure_times, count_2);
            long duration_avg_seconds = avgCalc(duration_times, count_3);


            try {
                FileWriter writer = new FileWriter(real_text, true);

                writer.write("--Arrival Times--");
                writer.write("\r\nEarliest Arrival: " + arrival_times.get(0)); // first time in the list of arrivals
                writer.write("\r\nLatest Arrival: " + arrival_times.get(arrival_times.size() - 1)); // last time in the list of arrivals
                writer.write("\r\nAverage Arrival: " + getTime(arrive_avg_seconds)); // timestamp of average arrival time

                writer.write("\r\n --Departure Times--");
                writer.write("\r\nEarliest Departure: " + departure_times.get(0)); // first time in the list of departures
                writer.write("\r\nLatest Departure: " + departure_times.get(departure_times.size() - 1)); // last time in the list of departures
                writer.write("\r\nAverage Departure: " + getTime(depart_avg_seconds)); // timestamp of average departure time

                writer.write("\r\n--Duration Times--");
                writer.write("\r\nShortest Duration: " + duration_times.get(0)); // first (shortest) HH:MM:SS in the list of duration times
                writer.write("\r\nLongest Duration: " + duration_times.get(duration_times.size() - 1)); // last (longest) HH:MM:SS in the list of duration times
                writer.write("\r\nAverage Duration: " + getTime(duration_avg_seconds)); // timestamp of average duration time

                writer.close();
            } catch (IOException e) {
                System.out.println("There was an error in the program");
                e.printStackTrace();
            }
        }

        // the rest of the program runs regardless of the user's choice of stats

        // count for events that aren't redundant
        int count = 0;

        try (CSVPrinter printer = new CSVPrinter(new FileWriter(csvFile), CSVFormat.EXCEL)) {
            // set the headers for the CSV file
            printer.printRecord("Tag Name", "Location Uuid", "Movement Type", "Date", "Event Number");
            for (ProximityTagLocomotionEventType cam : list) {
                // if there is movement parameter and it does not match the movement type of the event (cam)
                if (!movement.equals(" ") && !cam.getMovement().equals(ProximityTagLocomotionEventType.MovementEnum.valueOf(movement))) {
                        continue; // keep running through the data
                } else {
                    // if there is no movement parameter or it matches the movement type of the event (cam)
                    Long milliseconds = cam.getTimestampMs(); // establishing milliseconds
                    Timestamp timestamp = new Timestamp(milliseconds); // creating timestamp
                    String s = timestamp.toString().split("\\.")[0]; // cutting milliseconds from timestamp
                    count++;
                    // write to the CSV file
                    printer.printRecord(tagName, cam.getLocationUuid(), cam.getMovement(), s, count);
                }
            }
        }
        // in case the file was not able to be opened
        catch (IOException ex) {
            System.out.println("Couldn't open file");
            System.exit(0);
        }
    }
}