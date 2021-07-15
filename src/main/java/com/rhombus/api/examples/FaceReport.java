package com.rhombus.api.examples;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rhombus.ApiClient;
import com.rhombus.sdk.CameraWebserviceApi;
import com.rhombus.sdk.FaceWebserviceApi;
import com.rhombus.sdk.domain.*;

import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.ws.rs.client.ClientBuilder;

import java.io.*;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.text.SimpleDateFormat;

public class FaceReport
{

    private static ApiClient _apiClient;
    private static HttpClient _videoClient;
    private static FaceWebserviceApi _facewebservice;
    private static CameraWebserviceApi _camerawebservice;

    public static void main(String[] args) throws Exception
    {
        final Options options = new Options();
        options.addRequiredOption("a", "apikey", true, "API Key");
        options.addOption("s", "start", true, "Start Time format: yyyy-MM-dd~HH:mm:ss");
        options.addOption("e", "end", true, "End Time format: yyyy-MM-dd~HH:mm:ss");
        options.addOption("f", "filter", true, "What filter do you want [alert|trusted|named|other]");
        options.addOption("n", "name", true, "Searches for a name");
        options.addOption("cn", "cameraname", true, "name of the camera to search");
        options.addOption("c", "csv", true, "Name of the csv");
        options.addOption("r", "report", true, "Name and  path of the Report folder");

        final CommandLine commandLine;
        try {
            commandLine = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.err.println(e.getMessage());

            new HelpFormatter().printHelp(
                    "java -cp rhombus-api-examples-all.jar com.rhombus.api.examples.FaceReport",
                    options);
            return;
        }
        final String apiKey = commandLine.getOptionValue("apikey");
        long startTime;
        long endTime;
        FaceGetRecentFaceEventsWSRequestFilter.TypesEnum filter = null;
        String filterString;
        String name = null;
        String cameraname = null;
        String csvName;
        String report;

        if (commandLine.hasOption("start"))
        {
            startTime = millisecondTime(commandLine.getOptionValue("start"));
        }
        else
        {
            //Getting the current date
            Date date = new Date();
            //This method returns the time 1 hour ago in millis
            startTime = date.getTime() - (60*60*1000);
        }
        if (commandLine.hasOption("end"))
        {
            endTime = millisecondTime(commandLine.getOptionValue("end"));
        }
        else
        {
            //Getting the current date
            Date date = new Date();
            //This method returns the time 1 hour ago in millis
            endTime = date.getTime();
        }
        if (commandLine.hasOption("filter"))
        {
            filterString = commandLine.getOptionValue("filter");
            if (!filterString.equals("alert") && !filterString.equals("trusted") && !filterString.equals("named") && !filterString.equals("other"))
            {
                System.out.println("Please use one of the options [alert|trusted|named|other]");
                System.exit(0);
            }
            else
            {
                filter = FaceGetRecentFaceEventsWSRequestFilter.TypesEnum.fromValue(filterString);
            }
        }
        else
        {
            filter = FaceGetRecentFaceEventsWSRequestFilter.TypesEnum.NAMED;
        }
        if (commandLine.hasOption("name"))
        {
            name = commandLine.getOptionValue("name");
        }
        if (commandLine.hasOption("cameraname"))
        {
            cameraname = commandLine.getOptionValue("cameraname");
        }
        if (commandLine.hasOption("csv"))
        {
            csvName = commandLine.getOptionValue("csv");
        }
        else
        {
            csvName = "csvFile.csv";
        }
        if (commandLine.hasOption("report"))
        {
            report = commandLine.getOptionValue("report");
        }
        else
        {
            String path = System.getProperty("user.dir");
            report = path + "/Report";
        }

        _initialize(apiKey);
        FaceGetRecentFaceEventsWSResponse recentfaceData = recentFaces(filter, startTime, endTime);
        CameraGetMinimalCameraStateListWSResponse Datacamera = cameraData();
        File csvFile = CSVcreate(csvName, report);
        CSVadd(csvFile, report, recentfaceData, name, cameraname, Datacamera, apiKey);

    }

