package com.rhombus.api.examples;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rhombus.ApiClient;
import com.rhombus.sdk.UserWebserviceApi;
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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class UserList
{

    private static ApiClient _apiClient;
    private static UserWebserviceApi _userwebservice;

    public static void main(String[] args) throws Exception
    {
        final Options options = new Options();
        options.addRequiredOption("a", "apikey", true, "API Key");
        options.addOption("c", "csv", true, "Path to the csv and name the csv");

        final CommandLine commandLine;
        try
        {
            commandLine = new DefaultParser().parse(options, args);
        }
        catch (ParseException e)
        {
            System.err.println(e.getMessage());

            new HelpFormatter().printHelp(
                    "java -cp rhombus-api-examples-all.jar com.rhombus.api.examples.UserList",
                    options);
            return;
        }

        String apikey = commandLine.getOptionValue("apikey");
        String csv;
        String path;

        if (commandLine.hasOption("csv"))
        {
            csv = commandLine.getOptionValue("csv");
        }
        else
        {
            path = System.getProperty("user.dir");
            csv = path + "/users.csv";
        }

        _initialize(apikey);
        UserGetUsersInOrgWSResponse UserData = getUserdata();
        File fileCSV = CSVCreate(csv);
        CSVAdd(UserData, fileCSV);
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

            _userwebservice = new UserWebserviceApi(_apiClient);
        }
    }

    private static UserGetUsersInOrgWSResponse getUserdata() throws Exception
    {
        final UserGetUsersInOrgWSRequest userRequest = new UserGetUsersInOrgWSRequest();
        final UserGetUsersInOrgWSResponse userResponse = _userwebservice.getUsersInOrg(userRequest);
        return userResponse;
    }

    private static File CSVCreate(String csv)
    {
        File csvFile = new File(csv);
        return csvFile;
    }
    private static void CSVAdd(UserGetUsersInOrgWSResponse UserData, File csvFile)
    {
        try (CSVPrinter printer = new CSVPrinter(new FileWriter(csvFile), CSVFormat.EXCEL))
        {
            printer.printRecord("Name", "Email");
            final List <UserType> Users = UserData.getUsers();
            for (UserType rowData : Users)
            {
                printer.printRecord(rowData.getName(), rowData.getEmailCaseSensitive());
            }
        }
        catch (IOException ex)
        {
            System.out.println("Couldn't open file");
            System.exit(0);
        }
    }
}
