package com.fitcore.gym.security;

import com.fitcore.gym.exception.BadRequestException;

import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import tools.jackson.databind.json.JsonMapper;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

/** Explicit tenancy/security selectors are invalid input, including nested selectors. */
@ControllerAdvice
public class TenantWriteGuard extends RequestBodyAdviceAdapter {
    private static final Set<String> FORBIDDEN =
            Set.of(
                    "gym",
                    "gymid",
                    "tenant",
                    "tenantid",
                    "owner",
                    "ownerid",
                    "primaryowner",
                    "primaryowneruserid",
                    "authzversion",
                    "authorizationversion",
                    "passwordhash",
                    "credentialsversion",
                    "platformrole",
                    "platformprivileges",
                    "platformpermissions");
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Override
    public boolean supports(MethodParameter p, Type t, Class<? extends HttpMessageConverter<?>> c) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage input,
            MethodParameter p,
            Type t,
            Class<? extends HttpMessageConverter<?>> c)
            throws IOException {
        byte[] body = input.getBody().readNBytes(1_048_577);
        if (body.length > 1_048_576) throw new BadRequestException("Request body is too large");
        MediaType media = input.getHeaders().getContentType();
        if (media != null
                && (media.isCompatibleWith(MediaType.APPLICATION_JSON)
                        || MediaType.valueOf("application/*+json").isCompatibleWith(media))) {
            Object value;
            try {
                value = mapper.readValue(body, Object.class);
            } catch (RuntimeException invalid) {
                throw new BadRequestException("Invalid JSON");
            }
            if ((p.getParameterType().getSimpleName().equals("OwnerRegistrationRequest")
                            || p.getParameterType().getSimpleName().equals("RegisterRequest"))
                    && value instanceof Map<?, ?> map
                    && map.containsKey("role"))
                throw new BadRequestException("Role is not assignable");
            java.util.Set<String> allowed =
                    switch (p.getParameterType().getSimpleName()) {
                        case "OwnerRegistrationRequest" ->
                                Set.of("fullName", "email", "password", "gymName", "slug");
                        case "GymUpdateRequest" -> Set.of("name");
                        case "InvitationRequest" -> Set.of("email", "role");
                        default -> null;
                    };
            if (allowed != null
                    && value instanceof Map<?, ?> object
                    && !allowed.containsAll(object.keySet()))
                throw new BadRequestException("Unknown onboarding field");
            validate(value);
        }
        return new HttpInputMessage() {
            public InputStream getBody() {
                return new ByteArrayInputStream(body);
            }

            public HttpHeaders getHeaders() {
                return input.getHeaders();
            }
        };
    }

    private void validate(Object value) {
        if (value instanceof Map<?, ?> map)
            for (var entry : map.entrySet()) {
                String key =
                        entry.getKey()
                                .toString()
                                .replace("_", "")
                                .replace("-", "")
                                .toLowerCase(Locale.ROOT);
                if (FORBIDDEN.contains(key))
                    throw new BadRequestException("Tenant and security fields are not assignable");
                validate(entry.getValue());
            }
        else if (value instanceof Collection<?> list) list.forEach(this::validate);
    }
}
