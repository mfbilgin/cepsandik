package com.cepsandik.userservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Tüm Spring bağlamını kurar: PostgreSQL (localhost:5433), Redis ve RabbitMQ
 * çalışıyor olmalı. Yerel stack ayaktayken: RUN_INTEGRATION_TESTS=true mvnw test
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class UserServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
