package com.tadejd.dipllib.evaluation;

import ca.uhn.fhir.context.RuntimeSearchParam;
import ca.uhn.fhir.jpa.searchparam.extractor.ISearchParamExtractor;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import care.better.abac.policy.execute.evaluation.ValueSetEvaluationExpression;
import org.antlr.v4.runtime.misc.Triple;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.Resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author Tadej Delopst
 */
public class ExpressionContext {
	private final RestOperationTypeEnum operationType;
	private final Supplier<IBaseResource> newResource;
	private final Supplier<IBaseResource> existingResource;
	private final Supplier<Map<String, RuntimeSearchParam>> activeSearchParamsSupplier;
	private final ISearchParamExtractor extractor;

	// Add a dynamic attributes map
	private final Map<String, Object> attributes = new HashMap<>();

	// Add methods for dynamic attributes
	public void put(String key, Object value) {
		attributes.put(key, value);
	}

	public Object get(String key) {
		return attributes.get(key);
	}

	public ExpressionContext(
		RestOperationTypeEnum operationType,
		Supplier<IBaseResource> newResource,
		Supplier<IBaseResource> existingResource,
		Supplier<Map<String, RuntimeSearchParam>> activeSearchParamsSupplier,
		ISearchParamExtractor extractor) {
		this.operationType = operationType;
		this.newResource = newResource;
		this.existingResource = existingResource;
		this.activeSearchParamsSupplier = activeSearchParamsSupplier;
		this.extractor = extractor;
	}
	public String getId() {
		if (operationType == RestOperationTypeEnum.CREATE) {
			return null;
		} else {
			return extractId(existingResource.get());
		}
	}
	private String extractId(IBaseResource resource) {
		if (resource == null || resource.getIdElement() == null) {
			return null;
		}
		return resource.getIdElement().getIdPart();
	}

	public boolean pathDataEquals(String policyName, ValueSetEvaluationExpression expression) {
		IBaseResource resource = (operationType == RestOperationTypeEnum.CREATE)
			? newResource.get()
			: existingResource.get();

		if (resource == null) {
			return false;
		}

		Triple<String, String, String> pathComponents = ExpressionEvaluator.extractAndValidateExpressionPath(expression, policyName);
		String resourceName = pathComponents.a;
		String searchParameterName = pathComponents.b;

		String resourceType;
		if (resource instanceof IResource) {
			resourceType = ((IResource) resource).getResourceName();
		} else if (resource instanceof Resource) {
			resourceType = ((Resource) resource).getResourceType().name();
		} else {
			throw new IllegalStateException("Unsupported version of FHIR!");
		}

		if (!resourceType.equals(resourceName)) {
			return true;
		}

		List<String> values = expression.getValues().stream().toList();

		RuntimeSearchParam runtimeSearchParam = ExpressionEvaluator.validateAndGetSearchParameter(
			activeSearchParamsSupplier.get(), searchParameterName, resourceName, policyName);

		List<String> extractedValues = extractor.extractParamValuesAsStrings(runtimeSearchParam, resource);

		return extractedValues.stream().anyMatch(values::contains);
	}
}
