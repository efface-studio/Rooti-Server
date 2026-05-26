package com.rooti;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RootiApplicationTests {

    /** Smoke test: app context starts cleanly with the test profile. */
    @Test
    void contextLoads() {}
}
