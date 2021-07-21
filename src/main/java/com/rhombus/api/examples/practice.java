package com.rhombus.api.examples;

// imports are created for you as code is written

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rhombus.ApiClient;
import com.rhombus.sdk.CameraWebserviceApi;
import com.rhombus.sdk.LocationWebserviceApi;
import com.rhombus.sdk.domain.*;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.ws.rs.client.ClientBuilder;
import java.util.ArrayList;
import java.util.List;

// declaring class
public class practice
{
    // static means that the variable or method marked as such is available at the class level.
    // In other words, you don't need to create an instance of the class to access it.
    private static ApiClient _apiClient;
    public static void main(String[] args) throws Exception
    {
        // final means that the variable will not be altered throughout the program
        final String apiKey = "9Ts3iQ_HSZGHEqwxZnPKpA";
        final List<Header> defaultHeaders = new ArrayList<>();
        defaultHeaders.add(new BasicHeader("x-auth-scheme", "api-token"));
        defaultHeaders.add(new BasicHeader("x-auth-apikey", apiKey));

        final ClientBuilder clientBuilder = ClientBuilder.newBuilder();
        clientBuilder.register(new JacksonJaxbJsonProvider().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));

        // an object is an "instance of a class"
        // in this case, "HostnameVerifier is instance and "hostnameVerifier" is a variable
        final HostnameVerifier hostnameVerifier = new HostnameVerifier()
        {
            @Override
            public boolean verify(String hostname, SSLSession session)
            {
                return true;
            }
        };

        clientBuilder.hostnameVerifier(hostnameVerifier);
        _apiClient = new ApiClient();
        _apiClient.setHttpClient(clientBuilder.build());
        _apiClient.addDefaultHeader("x-auth-scheme", "api-token");
        _apiClient.addDefaultHeader("x-auth-apikey", apiKey);
        final CameraWebserviceApi cameraWebservice = new CameraWebserviceApi(_apiClient);
        final CameraGetMinimalCameraStateListWSRequest getMinimalCameraStateRequest = new CameraGetMinimalCameraStateListWSRequest();
        final CameraGetMinimalCameraStateListWSResponse getMinimalCameraStateListResponse = cameraWebservice.getMinimalCameraStateList(getMinimalCameraStateRequest);

        final List <MinimalDeviceStateType> list = getMinimalCameraStateListResponse.getCameraStates();

        System.out.println(getMinimalCameraStateListResponse);
        for ( MinimalDeviceStateType cam : list)
        {
            String name = cam.getName();
            MinimalDeviceStateType.HwVariationEnum variation = cam.getHwVariation();
            // i do not understand the syntax on the line above ^

            System.out.println(name);
            System.out.println(variation);
        }

        final LocationWebserviceApi locationWebservice = new LocationWebserviceApi(_apiClient);
        final LocationGetLocationsWSRequest getLocationsRequest = new LocationGetLocationsWSRequest();
        final LocationGetLocationsWSResponse getLocationsResponse = locationWebservice.getLocations(getLocationsRequest);

        System.out.println(getLocationsResponse);


////
//        System.out.println(getLocationsResponse);
////
    }
}
// the next step would to be able to make multiple API calls
// then you can convert some information into other types of information\

// for this code I am going to try to convert the location uuid to an address
// then I want to print out the camera name and the address
// what do you do when you have to incorporate payloads?



