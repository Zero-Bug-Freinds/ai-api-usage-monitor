package com.zerobugfreinds.identity_service.dto;

import java.util.List;

public record InternalUserIdsExistenceResponse(
		List<String> existingUserIds
) {
}
