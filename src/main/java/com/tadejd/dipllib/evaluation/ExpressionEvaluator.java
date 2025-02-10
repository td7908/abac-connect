package com.tadejd.dipllib.evaluation;

import ca.uhn.fhir.context.RuntimeSearchParam;
import care.better.abac.policy.execute.evaluation.*;
import org.antlr.v4.runtime.misc.Triple;

import java.util.Map;
import java.util.stream.Collectors;

import static org.hl7.fhir.instance.model.api.IAnyResource.SP_RES_ID;

/**
 * @author Tadej Delopst
 */
public class ExpressionEvaluator {

	private ExpressionEvaluator() {
		// Private constructor to prevent instantiation
	}

	public static boolean evaluate(EvaluationExpression expression, ExpressionContext context, String policyName) {
		return evaluateNode(expression, context, policyName);
	}

	private static boolean evaluateNode(EvaluationExpression expression, ExpressionContext context, String policyName) {
		if (expression instanceof BooleanEvaluationExpression) {
			return ((BooleanEvaluationExpression) expression).getBooleanValue();
		} else if (expression instanceof BooleanOperationEvaluationExpression boolExpr) {
            return switch (boolExpr.getBooleanOperation()) {
                case AND -> evaluateNode(boolExpr.getLeftChild(), context, policyName) &&
                        evaluateNode(boolExpr.getRightChild(), context, policyName);
                case OR -> evaluateNode(boolExpr.getLeftChild(), context, policyName) ||
                        evaluateNode(boolExpr.getRightChild(), context, policyName);
                case NOT -> !evaluateNode(
                        boolExpr.getLeftChild() != null ? boolExpr.getLeftChild() : boolExpr.getRightChild(),
                        context, policyName);
                default -> throw new UnsupportedOperationException("Unsupported boolean operation " + boolExpr.getBooleanOperation());
            };
		} else if (expression instanceof ResultSetEvaluationExpression) {
			return context != null && context.getId() != null &&
				((ResultSetEvaluationExpression) expression).getExternalIds().contains(context.getId());
		} else if (expression instanceof ValueSetEvaluationExpression) {
			return context != null && context.pathDataEquals(policyName, (ValueSetEvaluationExpression) expression);
		} else {
			throw new UnsupportedOperationException("Unsupported evaluation expression " + expression.getClass().getName());
		}
	}

	public static Triple<String, String, String> extractAndValidateExpressionPath(ValueSetEvaluationExpression expression, String policyName) {
		String[] parts = expression.getPath().split("\\.", 3);
		if (parts.length < 2 || (parts.length == 3 && !"identifier".equals(parts[2]))) {
			throw new IllegalArgumentException(
				"Policy '" + policyName + "' contains invalid search parameter syntax '" + expression.getPath() + "'. " +
					"The syntax must be 'NameOfResource.search-parameter[.chain]' " +
					"(where the only permissible chain is .identifier)");
		}
		return new Triple<>(parts[0], parts[1], parts.length > 2 ? parts[2] : null);
	}

	public static RuntimeSearchParam validateAndGetSearchParameter(Map<String, RuntimeSearchParam> activeSearchParams,
																						String searchParameterName, String resourceName, String policyName) {
		Map<String, RuntimeSearchParam> allowedSearchParameters = filterSearchParameters(activeSearchParams);
		RuntimeSearchParam searchParameter = allowedSearchParameters.get(searchParameterName);
		if (searchParameter == null) {
			throw allowedResourcePathsException(resourceName, policyName, searchParameterName, allowedSearchParameters);
		}
		return searchParameter;
	}

	private static Map<String, RuntimeSearchParam> filterSearchParameters(Map<String, RuntimeSearchParam> activeSearchParams) {
		return activeSearchParams.entrySet().stream()
			.filter(entry -> entry.getValue().getPath() != null && !entry.getValue().getPath().isEmpty() ||
				SP_RES_ID.equals(entry.getValue().getName()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	private static IllegalArgumentException allowedResourcePathsException(String resourceName, String policyName, String path,
																								 Map<String, RuntimeSearchParam> activeSearchParams) {
		String allowedParams = activeSearchParams.keySet().stream()
			.map(param -> resourceName + "." + param)
			.collect(Collectors.joining(", "));
		return new IllegalArgumentException("Policy '" + policyName + "' contains invalid search parameter '" + resourceName + "." + path +
															"'. Allowed parameters: " + allowedParams);
	}
}
