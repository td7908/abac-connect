package com.tadejd.dipllib.evaluation;

import ca.uhn.fhir.context.RuntimeSearchParam;
import ca.uhn.fhir.rest.server.util.ISearchParamRegistry;
import ca.uhn.fhir.rest.server.util.ResourceSearchParams;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Tadej Delopst
 */
public class FhirActiveSearchParametersSupplier implements Supplier<Map<String, RuntimeSearchParam>> {

	private final String resourceName;
	private final ISearchParamRegistry theSearchParamRegistry;

	private Map<String, RuntimeSearchParam> activeSearchParameters = null;
	private boolean read = false;

	private final ReentrantLock lock = new ReentrantLock();

	public FhirActiveSearchParametersSupplier(String resourceName, ISearchParamRegistry theSearchParamRegistry) {
		this.resourceName = resourceName;
		this.theSearchParamRegistry = theSearchParamRegistry;
	}

	@Override
	public Map<String, RuntimeSearchParam> get() {
		lock.lock();
		try {
			if (!read) {
				ResourceSearchParams searchParams = readSearchParameters();
				activeSearchParameters = searchParams.getSearchParamNames().stream()
					.collect(Collectors.toMap(
						name -> name,
						searchParams::get
					));
				read = true;
			}
			return activeSearchParameters;
		} finally {
			lock.unlock();
		}
	}

	private ResourceSearchParams readSearchParameters() {
		return theSearchParamRegistry.getActiveSearchParams(resourceName);
	}
}
