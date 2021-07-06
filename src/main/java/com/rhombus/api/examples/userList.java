package com.rhombus.api.examples;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rhombus.ApiClient;
import com.rhombus.sdk.UserWebserviceApi;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class userList
{

    private static ApiClient _apiClient;
    private static UserWebserviceApi _userwebservice;

    public static void main(String[] args) throws Exception
    {
        final Options options = new Options();
        options.addRequiredOption("a", "apikey", true, "API Key");
        options.addOption("c", "csv", true, "Name the csv file for the names");
        options.addOption("r", "report", true, "Name the file folder that the csv will be in");
        options.addOption("n", "names", true, "Put names if you want specific people in the csv");

        final CommandLine commandLine;
        try
        {
            commandLine = new DefaultParser().parse(options, args);
        }
        catch (ParseException e)
        {
            System.err.println(e.getMessage());

            new HelpFormatter().printHelp(
                    "java -cp rhombus-api-examples-all.jar com.rhombus.api.examples.CopyFootageToLocalStorage",
                    options);
            return;
        }

        String apikey = commandLine.getOptionValue("apikey");
        String csvName;
        String reportName;
        List<List<String>> names = null;
        List <String> namesList = null;

        if (commandLine.hasOption("csv"))
        {
            csvName = commandLine.getOptionValue("csv");
        }
        else
        {
            csvName = "csvFile";
        }

        if (commandLine.hasOption("report"))
        {
            reportName = commandLine.getOptionValue("report");
        }
        else
        {
            reportName = "Report";
        }
        if (commandLine.hasOption("names"))
        {
            namesList = namesOrganizer(commandLine.getOptionValue("names"));
        }

        _initialize(apikey);
        UserGetUsersInOrgWSResponse UserData = getUsersdata();
        File fileCSV = CSVCreate(reportName, csvName);
        CSVAdd(UserData, fileCSV, namesList);
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

    public static List<String> namesOrganizer(String namesList)
    {
        namesList = namesList.replace(", ",",");
        List<String> namesArray = Arrays.asList(namesList.split(","));
        return namesArray;
    }

    private static UserGetUsersInOrgWSResponse getUsersdata() throws Exception
    {
        final UserGetUsersInOrgWSRequest userRequest = new UserGetUsersInOrgWSRequest();
        final UserGetUsersInOrgWSResponse userResponse = _userwebservice.getUsersInOrg(userRequest);
        return userResponse;
    }

    private static File CSVCreate(String reportName, String csvName)
    {
        File report = new File(reportName);
        report.mkdir();
        File csvFile = new File(report + "/" + csvName + ".csv");
        if (csvFile.isFile())
        {
            System.out.println("File already exists");
            System.exit(0);
        }
        return csvFile;
    }
    private static void CSVAdd(UserGetUsersInOrgWSResponse UserData, File csvFile, List <String> namesList) throws Exception
    {
        FileWriter csvWriter = new FileWriter(csvFile);
        csvWriter.append("Name");
        csvWriter.append(",");
        csvWriter.append("Email");
        csvWriter.append("\n");

        final List <UserType> Users = UserData.getUsers();
        if (namesList != null)
        {
            for (String name : namesList)
            {
                for (UserType rowData : Users)
                {
                    if (name.equals(rowData.getName()))
                    {
                        csvWriter.append(String.join(",", rowData.getName()));
                        csvWriter.append(',');
                        csvWriter.append(String.join(",", rowData.getEmailCaseSensitive()));
                        csvWriter.append("\n");
                    }

                }
            }

        }
        else
        {
            for (UserType rowData : Users)
            {
                csvWriter.append(String.join(",", rowData.getName()));
                csvWriter.append(',');
                csvWriter.append(String.join(",", rowData.getEmailCaseSensitive()));
                csvWriter.append("\n");
            }
        }
        csvWriter.flush();
        csvWriter.close();
    }
}
