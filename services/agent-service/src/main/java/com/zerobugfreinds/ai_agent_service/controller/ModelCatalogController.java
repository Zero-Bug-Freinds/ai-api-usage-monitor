package com.zerobugfreinds.ai_agent_service.controller;

import com.zerobugfreinds.ai_agent_service.service.ExternalModelCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents/model-catalog")
public class ModelCatalogController {

	private final ExternalModelCatalogService externalModelCatalogService;

	public ModelCatalogController(ExternalModelCatalogService externalModelCatalogService) {
		this.externalModelCatalogService = externalModelCatalogService;
	}

	@GetMapping
	public ExternalModelCatalogService.CatalogSnapshot getCatalog() {
		return externalModelCatalogService.currentCatalog();
	}
}
