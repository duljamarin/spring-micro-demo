package com.demo.user.service;

import com.demo.user.dto.AuthResponse;
import com.demo.user.dto.LoginRequest;
import com.demo.user.dto.RegisterRequest;
import com.demo.user.exception.InvalidCredentialsException;
import com.demo.user.exception.UserNotFoundException;
import com.demo.user.exception.UsernameAlreadyExistsException;
import com.demo.user.model.User;
import com.demo.user.repository.UserRepository;
import com.demo.user.security.JwtTokenProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException(request.username());
        }
        User user = new User(request.username(), request.email(), passwordEncoder.encode(request.password()));
        return userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, jwtTokenProvider.expirationSeconds(), user.getId(), user.getUsername());
    }

    public User findById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }
}
