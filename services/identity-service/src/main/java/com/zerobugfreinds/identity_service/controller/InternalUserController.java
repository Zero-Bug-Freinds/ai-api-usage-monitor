package com.zerobugfreinds.identity_service.controller;

import com.zerobugfreinds.identity_service.common.ApiResponse;
import com.zerobugfreinds.identity_service.dto.InternalUserIdsExistenceRequest;
import com.zerobugfreinds.identity_service.dto.InternalUserIdsExistenceResponse;
import com.zerobugfreinds.identity_service.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {
    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/exists")
    public ResponseEntity<ApiResponse<Boolean>> existsByEmail(@RequestParam("email") String email) {
        boolean exists = userService.existsByEmail(email);
        return ResponseEntity.ok(ApiResponse.ok("사용자 존재 여부 조회에 성공했습니다", exists));
    }

    @PostMapping("/exists/user-ids")
    public ResponseEntity<ApiResponse<InternalUserIdsExistenceResponse>> existsByUserIds(
            @RequestBody(required = false) InternalUserIdsExistenceRequest request
    ) {
        List<String> userIds = request == null || request.userIds() == null ? List.of() : request.userIds();
        Set<String> existingUserIds = userService.findExistingUserIds(userIds);
        InternalUserIdsExistenceResponse response = new InternalUserIdsExistenceResponse(existingUserIds.stream().toList());
        return ResponseEntity.ok(ApiResponse.ok("사용자 ID 존재 여부 조회에 성공했습니다", response));
    }
}
