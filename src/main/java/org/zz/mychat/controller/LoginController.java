package org.zz.mychat.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zz.mychat.common.utils.ApiResult;
import org.zz.mychat.common.utils.JwtUtils;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LoginController {

    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;

    @RequestMapping("/login")
    public ApiResult login(String username, String password) {

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            username, password
                    )
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String jwt = jwtUtils.generateToken(userDetails);
            return ApiResult.success(jwt);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("300", e.getMessage(), null);
        }
    }
}
