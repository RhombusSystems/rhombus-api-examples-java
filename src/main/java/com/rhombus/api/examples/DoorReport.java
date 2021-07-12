package com.rhombus.api.examples;

import com.rhombus.sdk.LocationWebserviceApi;
import com.rhombus.sdk.DoorWebserviceApi;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rhombus.ApiClient;
import com.rhombus.sdk.domain.*;
import org.apache.commons.cli.*;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.ws.rs.client.ClientBuilder;
import java.io.File;
import java.io.FileWriter;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DoorReport {
    private static ApiClient _apiClient;
    private static DoorWebserviceApi _doorWebservice;
    private static LocationWebserviceApi _locationWebservice;

    public static void main(String[] args) throws Exception {
        final Options options = new Options();
        // command line arguments for the user
        options.addRequiredOption("a", "apikey", true, "API key");
        options.addRequiredOption("n", "sensorName", true, "Name of the sensor");
        options.addOption("s", "startTime", true, "Start time of data collection yyyy-mm-dd 00:00:00");
        options.addOption("e", "endTime", true, "End time of data collection yyyy-mm-dd 00:00:00");
        options.addOption("c", "csv", true, "Path to the csv and name the csv");

        // specifies what is displayed if help command given
        final CommandLine commandLine;
        try {
            commandLine = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.err.println(e.getMessage());

            new HelpFormatter().printHelp(
                    "java -cp rhombus-api-examples-all.jar com.rhombus.api.examples.DoorReport",
                    options);
            return;
        }

        String apiKey = commandLine.getOptionValue("apikey");
        String sensorName = commandLine.getOptionValue("sensorName");
        String startTime = commandLine.getOptionValue("startTime");
        String endTime = commandLine.getOptionValue("endTime");

        String csv;
        String real_csv;
        String path;
        long startTimeMilli;
        long endTimeMilli;

        // verifying the CSV input
        if (commandLine.hasOption("csv")) {
            csv = commandLine.getOptionValue("csv");
            if (csv.contains(".csv")){
                real_csv = csv;
            }
            else {
                real_csv = csv + ".csv";
            }
        } else {
            path = System.getProperty("user.dir");
            real_csv = path + "/doors.csv";
        }

        if (commandLine.hasOption("startTime")) {
            // converting string startTime argument to milliseconds
            DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
            dateFormat.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
            startTimeMilli = dateFormat.parse(startTime).getTime();
        }
        else {
            // if no command line argument, startTime defaults to 24 hours ago (milliseconds)
            startTimeMilli = ZonedDateTime.now().toInstant().toEpochMilli() - (1000 * 60 * 60 * 24);
        }
        if (commandLine.hasOption("endTime")) {
            // converting string endTime argument to milliseconds
            DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
            dateFormat.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
            endTimeMilli = dateFormat.parse(endTime).getTime();
        }
        else {
            // if no command line argument, endTime defaults to now (milliseconds)
            endTimeMilli = ZonedDateTime.now().toInstant().toEpochMilli();
        }

        _initialize(apiKey);
        String sensorUuid = uuidConvert(sensorName);
        // input validation for the sensor name
        if (sensorUuid.equals(" "))
        {
            System.out.println("Invalid sensor name");
            System.exit(0);
        }
        File csvFile = CSVCreate(real_csv);   // creating the CSV file
        doorEvents(sensorUuid, sensorName, startTimeMilli, endTimeMilli, csvFile);  // writing to the CSV file
    }

    private static void _initialize(String apiKey) throws Exception {
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

            _doorWebservice = new DoorWebserviceApi(_apiClient);
            _locationWebservice = new LocationWebserviceApi(_apiClient);
        }
    }

    // method converts sensor name to uuid
    public static String uuidConvert(String sensorName) throws Exception {
        final DoorGetMinimalDoorStatesWSRequest dataRequest = new DoorGetMinimalDoorStatesWSRequest();
        final DoorGetMinimalDoorStatesWSResponse dataResponse = _doorWebservice.getMinimalDoorStateList(dataRequest);
        final List<DoorMinimalDoorStateType> list = dataResponse.getDoorStates();
        // getting data from "Minimal Door State List" API

        for (DoorMinimalDoorStateType event : list) {
            String name = event.getName();
            String uuid = event.getSensorUuid();
            if (sensorName.equals(name)) {
                return uuid;
                // converting the sensor name to uuid
            }
        }
        // this invalid return value allows for input validation
        return " ";
    }

    // creates CSV file
    private static File CSVCreate(String csv) {
        File csvFile = new File(csv);
        return csvFile;
    }

    public static void doorEvents(String sensorUuid, String sensorName, long startTimeMilli, long endTimeMilli, File csvFile) throws Exception {
        // gathering the data on the door events
        final DoorGetDoorEventsForSensorWSRequest doorRequest = new DoorGetDoorEventsForSensorWSRequest();
        // setting the parameters for the API call
        doorRequest.setCreatedAfterMs(startTimeMilli);
        doorRequest.setCreatedBeforeMs(endTimeMilli);
        doorRequest.setSensorUuid(sensorUuid);
        final DoorGetDoorEventsForSensorWSResponse doorResponse = _doorWebservice.getDoorEventsForSensor(doorRequest);

        final List<DoorEventType> list = doorResponse.getDoorEvents();
        // creating a list of the all the door events

        int count = 0;
        // count for events that arent' redundant

        try (CSVPrinter printer = new CSVPrinter(new FileWriter(csvFile), CSVFormat.EXCEL)) {
            for (DoorEventType cam : list)
            {
                Boolean change = cam.isStateChanged();
                Long milliseconds = cam.getTimestampMs();
                Timestamp timestamp = new Timestamp(milliseconds);
                String s = timestamp.toString().split("\\.")[0];

                // An event is created every five minutes AND whenever the state of the door is changed
                // however, an event is only relevant when the state has changed
                if (change.equals(true)) {
                    count++;
                    printer.printRecord(sensorName, cam.getLocationUuid(), cam.getState(), s, count);
                    // writing data to the CSV file
                }
            }
        }
        // in case the file was not able to be opened
        catch (IOException ex)
        {
            System.out.println("Couldn't open file");
            System.exit(0);
        }
    }
}