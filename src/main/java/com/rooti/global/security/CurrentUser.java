package com.rooti.global.security;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Convenience composition annotation: inject the {@link PrincipalDetails} of the caller into a
 * controller method parameter without spelling out {@code @AuthenticationPrincipal} every time.
 *
 * <pre>{@code
 * @GetMapping("/me")
 * public ApiResponse<MeResponse> me(@CurrentUser PrincipalDetails me) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal
@Parameter(hidden = true, in = ParameterIn.HEADER)
public @interface CurrentUser {}
