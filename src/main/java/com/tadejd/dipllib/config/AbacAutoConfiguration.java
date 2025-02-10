package com.tadejd.dipllib.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.extractor.ISearchParamExtractor;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.util.ISearchParamRegistry;
import com.tadejd.dipllib.AbacInterceptor;
import com.tadejd.dipllib.client.AbacRestClient;
import com.tadejd.dipllib.client.AbacRestClientImpl;
import com.tadejd.dipllib.rule.AbacRuleManager;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @author Tadej Delopst
 * todo null checks
 */
@Configuration
@EnableConfigurationProperties(AbacProperties.class)
public class AbacAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public AbacInterceptor abacInterceptor(
			AbacProperties abacProperties,
			List<IFhirResourceDao<? extends IBaseResource>> resourceDaoList,
			ISearchParamExtractor searchParamExtractor,
			FhirContext fhirContext,
			AbacRuleManager abacRuleManger,
			AbacRestClient abacRestClient,
			RestfulServer restfulServer) {
		AbacInterceptor abacInterceptor = new AbacInterceptor(
				abacProperties,
				resourceDaoList,
				abacRuleManger,
				searchParamExtractor,
				fhirContext,
				abacRestClient
		);
		restfulServer.registerInterceptor(abacInterceptor);
		return abacInterceptor;
	}

	@Bean
	@ConditionalOnMissingBean
	public AbacRestClient abacRestClient(AbacProperties abacProperties) {
		return new AbacRestClientImpl(abacProperties.getUrl());
	}

	@Bean
	@ConditionalOnMissingBean
	public AbacRuleManager abacRuleManager(ISearchParamRegistry searchParamRegistry) {
		return new com.tadejd.dipllib.rule.AbacRuleManager(searchParamRegistry);
	}
}
