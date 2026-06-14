package com.daygenie.controller;

import com.daygenie.config.JwtUtil;
import com.daygenie.model.User;
import com.daygenie.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authManager;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    /** POST /api/auth/register */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        try {
            User user = userService.register(
                body.get("username"),
                body.get("email"),
                body.get("password"),
                body.getOrDefault("defaultLocation", "Kolkata, India")
            );
            String token = jwtUtil.generateToken(user.getUsername());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "token", token,
                "username", user.getUsername(),
                "email", user.getEmail()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** POST /api/auth/login */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        try {
            authManager.authenticate(
                new UsernamePasswordAuthenticationToken(body.get("username"), body.get("password")));
            UserDetails ud = userService.loadUserByUsername(body.get("username"));
            String token = jwtUtil.generateToken(ud.getUsername());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "token", token,
                "username", ud.getUsername()
            ));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Invalid credentials"));
        }
    }
}
