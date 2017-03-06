package com.example;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;

import javax.servlet.http.HttpServletResponse;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.gargoylesoftware.htmlunit.DefaultCredentialsProvider;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.util.Cookie;

/**
 * A test that sends a number of concurrent requests for the same session
 * to the test application. The expectation is that all requests are successful.
 */
public class ConcurrentRequestsIT {
	
	private static final URL APP_URL;
	private static final int THREADS = 20;
	private static final int ITERATIONS = 100;
	
	private static final List<String> ERROR_MESSAGES = Collections.synchronizedList(new ArrayList<>());
	private static final AtomicInteger SUCCESSFUL_CALLS = new AtomicInteger();

	static {
		try {
			APP_URL = new URL("http://localhost:8080/demo/hello");
		} catch (MalformedURLException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Executes the test.
	 */
	@Test
	public void perform() {
		// log in to the application and get the session cookie
		WebClient login = createWebClient();

		String sessionID = null;
		Optional<Cookie> current;
		// grab a reliable session cookie value:
		// for reasons unknown, and potentially related to Spring, the
		// initially returned cookie is sometimes not accepted and a
		// new JSESSIONID is generated
		// we need this stable, so all concurrent requests use the same
		// JSESSIONID
		while (true) {
			current = sendRequest.apply(login);
			Assertions.assertThat(current.isPresent()).isTrue();
			final String currentID = current.get().getValue();
			if (currentID.equals(sessionID)) {
				break;
			}
			sessionID = currentID;
		}

		final Cookie sessionCookie = current.get();

		final ExecutorService service = Executors.newFixedThreadPool(THREADS);
		SUCCESSFUL_CALLS.set(0);

		// run a number of threads...
		IntStream
			.range(0, THREADS)
			.forEach( i -> {
				final WebClient client = createWebClient();
				client.getCookieManager().addCookie(sessionCookie);
				service.submit( () -> {
					// ...sending a number of requests per thread
					IntStream
						.range(0, ITERATIONS)
						.forEach( j -> {
							final Optional<Cookie> cookie = sendRequest.apply(client);
							// for brevity we use a side effect here to log the error
							if (cookie.isPresent()) {
								if (!sessionCookie.equals(cookie.get())) {
									addError("unexpected cookie");
								}
							}
						} );
				});
			});

		// wait for all threads to end
		try {
			service.shutdown();
			service.awaitTermination(15, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			System.err.println("interrupted, shutting down now");
		} finally {
			if (!service.isTerminated()) {
				service.shutdownNow();
			}
		}
		
		// ensure that no issues were found
		System.out.printf("%d successful calls, and %d unsuccessful calls%n", SUCCESSFUL_CALLS.get(), ERROR_MESSAGES.size());
		Assertions.assertThat(ERROR_MESSAGES).isEmpty();
	}

	/**
	 * Sets up a client for authentication.
	 * @return a new web client, never <code>null</code>.
	 */
	private WebClient createWebClient() {
		final DefaultCredentialsProvider userCredentials = new DefaultCredentialsProvider();
		userCredentials.addCredentials("user123", "pass123");
		final WebClient webClient = new WebClient();
		webClient.setCredentialsProvider(userCredentials);
		webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
		return webClient;
	}

	/**
	 * Sends a request to the application and returns the session cookie.
	 */
	private final Function<WebClient, Optional<Cookie>> sendRequest = client -> {
		try {
			final WebRequest request = new WebRequest(APP_URL);
			final WebResponse response = client.loadWebResponse(request);
			final int sc = response.getStatusCode();
			if (sc == HttpServletResponse.SC_OK) {
				SUCCESSFUL_CALLS.incrementAndGet();
				return client
					.getCookies(APP_URL)
					.stream()
					.filter( cookie -> "JSESSIONID".equals(cookie.getName()) )
					.findFirst();
			}
			// for brevity we use a side effect here
			addError(response.getStatusCode() + " " + response.getStatusMessage());
			return Optional.empty();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	};

	private static void addError(final String message) {
		ERROR_MESSAGES.add(new SimpleDateFormat("yyy-MM-dd HH:mm:ss.SSS").format(new Date()) + " " + message);
	}

}
