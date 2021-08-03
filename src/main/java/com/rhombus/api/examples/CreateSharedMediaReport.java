package com.rhombus.api.examples;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rhombus.ApiClient;
import com.rhombus.sdk.CameraWebserviceApi;
import com.rhombus.sdk.EventWebserviceApi;
import com.rhombus.sdk.VideoWebserviceApi;
import com.rhombus.sdk.domain.*;
import org.apache.commons.cli.*;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.ws.rs.client.ClientBuilder;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.List;

import java.sql.Timestamp;

//This script runs a quick report on the status of all cameras in an organization, report includes name,uuid,status and details if there are any
//Also allows user to end sharing of shared timelapses, clips, and streams.
public class CreateSharedMediaReport
{
    private static ApiClient _apiClient;
    private static CameraWebserviceApi _cameraWebService;
    private static VideoWebserviceApi _videoWebService;
    private static EventWebserviceApi _eventWebService;

    public static void main(String[] args) throws Exception
    {
        final Options options = new Options();
        options.addRequiredOption("a", "apikey", true, "API Key");
        options.addOption("rc","removeSharedClip", true,"UUID of shared Clip to remove");
        options.addOption("rt","removeSharedTimelapse", true,"UUID of shared Timelapse to remove");
        options.addOption("rs","removeSharedStream", true,"comma separated, UUID of Camera and UUID of Stream");

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

        if(commandLine.hasOption("rc"))
        {
            try {
                removeSharedClip(commandLine.getOptionValue("rc"));
            }
            catch(com.rhombus.ApiException e)
            {
                System.out.println("Failed to Remove Shared Clip");
            }
        }
        if(commandLine.hasOption("rt"))
        {
            try {
                removeSharedTimelapse(commandLine.getOptionValue("rt"));
            }
            catch(com.rhombus.ApiException e)
            {
                System.out.println("Failed to Remove Shared Timelapse");
            }
        }
        if(commandLine.hasOption("rs"))
        {
            try {
                String claUUIDs = commandLine.getOptionValue("rs");
                String[] streamUuids = claUUIDs.split(",");
                if(streamUuids.length != 2)
                {
                    throw new InputMismatchException();
                }
                removeSharedStream(streamUuids[0], streamUuids[1]);
            }
            catch(InputMismatchException e)
            {
                System.out.println("Too Many/Few Arguments for removal of a Shared Stream");
            }
            catch(com.rhombus.ApiException e)
            {
                System.out.println("Failed to Remove Shared Stream");
            }
        }

        printSharedMediaReport();
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
            _videoWebService = new VideoWebserviceApi(_apiClient);
            _eventWebService = new EventWebserviceApi(_apiClient);
        }
    }
    private static void removeSharedClip(String uuid) throws Exception
    {
        final EventDeleteSharedClipGroupWSRequest removeSharedClipGroupRequest = new EventDeleteSharedClipGroupWSRequest();
        removeSharedClipGroupRequest.setUuid(uuid);
        _eventWebService.deleteSharedClipGroupV2(removeSharedClipGroupRequest);
        return;
    }
    private static void removeSharedTimelapse(String uuid) throws Exception
    {
        final VideoDeleteSharedTimelapseGroupWSRequest removeSharedTimelapseRequest = new VideoDeleteSharedTimelapseGroupWSRequest();
        removeSharedTimelapseRequest.setUuid(uuid);
        _videoWebService.deleteSharedTimelapseGroup(removeSharedTimelapseRequest);
        return;
    }
    private static void removeSharedStream(String camUuid, String uuid) throws Exception
    {
        final CameraDeleteSharedLiveVideoStreamWSRequest removeSharedStreamRequest = new CameraDeleteSharedLiveVideoStreamWSRequest();
        removeSharedStreamRequest.setCameraUuid(camUuid);
        removeSharedStreamRequest.setUuid(uuid);
        _cameraWebService.deleteSharedLiveVideoStream(removeSharedStreamRequest);
        return;
    }
    private static CameraGetMinimalCameraStateListWSResponse getCameraData() throws Exception
    {
        final CameraGetMinimalCameraStateListWSRequest cameraRequest = new CameraGetMinimalCameraStateListWSRequest();
        final CameraGetMinimalCameraStateListWSResponse cameraResponse = _cameraWebService.getMinimalCameraStateList(cameraRequest);
        return cameraResponse;
    }

    private static CameraFindSharedLiveVideoStreamsWSResponse getSharedStreams(String uuid) throws Exception
    {
        final CameraFindSharedLiveVideoStreamsForWSRequest sharedStreamRequest = new CameraFindSharedLiveVideoStreamsForWSRequest();//Create request for camera/getUptimeWindows
        sharedStreamRequest.setCameraUuid(uuid);
        final CameraFindSharedLiveVideoStreamsWSResponse sharedStreamResponse = _cameraWebService.findSharedLiveVideoStreams(sharedStreamRequest);//Send request and receive response
        return sharedStreamResponse;//Return data received from Rhombus Systems API
    }

    private static EventGetSharedClipGroupsV2WSResponse getSharedClipGroups() throws Exception
    {
        final EventGetSharedClipGroupsV2WSRequest clipGroupRequest = new EventGetSharedClipGroupsV2WSRequest();//Create request for camera/getUptimeWindows
        final EventGetSharedClipGroupsV2WSResponse clipGroupResponse = _eventWebService.getSharedClipGroupsV2(clipGroupRequest);//Send request and receive response
        return clipGroupResponse;//Return data received from Rhombus Systems API
    }

    private static VideoGetSharedTimelapseGroupsWSResponse getSharedTimelapses() throws Exception
    {
        final VideoGetSharedTimelapseGroupsWSRequest timeLapseGroupRequest = new VideoGetSharedTimelapseGroupsWSRequest();//Create request for camera/getUptimeWindows
        final VideoGetSharedTimelapseGroupsWSResponse timeLapseGroupResponse = _videoWebService.getSharedTimelapseGroups(timeLapseGroupRequest);//Send request and receive response
        return timeLapseGroupResponse;//Return data received from Rhombus Systems API
    }

    private static void printSharedMediaReport()
    {
        try {
            List<SharedClipGroupWrapperV2Type> sharedClipGroups = getSharedClipGroups().getSharedClipGroups();
            List<SharedTimelapseGroupWrapperType>  sharedTimelapseGroups = getSharedTimelapses().getSharedTimelapses();
            CameraGetMinimalCameraStateListWSResponse cameraList = getCameraData();

            List<CameraSharedLiveVideoStreamWS> sharedVideoStreams = new ArrayList<>();
            for(MinimalDeviceStateType state : cameraList.getCameraStates())
            {
                List<CameraSharedLiveVideoStreamWS> sharedVideoStreamsForUUID = getSharedStreams(state.getUuid()).getSharedLiveVideoStreams();
                if (sharedVideoStreamsForUUID != null) {
                    sharedVideoStreams.addAll(sharedVideoStreamsForUUID);
                }
            }
            System.out.println("---------------------");
            System.out.println("|Shared Media Report|");
            System.out.println("---------------------");
            if(sharedVideoStreams.size() > 0) {
                System.out.println("Shared Streams");
                System.out.println("\tCamera UUID, Stream UUID, Password Protected, Start Time, End Time, URL");
                for (CameraSharedLiveVideoStreamWS s : sharedVideoStreams) {
                    System.out.printf("\t%s,%s ,%s, %s, %s, %s\n", s.getCameraUuid(), s.getUuid(), s.isPasswordProtected(), s.getTimestampMs() / 1000, s.getExpirationTime(), s.getSharedLiveVideoStreamUrl());
                    if (s.isPasswordProtected() == null) {
                        System.out.println("\t\tFlag - Public Stream");
                    }
                    if (s.getExpirationTime() == null) {
                        System.out.println("\t\tFlag - Unlimited Stream");
                    }
                }
            }
            else{System.out.println("No Shared Streams");}
            if(sharedTimelapseGroups.size() > 0) {
                System.out.println("Shared Timelapse Groups");
                System.out.println("\tUUID, Title, Description, Password Protected, Created At, Expires At");
                for (SharedTimelapseGroupWrapperType tg : sharedTimelapseGroups) {
                    System.out.printf("\t%s, %s, %s, %s, %s, %s\n", tg.getUuid(),tg.getTitle(), tg.getDescription(), tg.isIsSecured(), tg.getCreatedAtMillis() / 1000, tg.getExpirationTimeSecs());
                    if (tg.isIsSecured() == null) {
                        System.out.println("\t\tFlag - Public Media");
                    }
                    if (tg.getExpirationTimeSecs() == null) {
                        System.out.println("\t\tFlag - Unlimited Media");
                    }
                }
            }
            else{System.out.println("No Shared Timelapse Groups");}
            if(sharedClipGroups.size() > 0){
                System.out.println("Shared Clip Groups");
                System.out.println("\tUUID, Title, Description, Password Protected, Created At, Expires At");
                for(SharedClipGroupWrapperV2Type cg : sharedClipGroups)
                {
                    System.out.printf("\t%s, %s, %s, %s, %s, %s\n", cg.getUuid(),cg.getTitle(),cg.getDescription(),cg.isIsSecured(),cg.getCreatedAtMillis()/1000,cg.getExpirationTimeSecs());
                    if(cg.isIsSecured() == false) {
                        System.out.println("\t\tFlag - Public Media");
                    }
                    if(cg.getExpirationTimeSecs() == null)
                    {
                        System.out.println("\t\tFlag - Unlimited Media");
                    }
                }
            }
            else{System.out.println("No Shared Clip Groups");}
        }
        catch (Exception e)
        {
            System.out.println("Failed to generate Media Report -" + e);
        }
    }
}
