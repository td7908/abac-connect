package com.tadejd.dipllib.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "abac")
public class AbacProperties {
	private String url;
	private List<PolicyProperties> policies;

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public List<PolicyProperties> getPolicies() {
		return policies;
	}

	public void setPolicies(List<PolicyProperties> policies) {
		this.policies = policies;
	}

	public static class PolicyProperties {
		private String name;
		private String type;
		private String resourceType;
		private String operations;
		private List<Mapping> mappings; // Add mappings list

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getResourceType() {
			return resourceType;
		}

		public void setResourceType(String resourceType) {
			this.resourceType = resourceType;
		}

		public String getOperations() {
			return operations;
		}

		public void setOperations(String operations) {
			this.operations = operations;
		}

		public List<Mapping> getMappings() {
			return mappings;
		}

		public void setMappings(List<Mapping> mappings) {
			this.mappings = mappings;
		}

		// Nested class for Mapping
		public static class Mapping {
			private String contextAttribute;
			private String expression;
			private Reference reference;

			public String getContextAttribute() {
				return contextAttribute;
			}

			public void setContextAttribute(String contextAttribute) {
				this.contextAttribute = contextAttribute;
			}

			public String getExpression() {
				return expression;
			}

			public void setExpression(String expression) {
				this.expression = expression;
			}

			public Reference getReference() {
				return reference;
			}

			public void setReference(Reference reference) {
				this.reference = reference;
			}
		}

		public static class Reference {
			private String targetResource;
			private String searchParameter;

			public String getTargetResource() {
				return targetResource;
			}

			public void setTargetResource(String targetResource) {
				this.targetResource = targetResource;
			}

			public String getSearchParameter() {
				return searchParameter;
			}

			public void setSearchParameter(String searchParameter) {
				this.searchParameter = searchParameter;
			}
		}
	}
}
