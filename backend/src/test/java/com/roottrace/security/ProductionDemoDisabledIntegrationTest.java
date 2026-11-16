package com.roottrace.security;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Even an accidental dev profile and an enabled property must not expose demos in prod.
@SpringBootTest(properties = "roottrace.demo.enabled=true")
@ActiveProfiles({"dev", "prod"})
class ProductionDemoDisabledIntegrationTest extends DemoAccessDisabledTestSupport {
}
