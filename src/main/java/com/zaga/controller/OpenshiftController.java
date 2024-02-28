package com.zaga.controller;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.zaga.handler.cloudPlatform.LoginHandler;

import io.fabric8.openshift.client.OpenShiftClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@Path("/openshift")
public class OpenshiftController {
    
    
    @Inject
    private LoginHandler loginHandler;

    private OpenShiftClient authenticatedClient;

   
    @ConfigProperty(name = "my.timeout.property", defaultValue = "5000") // Set the timeout to 5 seconds (adjust as needed)
    long timeout;

    @GET
    @Path("/login")
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(
        @QueryParam("username") String username,
        @QueryParam("password") String password,
        @QueryParam("oauthToken") String oauthToken, 
        @QueryParam("useOAuthToken") boolean useOAuthToken,
        @QueryParam("clusterUrl") String clusterUrl
    ) {
        try {
            authenticatedClient = loginHandler.login(username, password, oauthToken, useOAuthToken, clusterUrl);
    
            if (authenticatedClient != null) {
                String successMessage = "Login successful!";
                return Response.status(Response.Status.OK).entity(successMessage).build();
            } else {
                String errorMessage = "Incorrect username or password.";
                return Response.status(Response.Status.OK).entity(errorMessage).build();
            }
        } catch (Exception e) {
            String errorMessage = "Login request timed out or failed: " + e.getMessage();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errorMessage).build();
        }
    }
    

   
    @GET
    @Path("/listAllProjects")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listAllProjects() {
        return loginHandler.listAllServices(authenticatedClient);
    }

    // @GET
    // @Path("/listPods")
    // @Produces(MediaType.APPLICATION_JSON)
    // public Response listPods() {
    //     return loginHandler.listPods(authenticatedClient);
    // }

    @GET
    @Path("/listAllNodes")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listAllNodes(@QueryParam("username") String username,@QueryParam("clusterName") String clustername){
        Response response = loginHandler.listNodes(username, clustername);
        
        Object responseData = response.getEntity(); 
        // Object[] responseDataArray = { responseData }; 
      
    return Response.ok(responseData).build(); 
        
    }

    // @GET
    // @Path("/listAllNodes")
    // @Produces(MediaType.APPLICATION_JSON)
    // public Response listAllNodes(@QueryParam("username") String username,@QueryParam("clusterName") String clustername) {
    //     return loginHandler.listNodes( username  , clustername);
    // }

    // @GET
    // @Path("/viewClusterInfo")
    // @Produces(MediaType.APPLICATION_JSON)
    // @Consumes(MediaType.APPLICATION_JSON)
    // public Response viewClusterInfo() {
    //     return loginHandler.viewClusterInfo(authenticatedClient);
    // }

    // @GET
    // @Path("/viewClusterStatus")
    // @Produces(MediaType.APPLICATION_JSON)
    // public Response viewClusterCondition() {
    //     return loginHandler.viewClusterCondition(authenticatedClient);
    // }

    // @GET
    // @Path("/viewClusterInventory")
    // @Produces(MediaType.APPLICATION_JSON)
    // public Response viewClusterInventory() {
    //     return loginHandler.viewClusterInventory(authenticatedClient);
    // }

    // @GET
    // @Path("/viewClusterNetwork")
    // @Produces(MediaType.APPLICATION_JSON)
    // public Response viewClusterNetwork() {
    //     return loginHandler.viewClusterNetwork(authenticatedClient);
    // }

    // @GET
    // @Path("/viewClusterIP")
    // @Produces(MediaType.APPLICATION_JSON)
    // public Response viewClusterIp() {
    //     return loginHandler.viewClusterIP(authenticatedClient);
    // }

    @GET
    @Path("/viewNodeIP")
    @Produces(MediaType.APPLICATION_JSON)
    public Response viewNodeIp(@QueryParam("nodeName") String nodeName) {
        return loginHandler.viewNodeIP(authenticatedClient, nodeName);
    }


    // @GET
    // @Path("/viewClusterNodes")
    // @Produces(MediaType.APPLICATION_JSON)
    // public Response viewClusterNode() {
    //     return loginHandler.viewClusterNodes(authenticatedClient);
    // }



    @POST
    @Path("/instrument/{namespace}/{deploymentName}")
    public Response instrumentDeployment(
        @PathParam(value = "namespace") String namespace,
         @PathParam(value = "deploymentName") String deploymentName) {
            loginHandler.instrumentDeployment(authenticatedClient, namespace, deploymentName);
        return Response.ok("Instrumented "+deploymentName+"service").build();
    }

    @POST
    @Path("/unInstrument/{namespace}/{deploymentName}")
    public Response unInstrumentDeployment(
        @PathParam(value = "namespace") String namespace,
         @PathParam(value = "deploymentName") String deploymentName) {
            loginHandler.unInstrumentDeployment(authenticatedClient, namespace, deploymentName);
        return Response.ok("Uninstrumented"+deploymentName+"service").build();
    }
    


    @GET
    @Path("/logout")
    @Produces(MediaType.APPLICATION_JSON)
    public String logout() {
        loginHandler.logout(authenticatedClient);
        return "Logged out successfully!";
    }





@GET
@Path("/combinedinfo")
@Produces(MediaType.APPLICATION_JSON)
public Response getClusterInformation() {
    Response response = loginHandler.viewClustersInformation(authenticatedClient); 
    Object responseData = response.getEntity(); 
    Object[] responseDataArray = { responseData }; 
    return Response.ok(responseDataArray).build(); 
}



    @GET
    @Path("/getClusterInformation")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClusterDetails(@QueryParam("username") String username,@QueryParam("clusterName") String clustername){
        Response response = loginHandler.clusterDetails(username, clustername);
        Object responseData = response.getEntity(); 
        Object[] responseDataArray = { responseData }; 
      
    return Response.ok(responseDataArray).build(); 
        
    }

    @GET
    @Path("/getClusterNodeInformation")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClusterNodeDetails(@QueryParam("username") String username,@QueryParam("clusterName") String clustername, @QueryParam("nodeName") String nodename){
        Response response = loginHandler.clusterNodeDetails(username, clustername, nodename);
        Object responseData = response.getEntity(); 
        Object[] responseDataArray = { responseData }; 
      
    return Response.ok(responseDataArray).build(); 
        
    }

    @GET
    @Path("/getAllClusters")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllClusters(@QueryParam("username") String username){
        Response response = loginHandler.listClusters(username);
        return response;
    }


    @GET
    @Path("/getNodeDetails")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getNodeDetails(@QueryParam("username") String username,@QueryParam("clusterName") String clustername, @QueryParam("nodeName") String nodename){
        Response response = loginHandler.getNodes(username, clustername, nodename);
        return response;
    }


}
