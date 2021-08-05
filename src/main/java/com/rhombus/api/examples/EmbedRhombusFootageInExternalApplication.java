package com.rhombus.api.examples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.ws.rs.client.ClientBuilder;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rhombus.ApiClient;
import com.rhombus.sdk.EventWebserviceApi;
import com.rhombus.sdk.VideoWebserviceApi;
import com.rhombus.sdk.domain.EventCreateSharedClipGroupWSRequest;
import com.rhombus.sdk.domain.EventCreateSharedClipGroupWSRequestRuuidWrapper;
import com.rhombus.sdk.domain.EventGetSavedClipDetailsWSRequest;
import com.rhombus.sdk.domain.SavedClipWithDetailsType;
import com.rhombus.sdk.domain.VideoSpliceV2WSRequest;

public class EmbedRhombusFootageInExternalApplication
{
	private static ApiClient _apiClient;
	private static HttpClient _mediaClient;

	public static void main(String[] args) throws Exception
	{
		final Options options = new Options();
		options.addRequiredOption("a", "apikey", true, "API Key");
		options.addRequiredOption("c", "choice", true, "Add or remove a label [add|remove]");
		options.addRequiredOption("l", "label", true, "Label name");
		options.addRequiredOption("n", "names", true,
				"Names of people to add or remove labels from (ex: name, name, name)");

		final CommandLine commandLine;
		try
		{
			commandLine = new DefaultParser().parse(options, args);
		}
		catch (ParseException e)
		{
			System.err.println(e.getMessage());

			new HelpFormatter().printHelp(
					"java -cp rhombus-api-examples-all.jar com.rhombus.api.examples.AddRemoveLabels", options);
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
			clientBuilder
					.register(new JacksonJaxbJsonProvider().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
							.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));

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

			_mediaClient = httpClientBuilder
					.setSSLHostnameVerifier(hostnameVerifier).setDefaultHeaders(Arrays.asList(new BasicHeader[] {
							new BasicHeader("x-auth-scheme", "api-token"), new BasicHeader("x-auth-apikey", apiKey) }))
					.build();
		}
	}

	public static void generateClip(final List<String> deviceUuids, final long startTimeMillis, final int durationSec)
			throws Exception
	{
		final VideoWebserviceApi videoWebservice = new VideoWebserviceApi(_apiClient);
		final EventWebserviceApi eventWebservice = new EventWebserviceApi(_apiClient);

		final VideoSpliceV2WSRequest spliceRequest = new VideoSpliceV2WSRequest();
		spliceRequest.setDescription("Rhombus Clip");
		spliceRequest.setDeviceUuids(deviceUuids);
		spliceRequest.setDurationSec(durationSec);
		spliceRequest.setStartTimeMillis(startTimeMillis);
		spliceRequest.setTitle("Rhombus Clip");
		final String clipUuid = videoWebservice.spliceV2(spliceRequest).getClipUuid();

		final EventGetSavedClipDetailsWSRequest getSavedClipDetailsRequest = new EventGetSavedClipDetailsWSRequest();
		getSavedClipDetailsRequest.setClipUuid(clipUuid);

		SavedClipWithDetailsType savedClip = null;

		for (int attempts = 0; attempts < 10 && savedClip == null; attempts++)
		{
			savedClip = eventWebservice.getSavedClipDetails(getSavedClipDetailsRequest).getSavedClip();

			if(savedClip == null)
			{
				System.out.println("Waiting for clip to finish uploading...");
				Thread.sleep(10 << attempts);
			}
		}
	}

	public static String shareClip(final String clipUuid) throws Exception
	{
		final EventWebserviceApi eventWebservice = new EventWebserviceApi(_apiClient);

		final EventCreateSharedClipGroupWSRequestRuuidWrapper ruuidWrapper = new EventCreateSharedClipGroupWSRequestRuuidWrapper();
		ruuidWrapper.setEventUuid(clipUuid);

		final EventCreateSharedClipGroupWSRequest createSharedClipRequest = new EventCreateSharedClipGroupWSRequest();
		createSharedClipRequest.setDescription("Rhombus Clip");
		createSharedClipRequest.setTitle("Rhombus Clip");
		createSharedClipRequest.setUuids(Collections.singletonList(ruuidWrapper));

		final String shareUrl = eventWebservice.createSharedClipGroupV3(createSharedClipRequest).getShareUrl();

		return shareUrl;
	}

	public static String downloadThumbnail() throws Exception
	{
		// https://media.rhombussystems.com/media/metadata/us-west-1/s2siGm6rRB6jFC8d9wSMqA.jpeg

	}

	public static String downloadClip() throws Exception
	{

	}
}