package com.tadejd.dipllib.rule;

import ca.uhn.fhir.rest.server.util.ISearchParamRegistry;
import com.tadejd.dipllib.evaluation.FhirActiveSearchParametersSupplier;

/**
 * @author Tadej Delopst
 */
public class AbacRuleManager {
	private ISearchParamRegistry searchParamRegistry;

	public AbacRuleManager(ISearchParamRegistry searchParamRegistry) {
		this.searchParamRegistry = searchParamRegistry;
	}

	public FhirActiveSearchParametersSupplier createActiveSearchParametersSupplier(String resourceType) {
		return new FhirActiveSearchParametersSupplier(resourceType, searchParamRegistry);
	}
}
