package com.rhombus.api.examples;

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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.*;

import com.rhombus.sdk.CameraWebserviceApi;
import com.rhombus.sdk.ClimateWebserviceApi;
import org.apache.commons.cli.Options;

public class Climate {
    private static ApiClient _apiClient;
    private static ClimateWebserviceApi _climateWebservice;
    private static CameraWebserviceApi _cameraWebservice;

    public static void main(String[] args) throws Exception {
        final Options options = new Options();
        // command line arguments for the user
        options.addRequiredOption("a", "apiKey", true, "API key");
        options.addRequiredOption("n", "sensorName", true, "Name of the environmental sensor");
        options.addRequiredOption("o", "option", true, "Whether the program should look for past or present events");
        options.addOption("t", "time", true, "yyyy-mm-dd 00:00:00");
        options.addOption("tr", "tempRate", true, "The limit for which the ratechange of temperature is OK");
        options.addOption("hr", "humidRate", true, "The limit for which the ratechange of humidity is OK");
        options.addOption("c", "cameraName", true, "Name of the camera that is associated with the sensor");

        // specifies what is displayed if the help command given
        final CommandLine commandLine;
        try {
            commandLine = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.err.println(e.getMessage());

            new HelpFormatter().printHelp(
                    "java -cp rhombus-api-examples-all.jar com.rhombus.api.examples.Climate",
                    options);
            return;
        }

        String apiKey = commandLine.getOptionValue("apiKey");
        String sensorName = commandLine.getOptionValue("sensorName");
        String time = commandLine.getOptionValue("time");

        String cameraName;
        String cameraUuid = null;
        String option;

        float tempRate;
        float humidRate;

        Long timeMilli;
        ;

        // verifying movement filer input
        if (commandLine.hasOption("option")) { // will always be true
            option = commandLine.getOptionValue("option");
            // if command line input is invalid
            if (!option.equals("Past") && !option.equals("Present")) {
                System.out.println("-o [Past, Present]");
                System.exit(0); //
            }
        } else { // if there is no command line input for movement
            option = " ";
        }

        // verifying time input
        if (commandLine.hasOption("time")) {
            // converting string startTime argument to milliseconds
            DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
            dateFormat.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
            timeMilli = dateFormat.parse(time).getTime();
        } else {
            // if no command line argument, startTime defaults to 2 hours ago (milliseconds)
            timeMilli = ZonedDateTime.now().toInstant().toEpochMilli() - (1000 * 60 * 60 * 2);
        }

        // verifying camera name input
        if (commandLine.hasOption("cameraName")) {
            // converting the camera name to uuid if given in command line
            cameraName = commandLine.getOptionValue("cameraName");
            cameraUuid = cameraUuidConvert(cameraName);
        } else {
            final ClimateGetMinimalClimateStatesWSResponse sensorData = sensorData();
            final List<ClimateMinimalClimateStateType> list = sensorData.getClimateStates();

            for (ClimateMinimalClimateStateType event : list) {
                List<String> assoc_cameras = event.getAssociatedCameras();
                if (assoc_cameras.size() > 0) {
                    cameraUuid = assoc_cameras.get(0);
                } else {
                    System.out.println("There are no cameras associated with this sensor");
                    return;
                }
            }
        }

        if (commandLine.hasOption("tempRate")) {
            String tempRatestring = commandLine.getOptionValue("tempRate");
            tempRate = Float.parseFloat(tempRatestring);
        } else {
            tempRate = (float) 0.15;
        }

        if (commandLine.hasOption("humidRate")) {
            String humidRateString = commandLine.getOptionValue("humidRate");
            humidRate = Float.parseFloat(humidRateString);
        } else {
            humidRate = (float) 0.15;
        }

        _initialize(apiKey);
        String sensorUuid = sensorUuidConvert(sensorName);
        seekpointSearch(option, timeMilli, sensorUuid, tempRate, humidRate, cameraUuid);
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

            _climateWebservice = new ClimateWebserviceApi(_apiClient);
            _cameraWebservice = new CameraWebserviceApi(_apiClient);
        }
    }

    // method converts Camera name to uuid
    public static String cameraUuidConvert(String cameraName) throws Exception {
        final CameraGetMinimalCameraStateListWSRequest cameraRequest = new CameraGetMinimalCameraStateListWSRequest();
        final CameraGetMinimalCameraStateListWSResponse cameraResponse = _cameraWebservice.getMinimalCameraStateList(cameraRequest);
        final List<MinimalDeviceStateType> list = cameraResponse.getCameraStates();

        // running through the data
        for (MinimalDeviceStateType event : list) {
            String name = event.getName();
            String uuid = event.getUuid();
            if (cameraName.equals(name)) {
                return uuid;  // converting the sensor name to uuid
            }
        }
        // this invalid return value allows for input validation
        return " ";
    }

    // method returns data for climate events
    private static ClimateGetClimateEventsForSensorWSResponse climateCall(Long time_begin, Long time_end, String
            sensorUuid) throws Exception {
        final ClimateGetClimateEventsForSensorWSRequest climateRequest = new ClimateGetClimateEventsForSensorWSRequest();
        climateRequest.setCreatedAfterMs(time_begin);
        climateRequest.setCreatedBeforeMs(time_end);
        climateRequest.setSensorUuid(sensorUuid);
        final ClimateGetClimateEventsForSensorWSResponse climateResponse = _climateWebservice.getClimateEventsForSensor(climateRequest);
        return climateResponse;
    }

    // method returns data for state of environmental sensors
    private static ClimateGetMinimalClimateStatesWSResponse sensorData() throws Exception {
        final ClimateGetMinimalClimateStatesWSRequest minClimateRequest = new ClimateGetMinimalClimateStatesWSRequest();
        final ClimateGetMinimalClimateStatesWSResponse minClimateResponse = _climateWebservice.getMinimalClimateStateList(minClimateRequest);
        return minClimateResponse;
    }

    private static String sensorUuidConvert(String sensorName) throws Exception {
        final ClimateGetMinimalClimateStatesWSRequest minClimateRequest = new ClimateGetMinimalClimateStatesWSRequest();
        final ClimateGetMinimalClimateStatesWSResponse minClimateResponse = _climateWebservice.getMinimalClimateStateList(minClimateRequest);
        final List<ClimateMinimalClimateStateType> list = minClimateResponse.getClimateStates();
        for (ClimateMinimalClimateStateType cam : list) {
            String name = cam.getName();
            String uuid = cam.getSensorUuid();
            if (sensorName.equals(name)) {
                return uuid;
            }
        }
        return " ";
    }

    // method converts celsius to fahrenheit
    private static float celsiusConvert(float celsius) throws Exception {
        float fahrenheit = (float) ((celsius * 1.8) + 32);
        return fahrenheit;
    }

    // method creates seekpoint in the console
    private static CameraCreateFootageSeekpointsWSResponse createSeekpoint(String cameraUuid, Long millitime) throws Exception {
        FootageSeekPointV2Type footageSeekPointV2Type = new FootageSeekPointV2Type();
        final CameraCreateFootageSeekpointsWSRequest seekpointRequest = new CameraCreateFootageSeekpointsWSRequest();
        seekpointRequest.setCameraUuid(cameraUuid);
        footageSeekPointV2Type.setA(FootageSeekPointV2Type.AEnum.UNKNOWN);
        footageSeekPointV2Type.setTs(millitime);
        seekpointRequest.setFootageSeekPoint(footageSeekPointV2Type);
        final CameraCreateFootageSeekpointsWSResponse seekpointResponse = _cameraWebservice.createFootageSeekpoints(seekpointRequest);
        return seekpointResponse;
    }

    private static void seekpointSearch(String option, long timeMilli, String sensorUuid, float tempRate, float humidRate, String cameraUuid) throws Exception {
        float tempRateMin = -10;  // to verify that there was an event
        float humidRateMin = -10; // to verify that there was an event

        if (option.equals("Present")) {
            long time = timeMilli;
            // start time of data collection is 15 minutes before "time"
            long startTime = timeMilli - (15 * 60 * 1000);
            // end time of data collection is 15 minutes after "time"
            long endTime = timeMilli + (15 * 60 * 1000);

            ClimateGetClimateEventsForSensorWSResponse climateData = climateCall(startTime, endTime, sensorUuid);
            final List<ClimateEventType> list = climateData.getClimateEvents();
            for (int i = 0; i < list.size(); i++) {
                ClimateEventType earlierTime = list.get(i);
                // in milliseconds, an earlier time would be a smaller number than a later time
                Long timeMS = earlierTime.getTimestampMs(); // time in milliseconds of each event
                if (timeMS < time) {
                    ClimateEventType laterTime = list.get(i - 2);
                    // later time is the closest time that is later than timeMS
                    long timeDifMS = laterTime.getTimestampMs() - earlierTime.getTimestampMs(); // time difference in milliseconds
                    long timeDifMin = timeDifMS / 1000 / 60; // ms time difference is converted to minutes
                    float tempDif = celsiusConvert(laterTime.getTemp()) - celsiusConvert(laterTime.getTemp());
                    // temperature difference between the two times is calculated in Fahrenheit
                    float humidDif = laterTime.getHumidity() - earlierTime.getHumidity();
                    // humidity difference between the two times is calculated.
                    tempRateMin = tempDif / timeDifMin; // change in temperature per minute
                    humidRateMin = humidDif / timeDifMin; // change in humidity per minute
                    // get the event that was two back
                }
            }

            // tempRateMin and humidRateMin would remain the same if there were no data generated
            if (tempRateMin == -10 || humidRateMin == -10) {
                System.out.println("There are no data for that date");
            }

            System.out.println("Temperature rate of change: " + tempRateMin);
            System.out.println("Humidity rate of change: " + humidRateMin);

            // if rate at which temperature is changing exceeds threshold
            if (tempRateMin > tempRate || tempRateMin < -tempRate) {
                createSeekpoint(cameraUuid, time);
                System.out.println("Temperature rate of change exceeded threshold, seekpoint created");
            } // if rate at which humidity is changin exceeds threshold
            else if (humidRateMin > humidRate || humidRateMin < -humidRate) {
                createSeekpoint(cameraUuid, time);
                System.out.println("Humidity rate of change exceeded threshold, seekpoint created");
            } else {
                System.out.println("No seekpoints created");
            }
        } else { // if the user chooses the option of "Present"
            boolean running = true;

            List<Float> tempList = null; // list of temperatures in Fahrenheit
            List<Float> humidList = null; // list of humidities
            List<Long> timeList = null; // list of times in milliseconds

            while (running == true) {
                System.out.println("Processing ... ");
                // end time of data collection is always the current time
                long endTime = ZonedDateTime.now().toInstant().toEpochMilli();
                // start time of data collection is always 210 seconds before current time
                long startTime = endTime - (210 * 1000);

                ClimateGetClimateEventsForSensorWSResponse climateData = climateCall(startTime, endTime, sensorUuid);
                final List<ClimateEventType> list = climateData.getClimateEvents();
                for (ClimateEventType event : list) {
                    if (!tempList.contains(celsiusConvert(event.getTemp()))) {
                        tempList.add(celsiusConvert(event.getTemp())); // append Fahrenheit to tempList
                        humidList.add(event.getHumidity()); // append humidity to humidList
                        timeList.add(event.getTimestampMs()); // append milliseconds to timeList
                    }
                }
                int count = 0;

                if (tempList.size() > 1) { // if there are multiple events
                    // running through the events
                    while (count < tempList.size() - 1) {
                        // calculate the time difference between events in milliseconds
                        long timeDifMS = timeList.get(count + 1) - timeList.get(count);
                        // convert time difference to minutes
                        float timeDifMin = timeDifMS / 1000 / 60;

                        // calculate difference in temperature (Fahrenheit)
                        float tempDif = celsiusConvert(tempList.get(count + 1)) - celsiusConvert(tempList.get(count));
                        float humidDif = humidList.get(count + 1) - humidList.get(count); // calculate difference in humidity

                        tempRateMin = tempDif / timeDifMin; // rate at which temperature is changing
                        System.out.println("Temp rate: " + tempRateMin);

                        humidRateMin = humidDif / timeDifMin; // rate at which humidity is changing
                        System.out.println("Humid rate: " + humidRateMin);

                        // if rate at which temperature is changing exceeds threshold
                        if (tempRateMin > tempRate || tempRateMin < -tempRate) {
                            createSeekpoint(cameraUuid, endTime);
                            System.out.println("Temperature rate of change exceeds threshold, seekpoint created.");
                        } else {
                            System.out.println("Temperature rate of change: " + tempRateMin);
                            System.out.println("Temperature rate of change does not exceed threshold, no seekpoint created.");
                        }

                        // if rate at which humidity is changing exceeds threshold
                        if (humidRateMin > humidRate || humidRateMin < -humidRate) {
                            createSeekpoint(cameraUuid, endTime);
                            System.out.println("Humidity rate of change exceeds threshold, seekpoint created.");
                        } else {
                            System.out.println("Humidity rate of change: " + humidList);
                            System.out.println("Humidity rate of change does not exceed threshold, no seekpoint created.");
                        }

                        count++;
                    }
                }
                Thread.sleep(6000); // sleep for one minute between each data collection.
            }
        }
    }
}