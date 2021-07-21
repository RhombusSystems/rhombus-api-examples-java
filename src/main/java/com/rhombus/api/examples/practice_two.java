package com.rhombus.api.examples;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rhombus.ApiClient;
import com.rhombus.sdk.CameraWebserviceApi;
import com.rhombus.sdk.domain.*;
import com.rhombus.sdk.FaceWebserviceApi;
import org.apache.commons.cli.*;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.glassfish.jersey.jackson.JacksonFeature;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.ws.rs.client.ClientBuilder;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.cli.DefaultParser;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class practice_two {
    private static ApiClient _apiClient;

    public static void main(String[] args) throws Exception {

        final Options options = new Options();
        options.addRequiredOption("a", "apikey", true, "API Key");
        options.addRequiredOption("f", "faceName", true, "The face name to download from");

        // this is straight copied and pasted from the Github
        final CommandLine commandLine;
        try {
            commandLine = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.err.println(e.getMessage());

            new HelpFormatter().printHelp("java -cp rhombus-api-examples-all.jar com.rhombus.api.examples.practice_two",
                    options);
            return;
        }

        final String apiKey = commandLine.getOptionValue("apikey");
        final String faceName = commandLine.getOptionValue("faceName");

        final List<Header> defaultHeaders = new ArrayList<>();
        defaultHeaders.add(new BasicHeader("x-auth-scheme", "api-token"));
        defaultHeaders.add(new BasicHeader("x-auth-apikey", apiKey));
        final ClientBuilder clientBuilder = ClientBuilder.newBuilder();
        clientBuilder.register(JacksonFeature.class);

        final HostnameVerifier hostnameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        // Drew wrote this line to stop the EMPTY_BEANS error
        clientBuilder.register(new JacksonJaxbJsonProvider().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
        clientBuilder.hostnameVerifier(hostnameVerifier);
        _apiClient = new ApiClient();
        _apiClient.setHttpClient(clientBuilder.build());
        _apiClient.addDefaultHeader("x-auth-scheme", "api-token");
        _apiClient.addDefaultHeader("x-auth-apikey", apiKey);

        FaceWebserviceApi faceWebservice = new FaceWebserviceApi(_apiClient);
        final FaceGetFacesWSRequest getFacesRequest = new FaceGetFacesWSRequest();
        final FaceGetFacesWSResponse getFacesResponse = faceWebservice.getFacesV2(getFacesRequest);
        final List<FaceType> list_3 = getFacesResponse.getFaces();
        String faceId = null;
        String orgId = null;
        for ( FaceType cam_3 : list_3 )
        {
            String name = cam_3.getName();
            if (name.equals(faceName))
            {
                faceId = cam_3.getFaceId();
                orgId = cam_3.getOrgUuid();
            }
        }

        if (faceId == null) {
            System.out.println("There is no data for that face name.");
            return;
        }
        // not sure if this is necessary
        if (orgId == null) {
            System.out.println("There is no data for that face name.");
            return;
        }

        final FaceGetRecentFaceEventsForFaceWSRequest getRecentFaceEventsRequest = new FaceGetRecentFaceEventsForFaceWSRequest();
        getRecentFaceEventsRequest.setFaceId(faceId);
        getRecentFaceEventsRequest.setOrgUuid(orgId);
        final FaceGetRecentFaceEventsForFaceWSResponse getRecentFaceEventsResponse = faceWebservice.getRecentFaceEventsForFace(getRecentFaceEventsRequest);

        final List<FaceEventType> list = getRecentFaceEventsResponse.getFaceEvents();

        CameraWebserviceApi cameraWebservice = new CameraWebserviceApi(_apiClient);
        final CameraGetMinimalCameraStateListWSRequest getMinimalCamStateRequest = new CameraGetMinimalCameraStateListWSRequest();
        final CameraGetMinimalCameraStateListWSResponse getMinimalCamStateResponse = cameraWebservice.getMinimalCameraStateList(getMinimalCamStateRequest);

        final List<MinimalDeviceStateType> list_2 = getMinimalCamStateResponse.getCameraStates();

        String csvName = "csvFile";
        String reportName = "report";

        for (FaceEventType cam : list) {
            String name = cam.getFaceName();
            String device = cam.getDeviceUuid();
            Long ms_time = cam.getEventTimestamp();
            DateFormat simple = new SimpleDateFormat("dd MMM yyyy HH:mm:ss"); // Create data format
            Date timestamp = new Date(ms_time); // using Date() constructor to create a date from milliseconds

            for (MinimalDeviceStateType cam_2 : list_2) {
                String uuid = cam_2.getUuid();
                if (device.equals(uuid)) {
                    String cam_name = cam_2.getName();
                    System.out.println("\n");
                    System.out.println(name);
                    System.out.println(cam_name);
                    System.out.println(simple.format(timestamp));
                }
            }
        }
    }
}
// Eventually I want this code to have three elements:
// CSV creation
// Input validate the API key (already have the face name)
// after I get theCz
