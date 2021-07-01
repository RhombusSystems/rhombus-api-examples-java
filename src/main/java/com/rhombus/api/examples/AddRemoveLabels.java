package com.rhombus.api.examples;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rhombus.ApiClient;
import com.rhombus.sdk.FaceWebserviceApi;
import com.rhombus.sdk.domain.FaceAddFaceLabelWSRequest;
import com.rhombus.sdk.domain.FaceRemoveFaceLabelWSRequest;
import org.apache.commons.cli.*;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.ws.rs.client.ClientBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AddRemoveLabels
{
    private static ApiClient _apiClient;
    private static FaceWebserviceApi _faceWebservice;

    public static void main(String[] args) throws Exception
    {
        final Options options = new Options();
        options.addRequiredOption("a", "apikey", true, "API Key");
        options.addRequiredOption("c", "choice", true, "Add or remove a label");
        options.addRequiredOption("l", "label", true, "Label name");
        options.addRequiredOption("n", "names", true, "Names of people to add or remove labels from (ex: name, name, name)");

        final CommandLine commandLine;
        try {
            commandLine = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.err.println(e.getMessage());

            new HelpFormatter().printHelp(
                    "java -cp build/libs/rhombus-api-examples-all.jar com.rhombus.api.examples.AddRemoveLabels",
                    options);
            return;
        }

        final String apiKey = commandLine.getOptionValue("apikey");
        String namesList = commandLine.getOptionValue("names");
        final String choice = commandLine.getOptionValue("choice");
        final String label = commandLine.getOptionValue("label");

        if(!choice.equals("add") && !choice.equals("remove"))
        {
            System.out.println("-c [add, remove]");
            System.exit(0);

        }

        _initialize(apiKey);
        List<String> namesArray = names(namesList);

        for( String name : namesArray)
        {
            if(choice.equals("add"))
            {
                addLabel(name, label);
            }
            else if(choice.equals("remove"))
            {
                removeLabel(name, label);
            }
        }

    }

    private static void _initialize(final String apiKey) throws Exception
    {
        /*
         * API CLIENT
         */
        {
            final List<Header> defaultHeaders = new ArrayList<>();
            defaultHeaders.add(new BasicHeader("x-auth-scheme", "api-token"));
            defaultHeaders.add(new BasicHeader("x-auth-apikey", apiKey));

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

            _faceWebservice = new FaceWebserviceApi(_apiClient);

        }
    }
    public static void addLabel(String name, String label) throws Exception
    {
        final FaceAddFaceLabelWSRequest addFaceLabel = new FaceAddFaceLabelWSRequest();
        addFaceLabel.setLabel(label);
        addFaceLabel.setFaceIdentifier(name);
        _faceWebservice.addFaceLabel(addFaceLabel);
    }
    public static void removeLabel(String name, String label) throws Exception
    {
        final FaceRemoveFaceLabelWSRequest removeFaceLabel = new FaceRemoveFaceLabelWSRequest();
        removeFaceLabel.setLabel(label);
        removeFaceLabel.setFaceIdentifier(name);
        _faceWebservice.removeFaceLabel(removeFaceLabel);
    }
    public static List<String> names(String namesList)
    {
        namesList = namesList.replace(", ",",");
        List<String> namesArray = Arrays.asList(namesList.split(","));
        return namesArray;
    }
}