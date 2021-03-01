package com.rhombus.api.examples;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.ws.rs.client.ClientBuilder;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rhombus.ApiClient;
import com.rhombus.sdk.CameraWebserviceApi;
import com.rhombus.sdk.OrgWebserviceApi;
import com.rhombus.sdk.domain.CameraGetMediaUrisWSRequest;
import com.rhombus.sdk.domain.CameraGetMediaUrisWSResponse;
import com.rhombus.sdk.domain.OrgGenerateFederatedSessionTokenRequest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

public class CopyFootageToLocalStorage
{
	private static HttpClient _videoClient;
	private static ApiClient _apiClient;

	private static final Logger _logger = LoggerFactory.getLogger(CopyFootageToLocalStorage.class);

	public static void main(final String[] args) throws Exception
	{
		final Options options = new Options();
		options.addRequiredOption("a", "apikey", true, "API Key");
		options.addRequiredOption("d", "deviceId", true, "The Device Id to download from");
		options.addRequiredOption("o", "output", true, "The MP4 file to write to");
		options.addOption("k", "keystore", true, "P12 Keystore File (if cert-based key is used)");
		options.addOption("p", "password", true, "P12 password (if cert-based key is used)");
		options.addOption("s", "starttime", true, "Start Time in epoch seconds");
		options.addOption("u", "duration", true, "Duration in seconds");
		options.addOption("g", "debug", true, "Debug Mode");

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

		if(commandLine.hasOption("debug"))
		{
			((LoggerContext) LoggerFactory.getILoggerFactory())
					.getLogger("com.rhombus.api.examples.CopyFootageToLocalStorage").setLevel(Level.DEBUG);
		}

		final String apiKey = commandLine.getOptionValue("apikey");
		final String keystoreFile = commandLine.getOptionValue("keystore");
		final String deviceUuid = commandLine.getOptionValue("deviceId");
		final String outputFile = commandLine.getOptionValue("output");

		char[] keystorePassword = null;
		if(commandLine.hasOption("password"))
		{
			keystorePassword = commandLine.getOptionValue("password").toCharArray();
		}

		final long startTimeSec;

		if(commandLine.hasOption("starttime"))
		{
			startTimeSec = Long.parseLong(commandLine.getOptionValue("starttime"));
		}
		else
		{
			final Calendar startCal = Calendar.getInstance();
			startCal.add(Calendar.HOUR_OF_DAY, -1);

			startTimeSec = TimeUnit.MILLISECONDS.toSeconds(startCal.getTimeInMillis());
		}

		final long durationSec;
		if(commandLine.hasOption("duration"))
		{
			durationSec = Long.parseLong(commandLine.getOptionValue("duration"));
		}
		else
		{
			durationSec = TimeUnit.HOURS.toSeconds(1);
		}

		final String authScheme;
		if(commandLine.hasOption("keystore"))
		{
			authScheme = "api";
		}
		else
		{
			authScheme = "api-token";
		}

		_logger.info("Initializing API Key [" + apiKey + "] and Keystore [" + keystoreFile + "]");

		_initialize(authScheme, apiKey, keystoreFile, keystorePassword);

		_logger.info("Copying Footage from Camera [" + deviceUuid + "] to [" + outputFile + "] for the period ["
				+ new Date(TimeUnit.SECONDS.toMillis(startTimeSec)) + "] to ["
				+ new Date(TimeUnit.SECONDS.toMillis(startTimeSec + durationSec)) + "]");

		try (final FileOutputStream outputStream = new FileOutputStream(outputFile))
		{
			_downloadVodFootageLAN(deviceUuid, startTimeSec, durationSec, outputStream);
		}

		_logger.info("Copy Complete!");
	}

