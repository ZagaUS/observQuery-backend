package com.zaga.handler.cloudPlatform;

import com.google.gson.JsonArray;
import com.zaga.entity.queryentity.openshift.UserCredentials;

import io.fabric8.openshift.client.OpenShiftClient;
import jakarta.ws.rs.core.Response;


public interface LoginHandler {
    

    OpenShiftClient login(String username, String password, String oauthToken, boolean useOAuthToken, String clusterUrl);

    Response listAllServices(String username, String clustername);

    void instrumentDeployment(String username, String clustername, String namespace, String deploymentName);

    void unInstrumentDeployment(String username, String clustername, String namespace, String deploymentName);

    String logout(OpenShiftClient authenticatedClient);

    Response viewClusterInfo(OpenShiftClient authenticatedClient);

    Response viewClusterCondition(OpenShiftClient authenticatedClient);

    Response viewClusterInventory(OpenShiftClient authenticatedClient);

    Response viewClusterNetwork(OpenShiftClient authenticatedClient);

    Response viewClusterIP(OpenShiftClient authenticatedClient);

    Response viewClusterNodes(OpenShiftClient authenticatedClient);

    Response viewNodeIP(OpenShiftClient authenticatedClient, String nodename);


    // Response viewClustersInformation(String username, String clustername);

    Response listClusters(String username);
    Response listNodes(String username , String clustername);

    public Response clusterDetails(String username , String clustername);
    public OpenShiftClient commonClusterLogin(String username , String clustername);

    Response clusterNodeDetails(String username , String clustername, String nodename);
    Response getNodes(String username, String clustername, String nodename);

    Response viewClusterCapacity(OpenShiftClient openShiftClient, String nodename);
}

