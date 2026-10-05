package com.usjt.sistema_automatizado.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.usjt.sistema_automatizado.dto.request.PushTokenRequest;
import com.usjt.sistema_automatizado.dto.response.PushTokenResponse;
import com.usjt.sistema_automatizado.exception.GlobalExceptionHandler;
import com.usjt.sistema_automatizado.model.enums.PushPlatform;
import com.usjt.sistema_automatizado.service.NotificationService;
import com.usjt.sistema_automatizado.service.PushTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationController - Adversarial & Edge Case Tests")
class NotificationControllerAdversarialTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private PushTokenService pushTokenService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        NotificationController controller = new NotificationController(notificationService, pushTokenService);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                String header = webRequest.getHeader("X-User-Id");
                return header != null ? Long.parseLong(header) : 100L;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Boundary: Token with exact 512 characters succeeds with 201 Created")
    void registerToken_Exact512Chars_Succeeds() throws Exception {
        String exact512Token = "a".repeat(512);
        PushTokenRequest request = new PushTokenRequest(exact512Token, PushPlatform.WEB);
        PushTokenResponse response = new PushTokenResponse(1L, 100L, exact512Token, PushPlatform.WEB, LocalDateTime.now(), null);

        when(pushTokenService.registerToken(eq(100L), any(PushTokenRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/notifications/push-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(exact512Token));

        verify(pushTokenService, times(1)).registerToken(eq(100L), any(PushTokenRequest.class));
    }

    @Test
    @DisplayName("Boundary: Token with 513 characters violates @Size(max=512) and returns 400 Bad Request")
    void registerToken_513Chars_FailsValidation() throws Exception {
        String oversizedToken = "a".repeat(513);
        String json = String.format("""
                {
                    "token": "%s",
                    "platform": "ANDROID"
                }
                """, oversizedToken);

        mockMvc.perform(post("/api/v1/notifications/push-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verify(pushTokenService, never()).registerToken(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"token\": \"\", \"platform\": \"ANDROID\"}",
            "{\"token\": \"   \", \"platform\": \"ANDROID\"}",
            "{\"token\": null, \"platform\": \"ANDROID\"}",
            "{\"platform\": \"ANDROID\"}"
    })
    @DisplayName("Validation: Missing, null, or blank tokens return 400 Bad Request")
    void registerToken_BlankOrMissingTokens_Return400(String payload) throws Exception {
        mockMvc.perform(post("/api/v1/notifications/push-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        verify(pushTokenService, never()).registerToken(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"token\": \"valid-token-abc\", \"platform\": null}",
            "{\"token\": \"valid-token-abc\"}",
            "{\"token\": \"valid-token-abc\", \"platform\": \"WINDOWS_PHONE\"}",
            "{\"token\": \"valid-token-abc\", \"platform\": \"SYMBIAN\"}",
            "{\"token\": \"valid-token-abc\", \"platform\": \"123\"}"
    })
    @DisplayName("Validation: Null, missing, or unrecognized platforms return 400 Bad Request")
    void registerToken_InvalidPlatforms_Return400(String payload) throws Exception {
        mockMvc.perform(post("/api/v1/notifications/push-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        verify(pushTokenService, never()).registerToken(any(), any());
    }

    @Test
    @DisplayName("Malformed JSON payload returns 400 Bad Request")
    void registerToken_MalformedJson_Returns400() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/push-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ broken-json: true }"))
                .andExpect(status().isBadRequest());

        verify(pushTokenService, never()).registerToken(any(), any());
    }

    @Test
    @DisplayName("Revoke token with URL-encoded special characters succeeds with 204")
    void revokeToken_SpecialCharsUrlEncoded_Succeeds() throws Exception {
        String tokenWithSpecialChars = "fcm:token-part_123.abc";

        mockMvc.perform(delete("/api/v1/notifications/push-tokens/{token}", tokenWithSpecialChars))
                .andExpect(status().isNoContent());

        verify(pushTokenService, times(1)).revokeToken(100L, tokenWithSpecialChars);
    }
}
