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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerPushTokenTest {

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
                return header != null ? Long.parseLong(header) : 1L;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void registerPushToken_DeveRetornar201Created_QuandoDadosValidos() throws Exception {
        PushTokenRequest request = new PushTokenRequest("fcm-token-valido-123", PushPlatform.ANDROID);
        PushTokenResponse response = new PushTokenResponse(10L, 1L, "fcm-token-valido-123", PushPlatform.ANDROID, LocalDateTime.now(), null);

        when(pushTokenService.registerToken(eq(1L), any(PushTokenRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/notifications/push-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.token").value("fcm-token-valido-123"))
                .andExpect(jsonPath("$.platform").value("ANDROID"));

        verify(pushTokenService, times(1)).registerToken(eq(1L), any(PushTokenRequest.class));
    }

    @Test
    void registerPushToken_DeveRetornar400BadRequest_QuandoTokenEmBranco() throws Exception {
        String invalidJson = """
                {
                    "token": "   ",
                    "platform": "ANDROID"
                }
                """;

        mockMvc.perform(post("/api/v1/notifications/push-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verify(pushTokenService, never()).registerToken(any(), any());
    }

    @Test
    void registerPushToken_DeveRetornar400BadRequest_QuandoPlataformaNula() throws Exception {
        String invalidJson = """
                {
                    "token": "fcm-token-valido",
                    "platform": null
                }
                """;

        mockMvc.perform(post("/api/v1/notifications/push-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verify(pushTokenService, never()).registerToken(any(), any());
    }

    @Test
    void registerPushToken_DeveRetornar400BadRequest_QuandoPlataformaInvalida() throws Exception {
        String invalidJson = """
                {
                    "token": "fcm-token-valido",
                    "platform": "DESCONHECIDA"
                }
                """;

        mockMvc.perform(post("/api/v1/notifications/push-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verify(pushTokenService, never()).registerToken(any(), any());
    }

    @Test
    void revokePushToken_DeveRetornar204NoContent_QuandoTokenInformado() throws Exception {
        String token = "fcm-token-para-remover";

        mockMvc.perform(delete("/api/v1/notifications/push-tokens/{token}", token))
                .andExpect(status().isNoContent());

        verify(pushTokenService, times(1)).revokeToken(1L, token);
    }

    @Test
    void revokePushToken_DeveRetornar204NoContent_MesmoQuandoTokenNaoExistia() throws Exception {
        String token = "fcm-token-inexistente";
        doNothing().when(pushTokenService).revokeToken(1L, token);

        mockMvc.perform(delete("/api/v1/notifications/push-tokens/{token}", token))
                .andExpect(status().isNoContent());

        verify(pushTokenService, times(1)).revokeToken(1L, token);
    }
}
