package com.omniguardy.backend.global.config;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SwaggerTestApplication.class, properties = {
        "jwt.secret-key=swagger-test-secret-at-least-32-bytes",
        "jwt.access-token-expire-time=1800000",
        "jwt.refresh-token-expire-time=1209600000"
})
@ActiveProfiles("prod")
class SwaggerProdEndpointIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Test
    void doesNotExposeSwaggerEndpointsWhenDisabled() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }
}
