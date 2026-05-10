package com.zerobugfreinds.identity_service.controller;

import com.zerobugfreinds.identity_service.common.ApiResponse;
import com.zerobugfreinds.identity_service.dto.InternalUserIdsExistenceRequest;
import com.zerobugfreinds.identity_service.dto.InternalUserIdsExistenceResponse;
import com.zerobugfreinds.identity_service.service.UserService;
import org.springframework.http.HttpStatus;
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

    /**
     * team-service 등에서 숫자 id·이메일 중 무엇으로 넘겨도 동일 사용자로 매칭할 수 있도록 userId·email을 함께 반환한다.
     */
    @GetMapping("/principal")
    public ResponseEntity<ApiResponse<?>> resolvePrincipal(@RequestParam("q") String q) {
        return userService.resolvePrincipalForInternalLookup(q)
                .map(p -> ResponseEntity.<ApiResponse<?>>ok(ApiResponse.ok("사용자 식별자 조회에 성공했습니다", p)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("사용자를 찾을 수 없습니다")));
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
