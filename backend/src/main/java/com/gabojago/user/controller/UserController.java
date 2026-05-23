package com.tripmate.backend.user.controller;

import com.tripmate.backend.user.dto.MeResponse;
import com.tripmate.backend.user.dto.UpdateProfileRequest;
import com.tripmate.backend.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<MeResponse> getMe(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(userService.getMe(userId));
    }

    @PatchMapping("/profile")
    public ResponseEntity<Void> updateProfile(@AuthenticationPrincipal String userId,
                                               @Valid @RequestBody UpdateProfileRequest req) {
        userService.updateNickname(userId, req.nickname());
        return ResponseEntity.noContent().build();
    }
}
