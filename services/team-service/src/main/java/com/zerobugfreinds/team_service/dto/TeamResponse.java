package com.zerobugfreinds.team_service.dto;

public record TeamResponse(
		String id,
		String name,
		long memberCount
) {
}
