package in.srmist.revaluation.controller;

import in.srmist.revaluation.dto.Views.ApplicationCreated;
import in.srmist.revaluation.service.ApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer only (@WebMvcTest): request validation and JSON shapes of POST /api/applications.
 * ApplicationService is a mock, so Camunda and the database are not involved.
 */
@WebMvcTest(ApplicationController.class)
class ApplicationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ApplicationService service;

    @Test
    void validApplicationReturns201WithApplicationId() throws Exception {
        when(service.submit(any())).thenReturn(new ApplicationCreated("APP-261118080558997", 2251799813685249L));

        mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content("""
                        {"registerNo":"RA2411026011139","studentName":"Ram Prasath N",
                         "studentEmail":"ram@example.com","courses":["21CSC305P","21CSE251T"]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId").value("APP-261118080558997"));
    }

    @Test
    void invalidFieldsReturn400WithOneMessagePerField() throws Exception {
        mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content("""
                        {"registerNo":"RA24","studentName":"Ram","studentEmail":"ram@","courses":[]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.registerNo").exists())
                .andExpect(jsonPath("$.fieldErrors.studentEmail").exists())
                .andExpect(jsonPath("$.fieldErrors.courses").value("add at least one course"));
        verify(service, never()).submit(any());   // no process instance is started
    }

    @Test
    void badCourseCodeIsRejected() throws Exception {
        mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content("""
                        {"registerNo":"RA2411026011139","studentName":"Ram","studentEmail":"ram@example.com",
                         "courses":["CSC305"]}"""))
                .andExpect(status().isBadRequest());
    }
}
