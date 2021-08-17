package com.rhombus.api.examples;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.ws.rs.client.ClientBuilder;

import com.rhombus.sdk.domain.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rhombus.ApiClient;
import com.rhombus.sdk.EventWebserviceApi;
import com.rhombus.sdk.VideoWebserviceApi;

public class EmbedRhombusFootageInExternalApplication {
	private static ApiClient _apiClient;
	private static HttpClient _mediaClient;

	public static void main(String[] args) throws Exception {
		final Options options = new Options();
		options.addRequiredOption("a", "apikey", true, "API Key");
		options.addRequiredOption("t", "timestamp", true, "Time since epoch of start of Clip");
		options.addRequiredOption("d", "duration", true, "Duration of clip to create");
		options.addRequiredOption("u", "cameraUuid", true, "uuid of target camera");
		options.addOption("url", "getMediaUrls", false, "Prints URLs for Thumbnail and MP4 download");
		options.addOption("share", "getShareUrl", false, "Prints URL for Sharing clip");
		options.addOption("download", "downloadClipMedia", false, "Downloads Thumbnail and Video");

		final CommandLine commandLine;
		try {
			commandLine = new DefaultParser().parse(options, args);
		} catch (ParseException e) {
			System.err.println(e.getMessage());

			new HelpFormatter().printHelp(
					"java -cp rhombus-api-examples-all.jar com.rhombus.api.examples.EmbedRhombusFootageInExternalApplication", options);
			return;
		}

		final String apiKey = commandLine.getOptionValue("apikey");
		final long timestamp = Long.parseLong(commandLine.getOptionValue("timestamp"));
		final int duration = Integer.parseInt(commandLine.getOptionValue("duration"));
		final List<String> cameraUuid = Arrays.asList(commandLine.getOptionValue("cameraUuid").split(","));
		final Boolean getMediaUrls = commandLine.hasOption("getMediaUrls");
		final Boolean getShareUrl = commandLine.hasOption("getShareUrl");
		final Boolean downloadClipMedia = commandLine.hasOption("downloadClipMedia");

		if (getMediaUrls || getShareUrl || downloadClipMedia) {
			_initialize(apiKey);

			final String clipUuid = generateClip(cameraUuid, timestamp, duration);
			final String[] shareInfo = shareClip(clipUuid);
			System.out.println("Share Url: " + shareInfo[0]);

			if (getMediaUrls) {
				System.out.println("Thumbnail URL: " + getThumbnailUrl(clipUuid));
				System.out.println("Video URL: " + getVideoUrl(clipUuid));
			}
			if (downloadClipMedia) {
				downloadMedia(shareInfo[1]);
			}
		} else {
			System.out.println("At least one of url, share, or download must be selected");
		}
	}

	private static void _initialize(final String apiKey) throws Exception {
		/*
		 * API CLIENT
		 */
		{
			final ClientBuilder clientBuilder = ClientBuilder.newBuilder();
			clientBuilder
					.register(new JacksonJaxbJsonProvider().configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
							.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));

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
		}

		/*
		 * MEDIA/VIDEO CLIENT
		 */
		{
			final HttpClientBuilder httpClientBuilder = HttpClients.custom();

			final HostnameVerifier hostnameVerifier = new HostnameVerifier() {
				@Override
				public boolean verify(String hostname, SSLSession session) {
					return true;
				}
			};

			_mediaClient = httpClientBuilder
					.setSSLHostnameVerifier(hostnameVerifier).setDefaultHeaders(Arrays.asList(new BasicHeader[]{
							new BasicHeader("x-auth-scheme", "api-token"), new BasicHeader("x-auth-apikey", apiKey)}))
					.build();
		}
	}

	public static String generateClip(final List<String> deviceUuids, final long startTimeMillis, final int durationSec)
			throws Exception {
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

		for (int attempts = 0; attempts < 10 && savedClip == null; attempts++) {
			savedClip = eventWebservice.getSavedClipDetails(getSavedClipDetailsRequest).getSavedClip();

			if (savedClip == null) {
				System.out.println("Waiting for clip to finish uploading...");
				Thread.sleep(10 << attempts);
			}
		}
		return clipUuid;
	}

	public static String[] shareClip(final String clipUuid) throws Exception {
		final EventWebserviceApi eventWebservice = new EventWebserviceApi(_apiClient);

		final EventCreateSharedClipGroupWSRequestRuuidWrapper ruuidWrapper = new EventCreateSharedClipGroupWSRequestRuuidWrapper();
		ruuidWrapper.setEventUuid(clipUuid);

		final EventCreateSharedClipGroupWSRequest createSharedClipRequest = new EventCreateSharedClipGroupWSRequest();
		createSharedClipRequest.setDescription("Rhombus Clip");
		createSharedClipRequest.setTitle("Rhombus Clip");
		createSharedClipRequest.setUuids(Collections.singletonList(ruuidWrapper));
		final EventCreateSharedClipGroupWSResponse createSharedClipResponse = eventWebservice.createSharedClipGroupV3(createSharedClipRequest);
		final String shareUrl = createSharedClipResponse.getShareUrl();
		final String shareUuid = createSharedClipResponse.getUuid();

		String[] shareInfo = new String[]{shareUrl, shareUuid};
		return shareInfo;
	}

	public static String getThumbnailUrl(final String clipUuid) throws Exception {
		//https://media.rhombussystems.com/media/metadata/us-west-2/CLIPUUID.jpeg
		String thumbnailUrl = "https://media.rhombussystems.com/media/metadata/us-west-2/" + clipUuid + ".jpeg";
		return thumbnailUrl;
	}

	public static String getVideoUrl(final String clipUuid) throws Exception {
		//https://media.rhombussystems.com/media/metadata/us-west-2/CLIPUUID.mp4
		String videoUrl = "https://media.rhombussystems.com/media/metadata/us-west-2/" + clipUuid + ".mp4";
		return videoUrl;
	}

	public static void downloadMedia(final String shareUuid) throws Exception {
		final String clipUrl = "https://media.rhombussystems.com" + "/media/clips/share/" + shareUuid
				+ ".mp4?x-auth-scheme=share-raw&x-share-type=clip&x-share-id="
				+ shareUuid;

		HttpGet videoRequest = new HttpGet(clipUrl);

		HttpResponse videoResponse = _mediaClient.execute(videoRequest);

		HttpEntity videoEntity = videoResponse.getEntity();

		InputStream videoStream = videoEntity.getContent();

		FileOutputStream videoOutput = new FileOutputStream(new File(shareUuid + "_Video.mp4"));
		int inByteVideo;
		while ((inByteVideo = videoStream.read()) != -1) {
			videoOutput.write(inByteVideo);
		}
		videoOutput.close();
	}
}