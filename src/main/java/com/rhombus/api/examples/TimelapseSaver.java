package com.rhombus.api.examples;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rhombus.ApiClient;
import com.rhombus.ApiException;
import com.rhombus.sdk.CameraWebserviceApi;
import com.rhombus.sdk.VideoWebserviceApi;
import com.rhombus.sdk.domain.*;
import org.apache.commons.cli.*;
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
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TimelapseSaver
{
    private static HttpClient _videoClient;
    private static ApiClient _apiClient;
    private static CameraWebserviceApi _camerawebservice;
    private static VideoWebserviceApi _videowebservice;


    public static void main(String[] args) throws Exception
    {
        final Options options = new Options();
        options.addRequiredOption("a", "apikey", true, "API Key");
        options.addRequiredOption("c", "camera", true, "Which camera do you want the timelapse from");
        options.addOption("s", "startTime", true, "Add the end search time in yyyy-mm-dd~(0)0:00:00 or default to 1 day before current time");
        options.addOption("e", "endTime", true, "Add the end search time in yyyy-mm-dd~(0)0:00:00 or default to current time");
        options.addOption("d", "vidDuration", true, "Specify the duration of the timelapse you want default: 120 sec");
        options.addOption("f", "format", true, "Specify the format of the video [.mov|.mp4]");
        options.addOption("t", "timestamp", true, "Do you want the timestamp on the timelapse [True]");
        options.addOption("cd", "cameraDetails", true, "Do you want the camera details [True]");
        options.addOption("sw", "skipWeekends", true, "Do you want to skip the weekends in the timelapse [True]");
        options.addOption("sn", "skipNights", true, "Do you want to skip nights in the timelapse [True]");
        options.addOption("n", "name", true, "Path and name of the timelapse");

        final CommandLine commandLine;
        try
        {
            commandLine = new DefaultParser().parse(options, args);
        }
        catch (ParseException e)
        {
            System.err.println(e.getMessage());

            new HelpFormatter().printHelp(
                    "java -cp rhombus-api-examples-all.jar com.rhombus.api.examples.TimelapseSaver",
                    options);
            return;
        }

        final String apikey = commandLine.getOptionValue("apikey");
        _initialize(apikey);
        final String camera = commandLine.getOptionValue("camera");
        long startTime;
        long endTime;
        int duration;
        boolean timestamp = false;
        boolean cameraDetails = false;
        boolean skipWeekends = false;
        boolean skipNights = false;
        String format;
        String outputFile;
        CameraGetMinimalCameraStateListWSResponse cameraData = dataCamera();
        String cameraUuid = uuidConverter(cameraData, camera);

        if (commandLine.hasOption("startTime"))
        {
            startTime = millisecondTime(commandLine.getOptionValue("startTime"));
        }
        else
        {
            //Getting the current date
            Date date = new Date();
            //This method returns the time a day ago in millis
            startTime = date.getTime() - (1 * 24 * 60 * 60 * 1000);
        }

        if (commandLine.hasOption("endTime"))
        {
            endTime = millisecondTime(commandLine.getOptionValue("endTime"));
        }
        else
        {
            //Getting the current date
            Date date = new Date();
            //This method returns the time in millis
            endTime = date.getTime();
        }

        if ((endTime - startTime) < (60 * 60 * 1000))
        {
            System.out.println("Put a span of at least an hour");
            System.exit(0);
        }

        if (commandLine.hasOption("vidDuration"))
        {
            String durationString = commandLine.getOptionValue("vidDuration");
            duration = Integer.parseInt(durationString);
        }
        else
        {
            duration = 120;
        }

        if (commandLine.hasOption("format"))
        {
            format = commandLine.getOptionValue("format");
            if(!format.equals(".mov") && !format.equals(".mp4"))
            {
                System.out.println("-c [.mov, .mp4]");
                System.exit(0);

            }
        }
        else
        {
            format = ".mov";
        }

        if (commandLine.hasOption("timestamp"))
        {
            String timestampString = commandLine.getOptionValue("timestamp");
            if (!timestampString.equals("True"))
            {
                System.out.println(("[True]"));
                System.exit(0);
            }
            else
            {
                timestamp = true;
            }
        }
        else
        {
            timestamp = false;
        }

        if (commandLine.hasOption("cameraDetails"))
        {
            String cameraDetailsString = commandLine.getOptionValue("cameraDetails");
            if (!cameraDetailsString.equals("True"))
            {
                System.out.println(("[True]"));
                System.exit(0);
            }
            else
            {
                cameraDetails = true;
            }
        }
        else
        {
            cameraDetails = false;
        }

        if (commandLine.hasOption("skipWeekends"))
        {
            String skipWeekendsString = commandLine.getOptionValue("skipWeekends");
            if (!skipWeekendsString.equals("True"))
            {
                System.out.println(("[True]"));
                System.exit(0);
            }
            else
            {
                skipWeekends = true;
            }
        }
        else
        {
            skipWeekends = false;
        }

        if (commandLine.hasOption("skipNights"))
        {
            String skipNightsString = commandLine.getOptionValue("skipNights");
            if (!skipNightsString.equals("True"))
            {
                System.out.println(("[True]"));
                System.exit(0);
            }
            else
            {
                skipNights = true;
            }
        }
        else
        {
            skipNights = false;
        }

        if (commandLine.hasOption("name"))
        {
            outputFile = commandLine.getOptionValue("name");
        }
        else
        {
            String path = System.getProperty("user.dir");
            outputFile = path + "/timelapse" + format;;
        }
        String clipUuid = generateTimelapse(cameraUuid, startTime, endTime, duration, timestamp, cameraDetails, skipNights, skipWeekends, format);

        TimeUnit.SECONDS.sleep(15);
        while (!downloadProgress(clipUuid))
        {
            TimeUnit.SECONDS.sleep(10);
        }
        try (final FileOutputStream outputStream = new FileOutputStream(outputFile))
        {
            savingTimelapse(clipUuid, outputStream, apikey);
        }

    }

    public static void _initialize(String apikey)
    {
        /*
         * API CLIENT
         */
        {
            final List<Header> defaultHeaders = new ArrayList<>();
            defaultHeaders.add(new BasicHeader("x-auth-scheme", "api-token"));
            defaultHeaders.add(new BasicHeader("x-auth-apikey", apikey));

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
            _apiClient.addDefaultHeader("x-auth-apikey", apikey);

            _camerawebservice = new CameraWebserviceApi(_apiClient);
            _videowebservice = new VideoWebserviceApi(_apiClient);
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

    public static void savingTimelapse(String clipUuid, final OutputStream outputStream, String apikey) throws Exception
    {
        String mediaBaseURL = "https://media.rhombussystems.com/media/timelapse/";
        final HttpGet videoRequest = new HttpGet(mediaBaseURL + clipUuid + ".mp4");
        videoRequest.setHeader("x-auth-scheme", "api-token");
        videoRequest.setHeader("x-auth-apikey", apikey);

        final HttpResponse videoResponse = _videoClient.execute(videoRequest);
        final byte[] VideoDataRaw = IOUtils.toByteArray(videoResponse.getEntity().getContent());
        IOUtils.write(VideoDataRaw, outputStream);

    }

    public static CameraGetMinimalCameraStateListWSResponse dataCamera() throws ApiException
    {
        CameraGetMinimalCameraStateListWSRequest cameraDatarequest = new CameraGetMinimalCameraStateListWSRequest();
        return _camerawebservice.getMinimalCameraStateList(cameraDatarequest);
    }

    public static String uuidConverter(CameraGetMinimalCameraStateListWSResponse data, String camera)
    {
        String camUuid = null;
        final List <MinimalDeviceStateType> list = data.getCameraStates();
        for( MinimalDeviceStateType uuid : list)
        {
            if (camera.equals(uuid.getName()))
            {
                camUuid = uuid.getUuid();
                return camUuid;
            }
        }
        return camUuid;
    }

    public static long millisecondTime(String time) throws Exception
    {
        long milliTime = new java.text.SimpleDateFormat("MM/dd/yyyy~HH:mm:ss").parse(time).getTime();
        return milliTime;
    }

    public static boolean downloadProgress(String clipUuid) throws Exception
    {
        VideoGetTimelapseClipsWSRequest getTimelapseRequest = new VideoGetTimelapseClipsWSRequest();
        VideoGetTimelapseClipsWSResponse getTimelapseClipsResponse = _videowebservice.getTimelapseClips(getTimelapseRequest);
        final List <TimelapseClipType> list = getTimelapseClipsResponse.getTimelapseClips();
        for( TimelapseClipType item : list)
        {
            if (clipUuid.equals(item.getClipUuid()))
            {
                if (item.getStatus().getPercentComplete() == 100)
                {
                    return true;
                }
                else
                {
                    return false;
                }
            }
        }
        return false;
    }

    public static String generateTimelapse(String cameraUuid, long startTime, long endTime, int duration, boolean timestamp, boolean cameraDetails, boolean skipNights, boolean skipWeekends, String format) throws Exception
    {
        VideoGenerateTimelapseClipWSRequest generateTimlapseRequest = new VideoGenerateTimelapseClipWSRequest();
        generateTimlapseRequest.setDeviceUuids(Arrays.asList(cameraUuid));
        generateTimlapseRequest.setStartTime(startTime);
        generateTimlapseRequest.setStopTime(endTime);
        generateTimlapseRequest.setVideoDuration(duration);
        generateTimlapseRequest.setDrawTimestamp(timestamp);
        generateTimlapseRequest.setDrawCameraDetails(cameraDetails);
        generateTimlapseRequest.setSkipNights(skipNights);
        generateTimlapseRequest.setSkipWeekends(skipWeekends);
        generateTimlapseRequest.setVideoFormat(format);
        VideoGenerateTimelapseClipWSResponse generateTimelapseResponse = _videowebservice.generateTimelapseClip(generateTimlapseRequest);
        return generateTimelapseResponse.getClipUuid();
    }
}
