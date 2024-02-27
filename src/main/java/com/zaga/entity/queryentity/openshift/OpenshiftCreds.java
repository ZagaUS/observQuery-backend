package com.zaga.entity.queryentity.openshift;




import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.quarkus.mongodb.panache.common.MongoEntity;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties("id")
@ApplicationScoped
@MongoEntity(collection="UserCreds",database="ObservabilityCredentials")
public class OpenshiftCreds {

    private String username;
    private String clusterUsername;
    private String clusterPassword;
    private String hosutUrl;
    private Integer clusterId;
    private String clusterName;
    private String clusterType;
    
}
