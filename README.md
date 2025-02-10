# **ABAC library**  

🚀 **Attribute-Based Access Control (ABAC) for HAPI FHIR Server**  

This library provides an **ABAC (Attribute-Based Access Control) Interceptor** for HAPI FHIR servers, enabling fine-grained access control based on policies and dynamic attributes. It integrates with an external ABAC policy evaluation server and applies access control rules at various interception points.

---

## **Features**  
**Interceptor for FHIR Resource Access Control**  
- Hooks into FHIR operations (create, update, delete, read).  
- Evaluates access based on defined policies.  

**Integration with ABAC Server**  
- Sends resource context for evaluation.  
- Applies **ALLOW/DENY** decisions dynamically.  

**Flexible Policy Definition**  
- Policies are configured in `application.yml`.  
- Supports **FHIRPath expressions** for extracting attributes.  
- Allows **resource reference resolution** for additional context.  

**Supports OAuth 2.0 Authentication**  
- Extracts user identity from security context.  
- Relays OAuth 2.0 token in requests to ABAC server.  

**Automated Configuration**  
- Provides default beans for interceptor, ABAC client, and rule management.  
- Uses Spring Boot's **auto-configuration** for seamless integration.  

---

## **Installation**  
To use this library in a Spring Boot project, add the dependency after building it locally:  

```xml
<dependency>
  <groupId>com.tadejd.dipllib</groupId>
  <artifactId>abac-connect</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

### **Configuration**  
In your `application.yml`, define the ABAC server URL and policies:

```yaml
abac:
  url: "https://abac-server.com/api"
  policies:
    - name: DEMOGRAPHICS_CAREPLAN_READ
      type: read
      resourceType: CarePlan
      operations: "READ"
      mappings:
        - contextAttribute: "careTeamName"
          expression: "CarePlan.careTeam"
          reference:
            targetResource: CareTeam
            searchParameter: participant
```

---

## **Usage**  

### **Registering the Interceptor**  
The interceptor is automatically registered via Spring Boot auto-configuration. If manual registration is needed:

```java
@Bean
public AbacInterceptor abacInterceptor(RestfulServer restfulServer, AbacProperties properties) {
    AbacInterceptor interceptor = new AbacInterceptor(properties, ...);
    restfulServer.registerInterceptor(interceptor);
    return interceptor;
}
```

### **Client Integration for ABAC Policy Evaluation**  
If you need to call the ABAC server manually, use:

```java
@Autowired
private AbacRestClient abacRestClient;

public boolean checkAccess(String policyName, Map<String, String> context) {
    return abacRestClient.execute(policyName, context).isAllowed();
}
```

---

## **How It Works**  
1. The **Interceptor** captures incoming requests.  
2. It extracts **context attributes** using FHIRPath expressions.  
3. The **ABAC Client** sends this context to the ABAC server.  
4. The ABAC server evaluates the policy and returns **ALLOW/DENY**.  
5. The interceptor **filters responses** accordingly.  
