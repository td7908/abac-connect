package com.tadejd.dipllib;

import ca.uhn.fhir.rest.api.RestOperationTypeEnum;

import java.util.List;

/**
 * @author Tadej Delopst
 */
public record Policy(
	String policyName,
	String resourceType,
	List<RestOperationTypeEnum> operationType,
	List<Mapping> mappings) {

	public boolean matches(String resourceType, RestOperationTypeEnum operationType) {
		return this.resourceType.equals(resourceType) && this.operationType.contains(operationType);
	}

	@Override
	public String policyName() {
		return policyName;
	}

	@Override
	public String resourceType() {
		return resourceType;
	}

	@Override
	public List<RestOperationTypeEnum>  operationType() {
		return operationType;
	}

	public List<Mapping> mappings() {
		return mappings;
	}


	public record Reference(String targetResource, String searchParameter) {
	}
	// Nested Mapping class
	public record Mapping(String contextAttribute, String expression, Reference reference) {
	}
}
