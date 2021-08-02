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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import java.sql.Timestamp;

//This script runs a quick report on the status of all cameras in an organization, report includes name,uuid,status and details if there are anypublic
class CreateSharedMediaReport
{
    private static ApiClient _apiClient;
    private static CameraWebserviceApi _cameraWebService;
    private static VideoWebserviceApi _videoWebService;
    private static EventWebserviceApi _eventWebService;
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
                System.out.println("\tCamera UUID, Password Protected, Start Time, End Time, URL");
                for (CameraSharedLiveVideoStreamWS s : sharedVideoStreams) {
                    System.out.printf("\t%s, %s, %s, %s, %s\n", s.getCameraUuid(), s.isPasswordProtected(), s.getTimestampMs() / 1000, s.getExpirationTime(), s.getSharedLiveVideoStreamUrl());
                    if (s.isPasswordProtected() == null) {
                        System.out.println("\t\tFlag Unsecured Stream");
                    }
                    if (s.getExpirationTime() == null) {
                        System.out.println("\t\tFlag Unlimited Stream");
                    }
                }
            }
            else{System.out.println("No Shared Streams");}
            if(sharedTimelapseGroups.size() > 0) {
                System.out.println("Shared Timelapse Groups");
                System.out.println("\tTitle, UUID, Description, Password Protected, Created At, Expires At");
                for (SharedTimelapseGroupWrapperType tg : sharedTimelapseGroups) {
                    System.out.printf("\t%s, %s, %s, %s, %s, %s\n", tg.getTitle(),tg.getUuid(), tg.getDescription(), tg.isIsSecured(), tg.getCreatedAtMillis() / 1000, tg.getExpirationTimeSecs());
                    if (tg.isIsSecured() == null) {
                        System.out.println("\t\tFlag Unsecured Stream");
                    }
                    if (tg.getExpirationTimeSecs() == null) {
                        System.out.println("\t\tFlag Unlimited Stream");
                    }
                }
            }
            else{System.out.println("No Shared Timelapse Groups");}
            if(sharedClipGroups.size() > 0){
                System.out.println("Shared Clip Groups");
                System.out.println("\tTitle, UUID, Description, Password Protected, Created At, Expires At");
                for(SharedClipGroupWrapperV2Type cg : sharedClipGroups)
                {
                    System.out.printf("\t%s, %s, %s, %s, %s, %s\n", cg.getTitle(),cg.getUuid(),cg.getDescription(),cg.isIsSecured(),cg.getCreatedAtMillis()/1000,cg.getExpirationTimeSecs());
                    if(cg.isIsSecured() == false) {
                        System.out.println("\t\tFlag Unsecured Stream");
                    }
                    if(cg.getExpirationTimeSecs() == null)
                    {
                        System.out.println("\t\tFlag Unlimited Stream");
                    }
                }
            }
            else{System.out.println("No Shared Clip Groups");}
        }
        catch (Exception e)
        {
            System.out.println("Failed" + e);
        }
    }
}
