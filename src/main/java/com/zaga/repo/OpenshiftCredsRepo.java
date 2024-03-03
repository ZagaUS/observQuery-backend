package com.zaga.repo;

import com.zaga.entity.queryentity.openshift.UserCredentials;

import io.quarkus.cache.CacheResult;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.mongodb.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;


@ApplicationScoped
public class OpenshiftCredsRepo implements PanacheMongoRepository<UserCredentials> {

        @CacheResult(cacheName = "user-creds")
        public UserCredentials getUser(String username) {
        PanacheQuery<UserCredentials> data = UserCredentials.find("username=?1", username);
        UserCredentials userData = data.firstResult();
        return userData;

    }

    public UserCredentials findByClusterUsername(String clusterUsername) {
        return find("environments.clusterUsername", clusterUsername).firstResult();
    }
}
