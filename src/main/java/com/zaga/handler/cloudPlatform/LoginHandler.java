package com.zaga.handler.cloudPlatform;

import com.google.gson.JsonArray;

import io.fabric8.openshift.client.OpenShiftClient;
import jakarta.ws.rs.core.Response;


public interface LoginHandler {
    

    OpenShiftClient login(String username, String password, String oauthToken, boolean useOAuthToken, String clusterUrl);

    Response listAllServices(OpenShiftClient authenticatedClient);

    void instrumentDeployment(OpenShiftClient authenticatedClient,String namespace, String deploymentName);

    void unInstrumentDeployment(OpenShiftClient authenticatedClient,String namespace, String deploymentName);

    String logout(OpenShiftClient authenticatedClient);

    Response viewClusterInfo(OpenShiftClient authenticatedClient);

    Response viewClusterCondition(OpenShiftClient authenticatedClient);

    Response viewClusterInventory(OpenShiftClient authenticatedClient);

    Response viewClusterNetwork(OpenShiftClient authenticatedClient);

    Response viewClusterIP(OpenShiftClient authenticatedClient);

    Response viewClusterNodes(OpenShiftClient authenticatedClient);

    Response viewNodeIP(OpenShiftClient authenticatedClient);

    Response listNodes(OpenShiftClient authenticatedClient);
}

