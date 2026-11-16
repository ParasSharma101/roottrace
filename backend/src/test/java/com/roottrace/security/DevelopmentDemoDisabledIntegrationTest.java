package com.roottrace.security;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "roottrace.demo.enabled=false")
@ActiveProfiles("dev")
class DevelopmentDemoDisabledIntegrationTest extends DemoAccessDisabledTestSupport {
}