	private static void _initialize(final String authScheme, final String apiKey, final String keystoreFile,
			final char[] keystorePassword) throws Exception
	{
		/*
		 * API CLIENT
		 */
		{
			final List<Header> defaultHeaders = new ArrayList<>();
			defaultHeaders.add(new BasicHeader("x-auth-scheme", authScheme));
			defaultHeaders.add(new BasicHeader("x-auth-apikey", apiKey));

			final ClientBuilder clientBuilder = ClientBuilder.newBuilder();
			clientBuilder.register(JacksonFeature.class);

			if(keystoreFile != null)
			{
				final KeyStore keyStore = KeyStore.getInstance("PKCS12");

				try (final FileInputStream fileInputStream = new FileInputStream(keystoreFile))
				{
					keyStore.load(fileInputStream, keystorePassword);
				}

				final SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(new TrustStrategy()
				{
					@Override
					public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException
					{
						return true;
					}
				}).loadKeyMaterial(keyStore, keystorePassword).setProtocol("TLSv1.2").build();

				clientBuilder.sslContext(sslContext);
			}

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
			_apiClient.addDefaultHeader("x-auth-scheme", authScheme);
			_apiClient.addDefaultHeader("x-auth-apikey", apiKey);
		}

		/*
		 * MEDIA/VIDEO CLIENT
		 */
		{
			final HttpClientBuilder httpClientBuilder = HttpClients.custom();

			if(keystoreFile != null)
			{
				final KeyStore keyStore = KeyStore.getInstance("PKCS12");
				keyStore.load(new FileInputStream(keystoreFile), keystorePassword);

				final SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(new TrustStrategy()
				{
					@Override
					public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException
					{
						return true;
					}
				}).loadKeyMaterial(keyStore, keystorePassword).setProtocol("TLSv1.2").build();

				httpClientBuilder.setSSLContext(sslContext);
			}

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

	private static void _downloadVodFootageLAN(final String deviceUuid, final Long startTimeSec, final Long durationSec,
			final OutputStream outputStream) throws Exception
	{
		final CameraWebserviceApi cameraWebservice = new CameraWebserviceApi(_apiClient);
		final OrgWebserviceApi orgWebservice = new OrgWebserviceApi(_apiClient);

		final OrgGenerateFederatedSessionTokenRequest generateFederatedSessionTokenRequest = new OrgGenerateFederatedSessionTokenRequest();
		generateFederatedSessionTokenRequest.setDurationSec((int) TimeUnit.MINUTES.toSeconds(60));
		final String federatedSessionToken = orgWebservice
				.generateFederatedSessionToken(generateFederatedSessionTokenRequest).getFederatedSessionToken();

		final CameraGetMediaUrisWSRequest getMediaUrisRequest = new CameraGetMediaUrisWSRequest();
		getMediaUrisRequest.setCameraUuid(deviceUuid);
		final CameraGetMediaUrisWSResponse getMediaUrisResponse = cameraWebservice.getMediaUris(getMediaUrisRequest);

		_logger.info("Mpd URI: " + getMediaUrisResponse.getLanVodMpdUrisTemplates());

		final String mdpUri = getMediaUrisResponse.getLanVodMpdUrisTemplates().get(0)
				.replaceAll("\\{START_TIME\\}", startTimeSec.toString())
				.replaceAll("\\{DURATION\\}", durationSec.toString());

		final HttpGet mpdRequest = new HttpGet(mdpUri);
		mpdRequest.setHeader("Cookie", "RHOMBUS_SESSIONID=RFT:" + federatedSessionToken.toString());

		_logger.info("MPD Request: " + mpdRequest);

		final HttpResponse mpdResponse = _videoClient.execute(mpdRequest);

		_logger.info("MPD Response: " + mpdResponse);

		final byte[] mpdDocumentRaw = IOUtils.toByteArray(mpdResponse.getEntity().getContent());

		_logger.trace("MPD (RAW): " + new String(mpdDocumentRaw));

		{
			final String initSegmentUri = mdpUri.toString().replaceAll("clip\\.mpd", "seg_init.mp4");
			_logger.info("Init Segment URI: " + initSegmentUri);

			final HttpGet initSegmentRequest = new HttpGet(initSegmentUri);
			initSegmentRequest.setHeader("Cookie", "RHOMBUS_SESSIONID=RFT:" + federatedSessionToken.toString());

			final HttpResponse initSegmentResponse = _videoClient.execute(initSegmentRequest);

			_logger.info("Init Segment Response: " + initSegmentResponse);

			final byte[] initSegment = EntityUtils.toByteArray(initSegmentResponse.getEntity());

			_logger.trace("Init Segment: " + Hex.encodeHexString(initSegment));

			IOUtils.write(initSegment, outputStream);
		}

		final Long numSegments = durationSec / 2;

		for (int currSegment = 1; currSegment < numSegments; currSegment++)
		{
			final String segmentUri = mdpUri.toString().replaceAll("clip\\.mpd", "seg_" + currSegment + ".m4v");
			_logger.debug("Segment URI: " + segmentUri);

			final HttpGet segmentRequest = new HttpGet(segmentUri);
			segmentRequest.setHeader("Cookie", "RHOMBUS_SESSIONID=RFT:" + federatedSessionToken.toString());

			final HttpResponse segmentResponse = _videoClient.execute(segmentRequest);
			final byte[] segment = EntityUtils.toByteArray(segmentResponse.getEntity());

			IOUtils.write(segment, outputStream);

			/*
			 * BLS Log every 10 minutes
			 */
			if(currSegment % 300 == 0)
			{
				_logger.info("Segments written from ["
						+ new Date(TimeUnit.SECONDS.toMillis(startTimeSec + ((currSegment - 300) * 2))) + "] to ["
						+ new Date(TimeUnit.SECONDS.toMillis(startTimeSec + (currSegment * 2))) + "]");
			}
		}
	}
}
