package com.demo.user.controller;

import com.demo.user.dto.AuthResponse;
import com.demo.user.dto.LoginRequest;
import com.demo.user.dto.RegisterRequest;
import com.demo.user.dto.UserResponse;
import com.demo.user.model.User;
import com.demo.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request);
        return UserResponse.from(user);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }
}
