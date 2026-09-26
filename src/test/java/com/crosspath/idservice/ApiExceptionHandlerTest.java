package com.crosspath.idservice;

import com.crosspath.idservice.domain.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ApiExceptionHandlerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrationService registrationService;

    @Test
    void postWithoutAuthReturns401() throws Exception {
        mockMvc.perform(post("/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request_id\":\"78ea85f0-09b5-4b1b-b830-2fcb29711601\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getWithoutAuthReturns401() throws Exception {
        mockMvc.perform(get("/v1/registrations/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postWithBadContentTypeReturns400() throws Exception {
        mockMvc.perform(post("/v1/registrations")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUnknownPathReturns404() throws Exception {
        mockMvc.perform(get("/v1/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        mockMvc.perform(get("/v1/registrations"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void errorResponseHasNoStoreCacheControl() throws Exception {
        mockMvc.perform(post("/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request_id\":\"78ea85f0-09b5-4b1b-b830-2fcb29711601\"}"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

}