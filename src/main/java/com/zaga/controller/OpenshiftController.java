package com.zaga.controller;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.zaga.handler.cloudPlatform.LoginHandler;

import io.fabric8.openshift.client.OpenShiftClient;
// import jakarta.ejb.Timeout;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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


}