    private static void _initialize(String apiKey)
    {
        /*
         * API CLIENT
         */
        {
            final List<Header> defaultHeaders = new ArrayList<>();
            defaultHeaders.add(new BasicHeader("x-auth-scheme", "api-token"));
            defaultHeaders.add(new BasicHeader("x-auth-apikey", apiKey));

            final ClientBuilder clientBuilder = ClientBuilder.newBuilder();
            clientBuilder.register(new JacksonJaxbJsonProvider().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
            ;

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

            _facewebservice = new FaceWebserviceApi(_apiClient);
            _camerawebservice = new CameraWebserviceApi(_apiClient);
        }
        /*
         * MEDIA/VIDEO CLIENT
         */
        {
            final HttpClientBuilder httpClientBuilder = HttpClients.custom();

            final HostnameVerifier hostnameVerifier = new HostnameVerifier()
            {

                @Override
                public boolean verify(String hostname, SSLSession session)
                {
                    return true;
                }
            };

            _videoClient = httpClientBuilder.setSSLHostnameVerifier(hostnameVerifier).build();
        }
    }

    private static void savingIMG(String apikey, String thumbnail, OutputStream outputStream) throws Exception
    {
        {
            final HttpGet pictureRequest = new HttpGet(thumbnail);
            pictureRequest.setHeader("x-auth-scheme", "api-token");
            pictureRequest.setHeader("x-auth-apikey", apikey);

            final HttpResponse pictureResponse = _videoClient.execute(pictureRequest);
            final byte[] VideoDataRaw = IOUtils.toByteArray(pictureResponse.getEntity().getContent());
            IOUtils.write(VideoDataRaw, outputStream);

        }
    }

    private static String humanTime(long millisec)
    {
        Timestamp timestamp = new Timestamp(millisec);
        String date = timestamp.toString().split("\\.")[0];
        return date;
    }

    private static long millisecondTime(String time) throws Exception
    {
        String pattern = "yyyy-MM-dd~HH:mm:ss";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
        long milliTime = simpleDateFormat.parse(time).getTime();
        return milliTime;
    }

    private static CameraGetMinimalCameraStateListWSResponse cameraData() throws Exception
    {
        final CameraGetMinimalCameraStateListWSRequest minimalcamerarequest = new CameraGetMinimalCameraStateListWSRequest();
        final CameraGetMinimalCameraStateListWSResponse minimalcameraresponse = _camerawebservice.getMinimalCameraStateList(minimalcamerarequest);
        return minimalcameraresponse;
    }

    private static String cameraName(String deviceUuid, CameraGetMinimalCameraStateListWSResponse dataCamera)
    {
        final List <MinimalDeviceStateType> Camera = dataCamera.getCameraStates();
        for( MinimalDeviceStateType rowdata : Camera)
        {
            if (deviceUuid.equals(rowdata.getUuid()))
            {
                return rowdata.getName();
            }
        }
        return "";
    }

    private static FaceGetRecentFaceEventsWSResponse recentFaces(FaceGetRecentFaceEventsWSRequestFilter.TypesEnum filter, long startTime, long endTime) throws Exception
    {
        List <FaceGetRecentFaceEventsWSRequestFilter.TypesEnum> filterList;
        filterList = new ArrayList(Arrays.asList(filter));
        final FaceGetRecentFaceEventsWSRequest faceeventsrequest = new FaceGetRecentFaceEventsWSRequest();
        FaceGetRecentFaceEventsWSRequestFilter Filter = new FaceGetRecentFaceEventsWSRequestFilter();
        Filter.setTypes(filterList);
        faceeventsrequest.setFilter(Filter);
        FaceGetRecentFaceEventsWSRequestInterval interval = new FaceGetRecentFaceEventsWSRequestInterval();
        interval.setStart(startTime);
        interval.setEnd(endTime);
        faceeventsrequest.setInterval(interval);
        final FaceGetRecentFaceEventsWSResponse faceeventsresponse = _facewebservice.getRecentFaceEventsV2(faceeventsrequest);
        return faceeventsresponse;
    }

    private static void CSVadd(File csvFile, String report, FaceGetRecentFaceEventsWSResponse recentFaceData, String name, String cameraname, CameraGetMinimalCameraStateListWSResponse Datacamera, String apikey)
    {
        try (CSVPrinter printer = new CSVPrinter(new FileWriter(report + '/' + csvFile), CSVFormat.EXCEL))
        {
            long count = 1;
            printer.printRecord("Name", "Date", "Camera", "Image File Path");
            final List <FaceEventType> Faces = recentFaceData.getFaceEvents();
            for (FaceEventType rowData : Faces)
            {
                String date = humanTime(rowData.getEventTimestamp());
                String camera = cameraName(rowData.getDeviceUuid(), Datacamera);
                String faceName = rowData.getFaceName();
                String filename = report + '/' + faceName + '_' + count + ".jpg";
                String thumbnail = "https://media.rhombussystems.com/media/faces?s3ObjectKey=" + rowData.getThumbnailS3Key();
                String outputFile = filename;
                if (cameraname != null && name !=(null))
                {
                    if (cameraname.equals(camera) && name.equals(faceName))
                    {
                        printer.printRecord(faceName, date, camera, filename);
                        try (final FileOutputStream outputStream = new FileOutputStream(outputFile))
                        {
                            savingIMG(apikey, thumbnail, outputStream);
                            count ++;
                        }
                    }
                }
                else if (cameraname != null)
                {
                    if (cameraname.equals(camera))
                    {
                        printer.printRecord(faceName, date, camera, filename);
                        try (final FileOutputStream outputStream = new FileOutputStream(outputFile))
                        {
                            savingIMG(apikey, thumbnail, outputStream);
                            count ++;
                        }
                    }
                }
                else if (name != (null))
                {
                    if (name.equals(faceName))
                    {
                        printer.printRecord(faceName, date, camera, filename);
                        try (final FileOutputStream outputStream = new FileOutputStream(outputFile))
                        {
                            savingIMG(apikey, thumbnail, outputStream);
                            count ++;
                        }
                    }
                }
                else
                {
                    printer.printRecord(faceName, date, camera, filename);
                    try (final FileOutputStream outputStream = new FileOutputStream(outputFile))
                    {
                        savingIMG(apikey, thumbnail, outputStream);
                        count ++;
                    }
                }

            }
        }
        catch (IOException ex)
        {
            System.out.println("Couldn't open file");
            System.exit(0);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private static File CSVcreate(String csv, String report)
    {
        new File(report).mkdir();
        File csvFile = new File(csv);
        return csvFile;
    }
}
