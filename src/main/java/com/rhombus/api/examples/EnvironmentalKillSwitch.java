package com.rhombus.api.examples;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rhombus.ApiClient;
import com.rhombus.sdk.ClimateWebserviceApi;
import com.rhombus.sdk.domain.ClimateGetMinimalClimateStatesWSRequest;
import com.rhombus.sdk.domain.ClimateGetMinimalClimateStatesWSResponse;
import com.rhombus.sdk.domain.ClimateMinimalClimateStateType;
import org.apache.commons.cli.*;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.ws.rs.client.ClientBuilder;
import java.util.ArrayList;
import java.util.List;

public class EnvironmentalKillSwitch
{
    private static ApiClient _apiClient;
    private static ClimateWebserviceApi _climatewebservice;

    public static void main(String[] args) throws Exception
    {
        final Options options = new Options();
        options.addRequiredOption("ak", "apikey", true, "API Key");
        options.addRequiredOption("p", "plug", true, "What plug do yuo want to turn off");
        options.addOption("ho", "host", true, "What is the host name of the power strip");
        options.addOption("a", "alias", true, "What is the alias name of the power strip");
        options.addOption("hot", "hot", true, "What is the max temp to turn off the device");
        options.addOption("c", "cold", true, "What is the lowest temp to turn on the device");
        final CommandLine commandLine;
        try
        {
            commandLine = new DefaultParser().parse(options, args);
        }
        catch (ParseException e)
        {
            System.err.println(e.getMessage());

            new HelpFormatter().printHelp(
                    "java -cp rhombus-api-examples-all.jar com.rhombus.api.examples.environmentalKillSwitch",
                    options);
            return;
        }

        String plugString = commandLine.getOptionValue("plug");
        String hotString;
        String coldString;
        if (commandLine.hasOption("hot"))
        {
            hotString = commandLine.getOptionValue("hot");
        }
        else
        {
            hotString = "75";
        }
        if (commandLine.hasOption("hot"))
        {
            coldString = commandLine.getOptionValue("cold");
        }
        else
        {
            coldString = "70";
        }
        final String apikey = commandLine.getOptionValue("apikey");
        final int plug = Integer.parseInt(plugString);
        final long hot = Integer.parseInt(hotString);
        final long cold = Integer.parseInt(coldString);
        final String alias = commandLine.getOptionValue("alias");
        final String host = commandLine.getOptionValue("host");

        _initialize(apikey);

        Boolean running = true;
        while (running)
        {
            float celsius = climateData();
            long fahrenheit = CelsiusConvertToFahrenheit(celsius);
            if (fahrenheit > hot)
            {
                kill(alias, host, plug);
            }
            else if (fahrenheit < cold)
            {
                on(alias, host, plug);
            }
            Thread.sleep(1000);
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

            _climatewebservice = new ClimateWebserviceApi(_apiClient);

        }
    }

    public static void kill(String alias, String host, int plug) throws Exception
    {
        if (alias != null)
        {
            Runtime.getRuntime().exec("kasa --strip --alias "  + alias +  " off --index "  + Integer.toString(plug -1));
        }
        else if (host != null)
        {
            Runtime.getRuntime().exec("kasa --strip --host "  + host +  " off --index "  + Integer.toString(plug -1));
        }
    }

    public static void on(String alias, String host, int plug) throws Exception
    {
        if (alias != null)
        {
            Runtime.getRuntime().exec("kasa --strip --alias "  + alias +  " on --index "  + Integer.toString(plug -1));
        }
        else if (host != null)
        {
            Runtime.getRuntime().exec("kasa --strip --host "  + host +  " on --index "  + Integer.toString(plug -1));
        }
    }

    public static float climateData() throws Exception
    {
        float celsius = 0;
        ClimateGetMinimalClimateStatesWSRequest climateRequest = new ClimateGetMinimalClimateStatesWSRequest();
        ClimateGetMinimalClimateStatesWSResponse climateResponse = _climatewebservice.getMinimalClimateStateList(climateRequest);
        final List<ClimateMinimalClimateStateType> data = climateResponse.getClimateStates();
        for( ClimateMinimalClimateStateType temp : data)
        {
            celsius = temp.getTemperatureCelcius();
        }
        return celsius;
    }

    public static long CelsiusConvertToFahrenheit(float celsius)
    {
        long fahrenheit = Math.round((celsius * 1.8) + 32);
        return fahrenheit;
    }
}
