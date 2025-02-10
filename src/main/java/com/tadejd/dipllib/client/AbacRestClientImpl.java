package com.tadejd.dipllib.client;

import care.better.abac.policy.execute.evaluation.EvaluationExpression;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Tadej Delopst
 */
public class AbacRestClientImpl implements AbacRestClient {
	private final String serverUrl;

	private final RestTemplate restTemplate = new RestTemplate();
	{
		restTemplate.setInterceptors(
				List.of(new OAuth2TokenRelayInterceptor())
		);
	}

	public AbacRestClientImpl(String serverUrl) {
		this.serverUrl = serverUrl;
	}

	@Override
	public EvaluationExpression execute(String policyName, Map<String, String> context) {
		return restTemplate.exchange(serverUrl + "/rest/v1/policy/execute/name/{name}/expression",
											  HttpMethod.POST,
											  getHttpEntity(context),
											  EvaluationExpression.class, policyName).getBody();
	}

	@Override
	public List<EvaluationExpression> executeMulti(String policyName, List<Map<String, Object>> context) {
		return restTemplate.exchange(
			serverUrl + "/rest/v1/policy/execute/name/{name}/expression/multi",
			HttpMethod.POST,
			getHttpEntity(context),
			new ParameterizedTypeReference<List<EvaluationExpression>>() {},
			policyName
		).getBody();
	}

	private HttpEntity<Map<String, String>> getHttpEntity(Map<String, String> context) {
		// Create a mutable map from the context
		Map<String, String> abacContext = new HashMap<>(context);

		// Retrieve the security context and add "name" to the abacContext if applicable
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null) {
			String name = authentication.getName();
			if (name != null) {
				abacContext.put("user", name);
			}
		}

		// Create headers and set the required properties
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));
		headers.setContentType(MediaType.APPLICATION_JSON);

		// Return the HttpEntity with the context and headers
		return new HttpEntity<>(abacContext, headers);
	}

	private HttpEntity<List<Map<String, Object>>> getHttpEntity(List<Map<String, Object>> contexts) {
		// Create the body by appending authentication information to each context
		List<Map<String, Object>> body = contexts.stream()
			.map(context -> {
				Map<String, Object> extendedContext = new HashMap<>(context);

				Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
				if (authentication != null && authentication.getName() != null) {
					extendedContext.put("user", authentication.getName());
				}

				return extendedContext;
			})
			.toList(); // Collect the modified contexts

		// Create and configure headers
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));
		headers.setContentType(MediaType.APPLICATION_JSON);

		// Return the HttpEntity
		return new HttpEntity<>(body, headers);
	}


	private static class OAuth2TokenRelayInterceptor implements ClientHttpRequestInterceptor {
		@Override
		public @NotNull ClientHttpResponse intercept(
			@NotNull HttpRequest request,
			byte @NotNull [] body,
			@NotNull ClientHttpRequestExecution execution) throws IOException {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication != null) {
				request.getHeaders().add("Authorization", "Bearer " + authentication.getCredentials().toString());
			}
			return execution.execute(request, body);
		}
	}
}
