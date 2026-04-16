package com.zerobugfreinds.identity_service.dto;

import java.util.List;

public record InternalUserIdsExistenceRequest(
		List<String> userIds
) {
}
