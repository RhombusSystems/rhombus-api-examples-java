package com.rhombus.api.examples;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rhombus.ApiClient;
import com.rhombus.sdk.CameraWebserviceApi;
import com.rhombus.sdk.domain.*;
import org.apache.commons.cli.*;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.ws.rs.client.ClientBuilder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import java.sql.Timestamp;

//This script runs a quick report on the status of all cameras in an organization, report includes name,uuid,status and details if there are anypublic
class CheckCameraStatus
{
    private static ApiClient _apiClient;
    private static CameraWebserviceApi _cameraWebService;
    private static Timestamp _currentTime;

    public static void main(String[] args) throws Exception
    {
        _currentTime = new Timestamp(System.currentTimeMillis());

        final Options options = new Options();
        options.addRequiredOption("a", "apikey", true, "API Key");

        final CommandLine commandLine;
        try
        {
            commandLine = new DefaultParser().parse(options, args);
        }
        catch (ParseException e)
        {
            System.err.println(e.getMessage());

            new HelpFormatter().printHelp(
                    "java -cp rhombus-api-examples-all.jar com.rhombus.api.examples.CheckCameraStatus",
                    options);
            return;
        }

        String apikey = commandLine.getOptionValue("apikey");

        _initialize(apikey);
        printCameraStatus(getCameraData());
    }

    //Initializes the API Client and Webservices
    private static void _initialize(final String apiKey)
    {
        {
            final ClientBuilder clientBuilder = ClientBuilder.newBuilder();
            clientBuilder.register(new JacksonJaxbJsonProvider().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));;

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

            _cameraWebService = new CameraWebserviceApi(_apiClient);
        }
    }

    private static CameraGetMinimalCameraStateListWSResponse getCameraData() throws Exception
    {
        final CameraGetMinimalCameraStateListWSRequest cameraRequest = new CameraGetMinimalCameraStateListWSRequest();
        final CameraGetMinimalCameraStateListWSResponse cameraResponse = _cameraWebService.getMinimalCameraStateList(cameraRequest);
        return cameraResponse;
    }

    private static CameraGetUptimeWindowsWSResponse getCameraUptime(String uuid) throws Exception
    {
        final CameraGetUptimeWindowsWSRequest uptimeWindowRequest = new CameraGetUptimeWindowsWSRequest();//Create request for camera/getUptimeWindows
        uptimeWindowRequest.setCameraUuid(uuid);//Populate required fields for the request
        uptimeWindowRequest.setStartTime(1420113600L);
        uptimeWindowRequest.setEndTime(_currentTime.getTime());
        final CameraGetUptimeWindowsWSResponse cameraResponse = _cameraWebService.getUptimeWindows(uptimeWindowRequest);//Send request and receive response
        return cameraResponse;//Return data received from Rhombus Systems API
    }

    //Method Prints Status of each camera in an organization as returned by camera/getMinimalCameraStateList
    //Method also reports downtime information for cameras with "RED" health, this data is parsed from camera/getUptimeWindows
    //"Camera Name (UUID): Status - Status Details"
    //"     Offline Since: mm/dd/yyyy"
    //"     Offline for: ____ hours."
    private static void printCameraStatus(CameraGetMinimalCameraStateListWSResponse CameraData)
    {
        System.out.println("Camera Name (Camera UUID): Camera Status - Status Details");
        try {
            //For each camera in the organization
            for (MinimalDeviceStateType state : CameraData.getCameraStates()) {
                String uuid = state.getUuid();
                String name = state.getName();
                String status = state.getHealthStatus().toString();
                String statusDetails = state.getHealthStatusDetails().toString();
                if (statusDetails.equals("NONE")) {
                    statusDetails = "";
                } else {
                    statusDetails = " - " + statusDetails;
                }
                System.out.println(name + " (" + uuid + "): " + status + statusDetails);
                //If the camera is unhealthy
                if (status.equals("RED")) {
                    try {
                        CameraGetUptimeWindowsWSResponse UptimeData = getCameraUptime(uuid);//Get Uptime windows
                        List<TimeWindowSeconds> uptimeWindows = UptimeData.getUptimeWindows();//Get List of Uptime windows
                        TimeWindowSeconds lastWindow = uptimeWindows.get(uptimeWindows.size() - 1);//Get most recent window on list
                        long lastTimeStamp = lastWindow.getDurationSeconds() + lastWindow.getStartSeconds();

                        //Convert lastTimeStamp into a human readable Date, and print
                        SimpleDateFormat sf = new SimpleDateFormat("mm/dd/yyyy");
                        Date date = new Date(lastTimeStamp * 1000);
                        System.out.println("\tOffline Since: " + sf.format(date));
                        //Convert elapsed seconds to elapsed hours, and print
                        System.out.println("\tOffline for " + (_currentTime.getTime() / 1000 - lastTimeStamp) / 60 / 60 + " hours.");
                    } catch (Exception e) {//Catches any exceptions and provides debug information, most likely source of exception would be the getCameraUptime(uuid) call
                        System.out.println("\tFailed to Receive Uptime Information - " + e);
                    }
                }
            }
        }
        catch (Exception e){
            System.out.println("Failed to Receive Camera State List - " + e);
        }
    }
}
