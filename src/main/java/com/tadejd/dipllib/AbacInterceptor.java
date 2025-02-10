package com.tadejd.dipllib;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.searchparam.extractor.ISearchParamExtractor;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.interceptor.auth.AuthorizationInterceptor;
import ca.uhn.fhir.rest.server.interceptor.auth.PolicyEnum;
import care.better.abac.policy.execute.evaluation.EvaluationExpression;
import com.tadejd.dipllib.client.AbacRestClient;
import com.tadejd.dipllib.config.AbacProperties;
import com.tadejd.dipllib.evaluation.ExpressionContext;
import com.tadejd.dipllib.evaluation.ExpressionEvaluator;
import com.tadejd.dipllib.evaluation.FhirReadResourceSupplier;
import com.tadejd.dipllib.rule.AbacRuleManager;
import jakarta.annotation.PostConstruct;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r4.hapi.ctx.HapiWorkerContext;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ABAC Interceptor
 * This class integrates Attribute-Based Access Control (ABAC) by managing policies.
 * <p>
 *
 * @author Tadej Delopst
 */
@Interceptor
public class AbacInterceptor extends AuthorizationInterceptor implements CustomInterceptor {
    private final List<Policy> policies = new ArrayList<>();
    private final AbacProperties abacProperties;
    private final Map<Class<? extends IBaseResource>, IFhirResourceDao<? extends IBaseResource>> resourceDaosByType;
    private final AbacRuleManager abacRuleManager;
    private final ISearchParamExtractor extractor;
    private final FHIRPathEngine engine;
    private final FhirContext fhirContext;

    private final AbacRestClient client;

    public AbacInterceptor(
            AbacProperties abacProperties,
            List<IFhirResourceDao<? extends IBaseResource>> resourceDaoList,
            AbacRuleManager abacRuleManager,
            ISearchParamExtractor searchParamExtractor,
            FhirContext fhirContext,
            AbacRestClient client) {
        super(PolicyEnum.ALLOW);
        this.abacProperties = abacProperties;

        this.resourceDaosByType = resourceDaoList.stream()
                .collect(Collectors.toMap(
                        IFhirResourceDao::getResourceType,
                        dao -> dao
                ));

        this.abacRuleManager = abacRuleManager;
        this.extractor = searchParamExtractor;
        this.engine = new FHIRPathEngine(new HapiWorkerContext(fhirContext, fhirContext.getValidationSupport()));
        this.fhirContext = fhirContext;
        this.client = client;
    }

    @PostConstruct
    public void initializePolicies() {
        for (AbacProperties.PolicyProperties entry : abacProperties.getPolicies()) {
            List<RestOperationTypeEnum> operations = Arrays.stream(entry.getOperations().split(","))
                    .toList()
                    .stream()
                    .map(RestOperationTypeEnum::valueOf)
                    .collect(Collectors.toList());

            List<Policy.Mapping> mappings = null;
            if (entry.getMappings() != null) {
                mappings = entry.getMappings().stream()
                        .map(mapping -> {
                            Policy.Reference reference = null;
                            if (mapping.getReference() != null) {
                                reference = new Policy.Reference(mapping.getReference().getTargetResource(), mapping.getReference().getSearchParameter());
                            }
                            return new Policy.Mapping(mapping.getContextAttribute(), mapping.getExpression(), reference);
                        })
                        .toList();
            }

            policies.add(new Policy(
                    entry.getName(),
                    entry.getResourceType(),
                    operations,
                    mappings
            ));
        }
    }

