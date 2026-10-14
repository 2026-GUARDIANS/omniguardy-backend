package com.omniguardy.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("로컬 MySQL/Firebase/MQTT가 필요한 기존 환경 의존 smoke test")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
