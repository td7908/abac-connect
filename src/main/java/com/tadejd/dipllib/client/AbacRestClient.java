package com.tadejd.dipllib.client;

import care.better.abac.policy.execute.evaluation.EvaluationExpression;

import java.util.List;
import java.util.Map;

/**
 * @author Tadej Delopst
 */
public interface AbacRestClient {
    EvaluationExpression execute(String policyName, Map<String, String> context);
    List<EvaluationExpression> executeMulti(String policyName, List<Map<String, Object>> context);
}