    public List<Policy> getPolicies() {
        return policies;
    }

    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
    @Override
    public void hookResourcePreCreate(RequestDetails theRequest, IBaseResource theResource, Pointcut thePointcut) {
        super.hookResourcePreCreate(theRequest, theResource, thePointcut);
        handleSingleResource(theRequest, (Resource)theResource);
    }

    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_DELETED)
    @Override
    public void hookResourcePreDelete(RequestDetails theRequest, IBaseResource theResource, Pointcut thePointcut) {
        super.hookResourcePreDelete(theRequest, theResource, thePointcut);
        handleSingleResource(theRequest, (Resource)theResource);
    }

    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_UPDATED)
    @Override
    public void hookResourcePreUpdate(RequestDetails theRequest, IBaseResource theOldResource, IBaseResource theNewResource, Pointcut thePointcut) {
        super.hookResourcePreUpdate(theRequest, theOldResource, theNewResource, thePointcut);
        handleSingleResource(theRequest, (Resource)theOldResource);
    }

    private void handleSingleResource(RequestDetails theRequest, Resource theResource) {
        List<Policy> matchingPolicies = policies.stream()
                .filter(policy -> policy.matches(theRequest.getResourceName(), theRequest.getRestOperationType()))
                .toList();

        if (!matchingPolicies.isEmpty()) {
            matchingPolicies.forEach(policy -> {
                String policyName = policy.policyName();
                Map<String, Object> ctxEntry = evaluateResourceContextEntry(theResource, policy.mappings());
                if (ctxEntry != null) {
                    List<EvaluationExpression> abacResponse = client.executeMulti(policyName, List.of(ctxEntry));
                    if (!ExpressionEvaluator.evaluate(abacResponse.get(0),
                                                      (ExpressionContext)theRequest.getUserData().get("ABAC_EXPRESSION_CONTEXT"),
                                                      policyName)) {
                        throw new ResourceNotFoundException("Resource not found.");
                    }
                } else {
                    throw new ResourceNotFoundException("Resource not found.");
                }
            });
        }
    }

    @Override
    @Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
    public void hookOutgoingResponse(RequestDetails theRequestDetails, IBaseResource theResponseObject, Pointcut thePointcut) {
        super.hookOutgoingResponse(theRequestDetails, theResponseObject, thePointcut);

        if (!EnumSet.of(RestOperationTypeEnum.CREATE, RestOperationTypeEnum.UPDATE, RestOperationTypeEnum.DELETE)
                .contains(theRequestDetails.getRestOperationType())) {

            policies.stream()
                    .filter(policy -> policy.matches(theRequestDetails.getResourceName(), theRequestDetails.getRestOperationType()))
                    .forEach(policy -> {
                        Map<Resource, Map<String, Object>> resourceCtxMap = new HashMap<>();
                        String policyName = policy.policyName();
                        if (theResponseObject instanceof Bundle) {
                            ((Bundle)theResponseObject).getEntry().forEach(entry -> {
                                Map<String, Object> ctxEntry = evaluateResourceContextEntry(entry.getResource(), policy.mappings());
                                if (ctxEntry != null) {
                                    resourceCtxMap.put(entry.getResource(), ctxEntry);
                                }
                            });
                        } else if (theResponseObject instanceof Resource) {
                            Map<String, Object> ctxEntry = evaluateResourceContextEntry((Resource)theResponseObject, policy.mappings());
                            if (ctxEntry != null) {
                                resourceCtxMap.put((Resource)theResponseObject, ctxEntry);
                            }
                        }

                        List<Map<String, Object>> ctx = resourceCtxMap.values().stream().toList();
                        List<EvaluationExpression> abacResponse = client.executeMulti(policyName, ctx);
                        initExpressionContext(theRequestDetails);

                        if (theResponseObject instanceof Bundle) {
                            ((Bundle)theResponseObject).setEntry(((Bundle)theResponseObject).getEntry().stream()
                                                                         .filter(entry -> {
                                                                             int resourceIndex = getResourceIndex(resourceCtxMap, entry.getResource());
                                                                             return resourceIndex != -1 && ExpressionEvaluator.evaluate(abacResponse.get(
                                                                                                                                                resourceIndex),
                                                                                                                                        (ExpressionContext)theRequestDetails.getUserData()
                                                                                                                                                .get("ABAC_EXPRESSION_CONTEXT"),
                                                                                                                                        policyName); // Keep only entries with allow boolean
                                                                         }).toList());
                            ((Bundle)theResponseObject).setTotal(((Bundle)theResponseObject).getEntry().size());
                        } else if (theResponseObject instanceof Resource) {
                            if (!ExpressionEvaluator.evaluate(abacResponse.get(0),
                                                              (ExpressionContext)theRequestDetails.getUserData()
                                                                      .get("ABAC_EXPRESSION_CONTEXT"),
                                                              policyName)) {
                                throw new ResourceNotFoundException("Resource not found.");
                            }
                        }
                    });
        }
    }

    private int getResourceIndex(Map<Resource, Map<String, Object>> map, Resource target) {
        int index = 0;
        for (Resource key : map.keySet()) {
            if (key.equals(target)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private Map<String, Object> evaluateResourceContextEntry(Resource resource, List<Policy.Mapping> mappings) {
        boolean valid = true;
        Map<String, Object> ctxEntry = new HashMap<>();
        for (Policy.Mapping mapping : mappings) {
            List<Base> e = mapping.reference() == null ? engine.evaluate(resource, mapping.expression()) :
                    resolveReference(resource, mapping)
                            .stream()
                            .flatMap(res -> {
                                List<Base> eval = engine.evaluate((Resource)res, mapping.expression());
                                return eval.isEmpty() ? Stream.empty() : eval.stream();
                            })
                            .toList();

            if (e.isEmpty()) {
                valid = false;
                break;
            } else {
                if (e.size() == 1) {
                    ctxEntry.put(mapping.contextAttribute(), e.get(0).primitiveValue());
                } else {
                    ctxEntry.put(mapping.contextAttribute(), e.stream().map(Base::primitiveValue).collect(Collectors.toList()));
                }
            }
        }
        return valid ? ctxEntry : null;
    }

    private List<IBaseResource> resolveReference(Resource resource, Policy.Mapping mapping) {
        String referenced = resource.getId();

        IFhirResourceDao<? extends IBaseResource> resourceDao = resourceDaosByType.get(fhirContext.getResourceDefinition(mapping.reference().targetResource())
                                                                                               .getImplementingClass());

        SearchParameterMap params = new SearchParameterMap();
        params.add(mapping.reference().searchParameter(), new ReferenceParam(referenced));

        return resourceDao.search(params).getAllResources();
    }

    private void initExpressionContext(RequestDetails theRequest) {
        ExpressionContext expressionContext = new ExpressionContext(
                theRequest.getRestOperationType(),
                theRequest::getResource,
                createResourceSupplier(theRequest),
                abacRuleManager.createActiveSearchParametersSupplier(theRequest.getResourceName()),
                extractor
        );
        theRequest.getUserData().put("ABAC_EXPRESSION_CONTEXT", expressionContext);
    }

    private FhirReadResourceSupplier createResourceSupplier(RequestDetails details) {
        IFhirResourceDao<? extends IBaseResource> resourceDao = null;

        if (details.getResourceName() != null) {
            if (!("swagger-ui".equals(details.getResourceName()) || "api-docs".equals(details.getResourceName()))) {
                resourceDao = resourceDaosByType.get(
                        details.getFhirContext()
                                .getResourceDefinition(details.getResourceName())
                                .getImplementingClass()
                );
            }
        }

        return new FhirReadResourceSupplier(resourceDao, details.getResourceName(),
                                            details.getId() != null ? details.getId().getIdPart() : null);
    }
}
