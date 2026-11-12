package com.mocktest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test for the Spring Boot application, run with the "test" profile.
 */
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

	/**
	 * Checks that the Spring application context starts without errors. The
	 * test body is intentionally empty: it fails only if the context cannot
	 * load, for example because of a missing bean or a configuration error.
	 */
	@Test
	void contextLoads() {
	}

}