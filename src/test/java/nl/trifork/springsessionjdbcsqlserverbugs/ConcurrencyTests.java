package nl.trifork.springsessionjdbcsqlserverbugs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ConcurrencyTests {

	@Container
	static MSSQLServerContainer sqlServer = new MSSQLServerContainer();

	@DynamicPropertySource
	static void sqlServerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", sqlServer::getJdbcUrl);
		registry.add("spring.datasource.username", sqlServer::getUsername);
		registry.add("spring.datasource.password", sqlServer::getPassword);
	}

	@Autowired TestRestTemplate restTemplate;

	@Test
	void parallelSessionCreation() throws InterruptedException, ExecutionException {
		// every request will trigger the creation of a new Session _with_ a session attribute,
		// triggering a deadlock b/o the way the INSERT uses a subselect to find the session PK
		// based on the session ID via the unique index.
		makeCalls(10, () -> restTemplate.getForEntity("/", String.class));
	}

	@Test
	void parallelSessionUpdates() throws InterruptedException, ExecutionException {
		// this request will trigger the creation of a new Session
		ResponseEntity<String> firstResponse = restTemplate.getForEntity("/", String.class);
		HttpHeaders headers = new HttpHeaders();
		headers.add("Cookie", firstResponse.getHeaders().getFirst("Set-Cookie"));

		// now update this session concurrently by adding a second attribute. Multiple threads will think
		// that the need to INSERT the new attribute, causing primary key constraint violations
		makeCalls(10, () -> restTemplate.exchange("/?secondAttr=true", HttpMethod.GET, new HttpEntity<>(headers), String.class));
	}

	void makeCalls(int concurrency, Callable<ResponseEntity<String>> callable) throws InterruptedException, ExecutionException {
		ExecutorService pool = Executors.newFixedThreadPool(concurrency);
		List<Future<ResponseEntity<String>>> results = new ArrayList<>();
		for (int i = 0; i < concurrency; i++) {
			results.add(pool.submit(callable));
		}
		pool.shutdown();
		pool.awaitTermination(1, TimeUnit.MINUTES);
		for (Future<ResponseEntity<String>> result : results) {
			ResponseEntity<String> response = result.get();
			if (response.getStatusCode() != HttpStatus.OK) {
				fail(response.getBody());
			}
		}
	}
}
