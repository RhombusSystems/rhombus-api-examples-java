package com.rhombus.api.examples;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.ws.rs.client.ClientBuilder;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
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
import com.rhombus.sdk.domain.CameraGetMediaUrisWSRequest;
import com.rhombus.sdk.domain.CameraGetMediaUrisWSResponse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

public class HLSViewer
{
	private static final Pattern FIRST_SEGMENT_PATTERN = Pattern.compile("^seg_(\\d*)\\.m4v$", Pattern.MULTILINE);

	private static HttpClient _videoClient;
	private static ApiClient _apiClient;

	private static final Logger _logger = LoggerFactory.getLogger(HLSViewer.class);

	public static void main(final String[] args) throws Exception
	{
		final Options options = new Options();
		options.addRequiredOption("a", "apikey", true, "API Key");
		options.addRequiredOption("d", "deviceId", true, "The Device Id to download from");
		options.addRequiredOption("o", "output", true, "The MP4 file to write to");
		options.addOption("k", "keystore", true, "P12 Keystore File (if cert-based key is used)");
		options.addOption("p", "password", true, "P12 password (if cert-based key is used)");
		options.addOption("s", "starttime", true, "Start Time in epoch seconds (defaults to live)");
		options.addOption("u", "duration", true, "Duration in seconds (defaults to 1 minute)");
		options.addOption("g", "debug", true, "Debug Mode");

		final CommandLine commandLine;
		try
		{
			commandLine = new DefaultParser().parse(options, args);
		}
		catch (ParseException e)
		{
			System.err.println(e.getMessage());

			new HelpFormatter().printHelp("java -cp rhombus-api-examples-all.jar com.rhombus.api.examples.HLSViewer",
					options);
			return;
		}

		if(commandLine.hasOption("debug"))
		{
			((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("com.rhombus.api.examples.HLSViewer")
					.setLevel(Level.DEBUG);
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

		final Long startTimeSec;
		if(commandLine.hasOption("starttime"))
		{
			startTimeSec = Long.parseLong(commandLine.getOptionValue("starttime"));
		}
		else
		{
			startTimeSec = null;
		}

		final long durationSec;
		if(commandLine.hasOption("duration"))
		{
			durationSec = Long.parseLong(commandLine.getOptionValue("duration"));
		}
		else
		{
			durationSec = TimeUnit.MINUTES.toSeconds(1);
		}

		final String authScheme;
		final Function<String, String> urlRewriter;
		if(commandLine.hasOption("keystore"))
		{
			authScheme = "api";
			urlRewriter = (originalUrl) -> originalUrl.replace(".dash.", ".dash-internal.")
					.replace("/dash/mobile/outbound", "/dash/api/outbound");
		}
		else
		{
			authScheme = "api-token";
			urlRewriter = (originalUrl) -> originalUrl.replace("/dash/mobile/outbound", "/dash/api/outbound");
		}

		_logger.info("Initializing API Key [" + apiKey + "] and Keystore [" + keystoreFile + "]");

		_initialize(authScheme, apiKey, keystoreFile, keystorePassword);

		try (final FileOutputStream outputStream = new FileOutputStream(outputFile))
		{
			if(startTimeSec == null)
			{
				_logger.info("Copying Live Footage from Camera [" + deviceUuid + "] to [" + outputFile + "] until ["
						+ new Date(TimeUnit.SECONDS.toMillis(durationSec)) + "]");

				_downloadLiveFootageWAN(deviceUuid, durationSec, outputStream, urlRewriter);
			}
			else
			{
				_logger.info("Copying Past Footage from Camera [" + deviceUuid + "] to [" + outputFile
						+ "] for the period [" + new Date(TimeUnit.SECONDS.toMillis(startTimeSec)) + "] to ["
						+ new Date(TimeUnit.SECONDS.toMillis(startTimeSec + durationSec)) + "]");

				_downloadVODFootageWAN(deviceUuid, startTimeSec, durationSec, outputStream, urlRewriter);
			}
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
			final List<Header> defaultHeaders = new ArrayList<>();
			defaultHeaders.add(new BasicHeader("x-auth-scheme", authScheme));
			defaultHeaders.add(new BasicHeader("x-auth-apikey", apiKey));

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

			_videoClient = httpClientBuilder.setSSLHostnameVerifier(hostnameVerifier).setDefaultHeaders(defaultHeaders)
					.build();
		}
	}

	private static void _downloadLiveFootageWAN(final String deviceUuid, final Long durationSec,
			final OutputStream outputStream, final Function<String, String> urlRewriter) throws Exception
	{
		final CameraWebserviceApi cameraWebservice = new CameraWebserviceApi(_apiClient);

		final CameraGetMediaUrisWSRequest getMediaUrisRequest = new CameraGetMediaUrisWSRequest();
		getMediaUrisRequest.setCameraUuid(deviceUuid);
		final CameraGetMediaUrisWSResponse getMediaUrisResponse = cameraWebservice.getMediaUris(getMediaUrisRequest);

		final String hlsUri = urlRewriter.apply(getMediaUrisResponse.getWanLiveM3u8Uri());

		_logger.debug("HLS URI: " + hlsUri);

		/*
		 * Fetch M3U8 Document
		 */
		final String m3u8Document;
		{
			final HttpGet m3u8Request = new HttpGet(hlsUri);
			final HttpResponse m3u8Response = _videoClient.execute(m3u8Request);

			if(m3u8Response.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
			{
				throw new RuntimeException("M3U8 Failure: " + m3u8Response.getStatusLine().getStatusCode());
			}

			m3u8Document = EntityUtils.toString(m3u8Response.getEntity());

		}

		_logger.debug("M3U8: " + m3u8Document);

		/*
		 * Fetch/Write init segment
		 */
		{
			final String initSegmentUri = hlsUri.replaceAll("file\\.m3u8", "seg_init.mp4");
			_logger.debug("Init Segment URI: " + initSegmentUri);

			final HttpGet initSegmentRequest = new HttpGet(initSegmentUri);
			final HttpResponse initSegmentResponse = _videoClient.execute(initSegmentRequest);

			if(initSegmentResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
			{
				throw new RuntimeException(
						"Init Segment Failure: " + initSegmentResponse.getStatusLine().getStatusCode());
			}

			final byte[] initSegment = EntityUtils.toByteArray(initSegmentResponse.getEntity());

			IOUtils.write(initSegment, outputStream);
		}

		/*
		 * Pull first segment number from M3U8
		 */
		int segmentNumber;
		{
			final Matcher matcher = FIRST_SEGMENT_PATTERN.matcher(m3u8Document);

			if(matcher.find())
			{
				segmentNumber = Integer.parseInt(matcher.group(1));
			}
			else
			{
				throw new RuntimeException("Invalid M3U8");
			}
		}

		/*
		 * Download/Write Segments
		 */
		for (int i = 0; i < (durationSec / 2); i++)
		{
			final String segmentUri = hlsUri.replaceAll("file\\.m3u8", "seg_" + segmentNumber++ + ".m4v");
			_logger.debug("Segment URI: " + segmentUri);

			final HttpGet segmentRequest = new HttpGet(segmentUri);

			final HttpResponse segmentResponse = _videoClient.execute(segmentRequest);

			if(segmentResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
			{
				throw new RuntimeException(
						"Segment[" + segmentNumber + "] Failure: " + segmentResponse.getStatusLine().getStatusCode());
			}

			final byte[] segment = EntityUtils.toByteArray(segmentResponse.getEntity());

			IOUtils.write(segment, outputStream);
		}
	}

	private static void _downloadVODFootageWAN(final String deviceUuid, final Long startTimeSec, final Long durationSec,
			final OutputStream outputStream, final Function<String, String> urlRewriter) throws Exception
	{
		final CameraWebserviceApi cameraWebservice = new CameraWebserviceApi(_apiClient);

		final CameraGetMediaUrisWSRequest getMediaUrisRequest = new CameraGetMediaUrisWSRequest();
		getMediaUrisRequest.setCameraUuid(deviceUuid);
		final CameraGetMediaUrisWSResponse getMediaUrisResponse = cameraWebservice.getMediaUris(getMediaUrisRequest);

		final String hlsUri = urlRewriter.apply(getMediaUrisResponse.getWanVodM3u8UriTemplate())
				.replace("{START_TIME}", startTimeSec.toString()).replace("{DURATION}", durationSec.toString());

		_logger.debug("HLS URI: " + hlsUri);

		/*
		 * Fetch M3U8 Document
		 */
		final String m3u8Document;
		{
			final HttpGet m3u8Request = new HttpGet(hlsUri);
			final HttpResponse m3u8Response = _videoClient.execute(m3u8Request);

			if(m3u8Response.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
			{
				throw new RuntimeException("M3U8 Failure: " + m3u8Response.getStatusLine().getStatusCode());
			}

			m3u8Document = EntityUtils.toString(m3u8Response.getEntity());

		}

		_logger.debug("M3U8: " + m3u8Document);

		/*
		 * Fetch/Write init segment
		 */
		{
			final String initSegmentUri = hlsUri.replaceAll("file\\.m3u8", "seg_init.mp4");
			_logger.debug("Init Segment URI: " + initSegmentUri);

			final HttpGet initSegmentRequest = new HttpGet(initSegmentUri);
			final HttpResponse initSegmentResponse = _videoClient.execute(initSegmentRequest);

			if(initSegmentResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
			{
				throw new RuntimeException(
						"Init Segment Failure: " + initSegmentResponse.getStatusLine().getStatusCode());
			}

			final byte[] initSegment = EntityUtils.toByteArray(initSegmentResponse.getEntity());

			IOUtils.write(initSegment, outputStream);
		}

		/*
		 * Pull first segment number from M3U8
		 */
		int segmentNumber;
		{
			final Matcher matcher = FIRST_SEGMENT_PATTERN.matcher(m3u8Document);

			if(matcher.find())
			{
				segmentNumber = Integer.parseInt(matcher.group(1));
			}
			else
			{
				throw new RuntimeException("Invalid M3U8");
			}
		}

		/*
		 * Download/Write Segments
		 */
		for (int i = 0; i < (durationSec / 2); i++)
		{
			final String segmentUri = hlsUri.replaceAll("file\\.m3u8", "seg_" + segmentNumber++ + ".m4v");
			_logger.debug("Segment URI: " + segmentUri);

			final HttpGet segmentRequest = new HttpGet(segmentUri);

			final HttpResponse segmentResponse = _videoClient.execute(segmentRequest);

			if(segmentResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
			{
				throw new RuntimeException(
						"Segment[" + segmentNumber + "] Failure: " + segmentResponse.getStatusLine().getStatusCode());
			}

			final byte[] segment = EntityUtils.toByteArray(segmentResponse.getEntity());

			IOUtils.write(segment, outputStream);
		}
	}
}
