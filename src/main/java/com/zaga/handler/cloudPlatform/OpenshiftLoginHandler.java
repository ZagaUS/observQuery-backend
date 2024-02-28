package com.zaga.handler.cloudPlatform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.harmony.pack200.NewAttributeBands.Integral;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zaga.entity.queryentity.openshift.ClusterNetwork;
import com.zaga.entity.queryentity.openshift.Environments;
import com.zaga.entity.queryentity.openshift.ServiceList;
import com.zaga.entity.queryentity.openshift.UserCredentials;
import com.zaga.repo.OpenshiftCredsRepo;
import com.zaga.repo.ServiceListRepo;

import io.fabric8.kubernetes.api.model.ComponentCondition;
import io.fabric8.kubernetes.api.model.ComponentStatus;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeAddress;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.api.model.storage.StorageClassList;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.config.v1.ClusterVersion;
import io.fabric8.openshift.api.model.config.v1.InfrastructureList;
import io.fabric8.openshift.client.OpenShiftClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class OpenshiftLoginHandler  implements LoginHandler{

     @Inject
    ServiceListRepo serviceListRepo;

    @Inject
    ClusterNetwork clusterNetwork;

    @Inject
    OpenshiftCredsRepo openshiftCredsRepo;

    // public Response login(String username, String password, String clusterUrl){
    //     try {
    //         KubernetesClient kubernetesClient;
    //         kubernetesClient = new KubernetesClientBuilder()
    //                 .withConfig(new ConfigBuilder()
    //                     .withPassword(password)
    //                     .withUsername(username)
    //                     .withMasterUrl(clusterUrl)
    //                     .withTrustCerts(true) 
    //                     .build())
    //                 .build();
    //                 OpenShiftClient openShiftClient = kubernetesClient.adapt(OpenShiftClient.class);
    //                 openShiftClient.projects().list();
    //                 String successMessage = "Login successful!";
    //                 return Response.status(Response.Status.OK).entity(successMessage).build();
    
            
    //     } 

    //     catch(KubernetesClientException e){
    //         String errorMessage = "Incorrect username or password.";
    //             return Response.status(Response.Status.OK).entity(errorMessage).build();

    //     }

    //     catch (Exception e) {
    //         e.printStackTrace();
    //         return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
    //                 .entity("Something went wrong with the system")
    //                 .build();
    //     }

    // }

    public OpenShiftClient login(String username, String password, String oauthToken, boolean useOAuthToken, String clusterUrl) {
        try {
            KubernetesClient kubernetesClient;
    
            if (useOAuthToken) {
                kubernetesClient = new KubernetesClientBuilder()       //"https://api.zagaopenshift.zagaopensource.com:6443"
                    .withConfig(new ConfigBuilder()
                        .withOauthToken(oauthToken)
                        .withMasterUrl(clusterUrl)
                        .withTrustCerts(true) 
                        .build())
                    .build();
            } else {
                kubernetesClient = new KubernetesClientBuilder()
                    .withConfig(new ConfigBuilder()
                        .withPassword(password)
                        .withUsername(username)
                        .withMasterUrl(clusterUrl)
                        .withTrustCerts(true) 
                        .build())
                    .build();
            }
    
            OpenShiftClient openShiftClient = kubernetesClient.adapt(OpenShiftClient.class);
    
            // Attempt to list OpenShift projects as a verification step
            if (isLoginSuccessful(openShiftClient)) {
                logSuccess("Login successful");
                return openShiftClient;
            } else {
                System.out.println("Login failed. Invalid credentials or insufficient permissions.");
                return null;
            }
        } catch (Exception e) {
            logError("Error while logging in", e);
            return null;
        }
    } 


    private boolean isLoginSuccessful(OpenShiftClient openShiftClient) {
        // Attempt to list OpenShift projects or perform other verification steps
        try {
            openShiftClient.projects().list();
            return true; // Login is successful
        } catch (KubernetesClientException e) {
            logError("Error while verifying login: " + e.getMessage(), e);
            return false; // Login is unsuccessful
        }
    }
    
    private void logError(String message, Exception e) {
        System.err.println(message);
        e.printStackTrace();
    }
    
    private void logSuccess(String message) {
        System.out.println(message);
    }
    




 @Override
 public Response listAllServices(OpenShiftClient authenticatedClient) {
    System.out.println("clientservices"+authenticatedClient);
        if (authenticatedClient != null) {
            try {
                OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);

                NamespaceList namespaceList = openShiftClient.namespaces().list();
                List<ServiceList> deploymentsInfoList = new ArrayList<>();

                for (Namespace namespace : namespaceList.getItems()) {
                    String namespaceName = namespace.getMetadata().getName();

                    DeploymentList deploymentList = openShiftClient.apps().deployments().inNamespace(namespaceName).list();

                    for (Deployment deployment : deploymentList.getItems()) {
                        String serviceName = deployment.getMetadata().getLabels().get("app");

                        String deploymentName = deployment.getMetadata().getName();


                        Map<String, String> annotations = deployment.getSpec().getTemplate().getMetadata().getAnnotations();
                        String injectJavaValue = annotations.get("instrumentation.opentelemetry.io/inject-java");

                         // Extracting createdTime from metadata
                        String createdTime = deployment.getMetadata().getCreationTimestamp();


                        if (injectJavaValue == null) {
                            injectJavaValue = "false";
                        }

                        ServiceList serviceList = new ServiceList();
                        serviceList.setNamespaceName(namespaceName);  // Correct usage of setNamespaceName
                        serviceList.setServiceName(serviceName);
                        serviceList.setInstrumented(injectJavaValue);
                        serviceList.setDeploymentName(deploymentName);
                        serviceList.setCreatedTime(createdTime);

                        // serviceListRepo.persist(serviceList);

                        deploymentsInfoList.add(serviceList);
                    }
                }

                return Response.ok(deploymentsInfoList).build();
            } catch (Exception e) {
                e.printStackTrace();
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("You are unauthorized to do this action.")
                        .build();
            }
        } else {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("You are not logged in.")
                    .build();
        }
    }


    @Override
    public void instrumentDeployment(OpenShiftClient authenticatedClient,String namespace, String deploymentName) {
        System.out.println("namespace: " + namespace);
        System.out.println("deploymentName: " + deploymentName);
        System.out.println("client: " + authenticatedClient);
        if (authenticatedClient !=null && namespace != null && deploymentName != null) {
            try {
                OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);
                
                System.out.print("authern----------"+authenticatedClient);

                Deployment deployment = openShiftClient.apps().deployments()
                        .inNamespace(namespace)
                        .withName(deploymentName)
                        .get();

                if (deployment != null) {

                    Map<String, String> annotations = deployment.getSpec().getTemplate().getMetadata().getAnnotations();
                    System.out.println("---------Before instrumentation: ---------" + annotations.get("instrumentation.opentelemetry.io/inject-java"));
                    annotations.put("instrumentation.opentelemetry.io/inject-java", "true");
                    System.out.println("--------------After instrumentation:------- " + annotations.get("instrumentation.opentelemetry.io/inject-java"));

                    openShiftClient.apps().deployments()
                            .inNamespace(namespace)
                            .withName(deploymentName)
                            .patch(deployment);
                } else {
                    System.out.println("Deployment not found in namespace: " + namespace + ", deploymentName: " + deploymentName);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Invalid parameters");
        }
    }



    
    @Override
        public void unInstrumentDeployment(OpenShiftClient authenticatedClient,String namespace, String deploymentName) {
        System.out.println("namespace: " + namespace);
        System.out.println("deploymentName: " + deploymentName);
        System.out.println("client: " + authenticatedClient);
        if (authenticatedClient !=null && namespace != null && deploymentName != null) {
            try {
                OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);
                
                System.out.print("authern----------"+authenticatedClient);

                Deployment deployment = openShiftClient.apps().deployments()
                        .inNamespace(namespace)
                        .withName(deploymentName)
                        .get();

                if (deployment != null) {

                    Map<String, String> annotations = deployment.getSpec().getTemplate().getMetadata().getAnnotations();
                    System.out.println("---------Before instrumentation: ---------" + annotations.get("instrumentation.opentelemetry.io/inject-java"));
                    annotations.put("instrumentation.opentelemetry.io/inject-java", "false");
                    System.out.println("--------------After instrumentation:------- " + annotations.get("instrumentation.opentelemetry.io/inject-java"));

                    openShiftClient.apps().deployments()
                            .inNamespace(namespace)
                            .withName(deploymentName)
                            .patch(deployment);
                } else {
                    System.out.println("Deployment not found in namespace: " + namespace + ", deploymentName: " + deploymentName);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Invalid parameters");
        }
    }

    @Override
    public String logout(OpenShiftClient authenticatedClient) {
        if (authenticatedClient != null) {
            try {
                // Perform OpenShift-specific logout actions, e.g., token revocation or session clearing
                // authenticatedClient.logout();
                // or equivalent OpenShift API calls
            } catch (Exception e) {
                e.printStackTrace();
                return "Logout failed"; // Handle the exception as needed
            } finally {
                authenticatedClient.close(); // Close the client
            }
        }
        return "Logged out successfully!";
    }
    @Override
    public Response viewClusterInfo(OpenShiftClient authenticatedClient) {

        if (authenticatedClient == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("You are not logged in.")
                    .build();
        }
        else{
            OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class); 
        try {
            List<ClusterVersion>  clusterInfo = openShiftClient.config().clusterVersions().list().getItems();
            System.out.println(clusterInfo);
            List<Map<String,String>> clusterListInfo = new ArrayList<>();
            for (ClusterVersion clusterVersion : clusterInfo) {
                Gson gson = new Gson();
                JsonElement jsonElement = gson.toJsonTree(clusterVersion);
                JsonObject jsonObject = (JsonObject) jsonElement.getAsJsonObject().get("spec");
                // System.out.println(jsonElement.getAsJsonObject().get("kind"));
                System.out.println("clusterID " + jsonObject.get("clusterID"));
                System.out.println("Channel " + jsonObject.get("channel"));
                JsonObject jsonObject2 = (JsonObject) jsonElement.getAsJsonObject().get("status");
                System.out.println("Version " + jsonObject2.get("desired").getAsJsonObject().get("version"));
                Map<String, String> clusterInfoMap = new HashMap<>();
                clusterInfoMap.put("clusterID", jsonObject.get("clusterID").getAsString());
                clusterInfoMap.put("channel" , jsonObject.get("channel").getAsString());
                clusterInfoMap.put("version" , jsonObject2.get("desired").getAsJsonObject().get("version").getAsString());
                clusterListInfo.add(clusterInfoMap);
   }
        return Response.ok(clusterListInfo).build();            } 
        catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("You are unauthorized to do this action.")
                    .build();
        }
        }
        
}

    @Override
    public Response viewClusterCondition(OpenShiftClient authenticatedClient) {
        if (authenticatedClient == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("You are not logged in.")
                    .build();
        }
        else{
            
            OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);

            try {
            List<ComponentStatus> clusterStatus = openShiftClient.componentstatuses().list().getItems();

            List<Map<String, String>> clusterListInfo = new ArrayList<>();

            for (ComponentStatus componentStatus : clusterStatus) {
                String componentName = componentStatus.getMetadata().getName();

                List<String> types = new ArrayList<>();
                for (ComponentCondition condition : componentStatus.getConditions()) {
                    types.add(condition.getType());
                }

                Map<String, String> clusterMap = new HashMap<>();
                clusterMap.put("name", componentName);
                clusterMap.put("condition", String.join(", ", types)); 

                // Adding the map to the list
                clusterListInfo.add(clusterMap);
            }

            Gson gson = new Gson();
            String jsonOutput = gson.toJson(clusterListInfo);

            System.out.println(jsonOutput);

            return Response.ok(jsonOutput).build();   }
            catch (Exception e) {
                e.printStackTrace();
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("You are unauthorized to do this action.")
                        .build();
            }
            }

    
}
    @Override
    public Response viewClusterInventory(OpenShiftClient authenticatedClient) {
        if (authenticatedClient == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("You are not logged in.")
                    .build();
        }
        else{
            try{
                OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);
                NodeList nodeList = openShiftClient.nodes().list();
                Gson nodeGson = new Gson();
                JsonElement nodeJsonElement = nodeGson.toJsonTree(nodeList);
                JsonArray nodeJsonArray = nodeJsonElement.getAsJsonObject().get("items").getAsJsonArray();
                Integer nodeCount = nodeJsonArray.size();
                System.out.println(nodeCount);
                PersistentVolumeClaimList pvc = openShiftClient.persistentVolumeClaims().inAnyNamespace().list();
                Gson pvcGson = new Gson();
                JsonElement pvcJsonElement = pvcGson.toJsonTree(pvc);
                JsonArray pvcArray = pvcJsonElement.getAsJsonObject().get("items").getAsJsonArray();
                System.out.println(pvcArray.size());
                Integer pvcCount = pvcArray.size();
                PodList podList = openShiftClient.pods().inAnyNamespace().list();
                Gson podGson = new Gson();
                JsonElement podJsonElement = podGson.toJsonTree(podList);
                JsonArray podArray = podJsonElement.getAsJsonObject().get("items").getAsJsonArray();
                System.out.println(podArray.size());
                Integer PodCount = podArray.size();
                StorageClassList storage = openShiftClient.storage().storageClasses().list();
                Gson gson = new Gson();
                JsonElement jsonElement = gson.toJsonTree(storage);

                JsonArray jsonArray = jsonElement.getAsJsonObject().get("items").getAsJsonArray();
                System.out.println(jsonArray.size());
                Integer StorageClass = jsonArray.size();
                List<Map<String, Integer>> clusterInventory = new ArrayList<>();
                Map<String,Integer> clusterInventoryMap = new HashMap<>();
                clusterInventoryMap.put("Node", nodeCount);
                clusterInventoryMap.put("StorageClass", StorageClass);
                clusterInventoryMap.put("PersistentVolumeClaims", pvcCount);
                clusterInventoryMap.put("Pods", PodCount);
                clusterInventory.add(clusterInventoryMap);
                return Response.ok(clusterInventory).build();
            }
            catch (Exception e) {
                e.printStackTrace();
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("You are unauthorized to do this action.")
                        .build();
            }
            }
        }
        
        @Override
        public Response viewClusterNetwork(OpenShiftClient authenticatedClient) {
                if (authenticatedClient == null) {
                    return Response.status(Response.Status.UNAUTHORIZED)
                            .entity("You are not logged in.")
                            .build();
                }
                else{
                    try{
                    OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);
                    List<io.fabric8.openshift.api.model.config.v1.Network>  clusterInfo = openShiftClient.config().networks().list().getItems();
                    List<Map<String,String>> clusterNetworkInfo = new ArrayList<>();
                    for (io.fabric8.openshift.api.model.config.v1.Network network : clusterInfo) {
                        Gson gson = new Gson();
                        JsonElement jsonElement = gson.toJsonTree(network);
                        JsonObject jsonObject = (JsonObject) jsonElement.getAsJsonObject().get("spec");
                        // System.out.println(jsonElement.getAsJsonObject().get("kind"));
                        System.out.println("Network Type " + jsonObject.get("networkType"));
                        System.out.println("Service Network " + jsonObject.get("serviceNetwork"));
                        JsonArray clusterNetworkArray = jsonObject.getAsJsonArray("clusterNetwork");
                        System.out.println("Cluster Network:");
                        for (JsonElement clusterNetworkElement : clusterNetworkArray) {
                            JsonObject clusterNetworkObject = clusterNetworkElement.getAsJsonObject();
                            String cidr = clusterNetworkObject.get("cidr").getAsString();
                            int hostPrefix = clusterNetworkObject.get("hostPrefix").getAsInt();
                            System.out.println("- CIDR: " + cidr);
                            System.out.println("- Host Prefix: " + hostPrefix);
                        
                        Map<String, String> clusterNetworkMap = new HashMap<>();
                        clusterNetworkMap.put("networkType" , jsonObject.get("networkType").getAsString());
                        clusterNetworkMap.put("serviceNetwork" , jsonObject.get("serviceNetwork").getAsString());
                        clusterNetworkMap.put("cidr" , clusterNetworkObject.get("cidr").getAsString());
                        clusterNetworkMap.put("hostPrefix" , clusterNetworkObject.get("hostPrefix").getAsString());
                        clusterNetworkInfo.add(clusterNetworkMap);
                    }}
        
                return Response.ok(clusterNetworkInfo).build();
        
            }

            catch (Exception e) {
                e.printStackTrace();
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("You are unauthorized to do this action.")
                        .build();
            }
        }
    }
        
        @Override
        public Response viewClusterIP(OpenShiftClient authenticatedClient) {
            if (authenticatedClient == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity("You are not logged in.")
                        .build();
            }
            else{
                try{
            OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);

            NodeList nodeList = openShiftClient.nodes().list();
            Gson nodeGson = new Gson();
            JsonElement nodeJsonElement = nodeGson.toJsonTree(nodeList);
            JsonArray nodeJsonArray = nodeJsonElement.getAsJsonObject().get("items").getAsJsonArray();
            Integer nodeCount = nodeJsonArray.size();
            List<Map<String,String>> clusterConfigInfo = new ArrayList<>();
            if(nodeCount == 1){
            List<Node> node = openShiftClient.nodes().list().getItems();
            Gson gson = new Gson();
            JsonElement jsonElement = gson.toJsonTree(node);
            JsonArray jsonArrayList = jsonElement.getAsJsonArray();
            for (JsonElement jsonEle : jsonArrayList) {
                JsonObject jsonObject = (JsonObject) jsonEle.getAsJsonObject().get("status").getAsJsonObject();
                JsonArray jsonArray = jsonObject.get("addresses").getAsJsonArray();
                Map<String,String> addressMap = new HashMap<>();
                for (JsonElement jsonElement2 : jsonArray) {
                    String type = jsonElement2.getAsJsonObject().get("type").getAsString();
                    if(type.equalsIgnoreCase("InternalIP")){
                    String ipAddress = jsonElement2.getAsJsonObject().get("address").getAsString();
                    addressMap.put("apiServerInternalIP", ipAddress);
                    addressMap.put("ingressIP", ipAddress);
                    }
                }
                clusterConfigInfo.add(addressMap);
            }}
             else if(nodeCount > 1){
            InfrastructureList clusterConfig = openShiftClient.config().infrastructures().list();
                Gson gson = new Gson();
                JsonElement jsonElement = gson.toJsonTree(clusterConfig);
                JsonArray jsonArray = jsonElement.getAsJsonObject().get("items").getAsJsonArray();
                
                for (JsonElement jsonElement2 : jsonArray) {
                    JsonObject jsonObject = jsonElement2.getAsJsonObject().get("status").getAsJsonObject().get("platformStatus").getAsJsonObject();
                    String apiServerInternalIP = jsonObject.get("baremetal").getAsJsonObject().get("apiServerInternalIP").getAsString();
                    String ingressIP = jsonObject.get("baremetal").getAsJsonObject().get("ingressIP").getAsString();
                    Map<String,String> clustMap = new HashMap<>();
                    clustMap.put("apiServerInternalIP", apiServerInternalIP);
                    clustMap.put("ingressIP", ingressIP);
                    clusterConfigInfo.add(clustMap);
            
        
        }}
        return Response.ok(clusterConfigInfo).build();
    }
        catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("You are unauthorized to do this action.")
                    .build();
        }
    }
 }



        @Override
        public Response viewClusterNodes(OpenShiftClient authenticatedClient){

            if (authenticatedClient == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity("You are not logged in.")
                        .build();
            }
            else{
                try{
            OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);
            int controlPlaneNodeCount = 0;
            int workerNodeCount = 0;
            
                    Map<String,String> clusterConfigInfo = new HashMap<>();
                    for (Node node : openShiftClient.nodes().list().getItems()) {

                        if (isControlPlaneNode(node)) {
                            controlPlaneNodeCount++;
                        } else if (isWorkerNode(node)) {
                            workerNodeCount++;
                        }
                    }
                    clusterConfigInfo.put("controlPlaneNodes", String.valueOf(controlPlaneNodeCount));
                    clusterConfigInfo.put("workerNodes", String.valueOf(workerNodeCount));
                   
        
        
                    
                    return Response.ok(clusterConfigInfo).build();

                }
                catch (Exception e) {
                    e.printStackTrace();
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity("You are unauthorized to do this action.")
                            .build();
                }}
}


            private boolean isControlPlaneNode(Node node) {
                return node.getMetadata().getLabels().containsKey("node-role.kubernetes.io/master");
            }

            private boolean isWorkerNode(Node node) {
                return node.getMetadata().getLabels().containsKey("node-role.kubernetes.io/worker");
            }

            @Override
            public Response viewNodeIP(OpenShiftClient authenticatedClient, String nodeName) {
                
                if (authenticatedClient == null) {
                    return Response.status(Response.Status.UNAUTHORIZED)
                            .entity("You are not logged in.")
                            .build();
                }
                else{
                    try{

                OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);

                List<Map<String,String>> clusterConfigInfo = new ArrayList<>();
                        
                for (Node node : openShiftClient.nodes().list().getItems()) {
                    String nodeType = " ";
                    if (isControlPlaneNode(node)) {
                        nodeType = "master";
                    } else if (isWorkerNode(node)) {
                        nodeType = "worker";
                    }

                JsonObject statusObject = (JsonObject) new Gson().toJsonTree(node.getStatus());
                JsonArray addressesArray = statusObject.getAsJsonArray("addresses");
                Map<String, String> addressMap = new HashMap<>();
                
                for (JsonElement addressElement : addressesArray) {
                    JsonObject addressObject = addressElement.getAsJsonObject();
                    String type = addressObject.get("type").getAsString();
                    String ipAddress = addressObject.get("address").getAsString();
                    
                    addressMap.put(type, ipAddress);
                
                    
                }
                    addressMap.put("nodeType", nodeType.toString());

                    String hostname = addressMap.get("Hostname");
                    if (hostname != null && hostname.equals(nodeName)) {
                        clusterConfigInfo.add(addressMap);
                    }
                }
                return Response.ok(clusterConfigInfo).build();}


    catch (Exception e) {
        e.printStackTrace();
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("You are unauthorized to do this action.")
                .build();
    }
}
}

@Override
public Response getNodes(String username, String clustername, String nodename) {
    try {
        OpenShiftClient openshiftLogin = commonClusterLogin(username, clustername);
            PodList podList = openshiftLogin.pods().inAnyNamespace().list();
            List<Pod> pods = podList.getItems();
            
            int podCount = 0;
            for (Pod pod : pods) {
                if (pod.getSpec().getNodeName() != null && pod.getSpec().getNodeName().equals(nodename)) {
                    podCount++;
                }
            }
            
            return Response.ok(podCount).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("An error occurred while retrieving pod count.")
                    .build();
        }
}




//one method
@Override
public Response viewClustersInformation(OpenShiftClient authenticatedClient) {
    if (authenticatedClient == null) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity("You are not logged in.")
                .build();
    }

    try {
        OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);
        
        // Cluster Info
        List<ClusterVersion> clusterInfo = openShiftClient.config().clusterVersions().list().getItems();
        List<Map<String, String>> clusterListInfo = new ArrayList<>();
        for (ClusterVersion clusterVersion : clusterInfo) {
            Gson gson = new Gson();
            JsonElement jsonElement = gson.toJsonTree(clusterVersion);
            JsonObject jsonObject = (JsonObject) jsonElement.getAsJsonObject().get("spec");
            JsonObject jsonObject2 = (JsonObject) jsonElement.getAsJsonObject().get("status");
            Map<String, String> clusterInfoMap = new HashMap<>();
            clusterInfoMap.put("clusterID", jsonObject.get("clusterID").getAsString());
            clusterInfoMap.put("channel", jsonObject.get("channel").getAsString());
            clusterInfoMap.put("version", jsonObject2.get("desired").getAsJsonObject().get("version").getAsString());
            clusterListInfo.add(clusterInfoMap);
        }

        // Cluster Status
        List<ComponentStatus> clusterStatus = openShiftClient.componentstatuses().list().getItems();
        List<Map<String, String>> clusterStatusList = new ArrayList<>();
        for (ComponentStatus componentStatus : clusterStatus) {
            String componentName = componentStatus.getMetadata().getName();
            List<String> types = new ArrayList<>();
            for (ComponentCondition condition : componentStatus.getConditions()) {
                types.add(condition.getType());
            }
            Map<String, String> clusterMap = new HashMap<>();
            clusterMap.put("name", componentName);
            clusterMap.put("condition", String.join(", ", types));
            clusterStatusList.add(clusterMap);
        }

        // Cluster Inventory
        NodeList nodeList = openShiftClient.nodes().list();
        PersistentVolumeClaimList pvc = openShiftClient.persistentVolumeClaims().inAnyNamespace().list();
        PodList podList = openShiftClient.pods().inAnyNamespace().list();
        StorageClassList storage = openShiftClient.storage().storageClasses().list();
        // Aggregate counts
        int nodeCount = nodeList.getItems().size();
        int pvcCount = pvc.getItems().size();
        int podCount = podList.getItems().size();
        int storageClassCount = storage.getItems().size();
        List<Map<String, Integer>> clusterInventory = new ArrayList<>();
        Map<String, Integer> clusterInventoryMap = new HashMap<>();
        clusterInventoryMap.put("Node", nodeCount);
        clusterInventoryMap.put("StorageClass", storageClassCount);
        clusterInventoryMap.put("PersistentVolumeClaims", pvcCount);
        clusterInventoryMap.put("Pods", podCount);
        clusterInventory.add(clusterInventoryMap);

        // Cluster Network
        List<io.fabric8.openshift.api.model.config.v1.Network> clusterNetworkInfo = openShiftClient.config().networks().list().getItems();
        List<Map<String, String>> clusterNetworkList = new ArrayList<>();
        for (io.fabric8.openshift.api.model.config.v1.Network network : clusterNetworkInfo) {
            Gson gson = new Gson();
            JsonElement jsonElement = gson.toJsonTree(network);
            JsonObject jsonObject = (JsonObject) jsonElement.getAsJsonObject().get("spec");
            JsonArray clusterNetworkArray = jsonObject.getAsJsonArray("clusterNetwork");
            for (JsonElement clusterNetworkElement : clusterNetworkArray) {
                JsonObject clusterNetworkObject = clusterNetworkElement.getAsJsonObject();
                String cidr = clusterNetworkObject.get("cidr").getAsString();
                int hostPrefix = clusterNetworkObject.get("hostPrefix").getAsInt();
                Map<String, String> clusterNetworkMap = new HashMap<>();
                clusterNetworkMap.put("networkType", jsonObject.get("networkType").getAsString());
                clusterNetworkMap.put("serviceNetwork", jsonObject.get("serviceNetwork").getAsString());
                clusterNetworkMap.put("cidr", cidr);
                clusterNetworkMap.put("hostPrefix", String.valueOf(hostPrefix));
                clusterNetworkList.add(clusterNetworkMap);
            }
        }
        // Cluster IP
NodeList nodes = openShiftClient.nodes().list();
List<Map<String, String>> clusterIpList = new ArrayList<>();
for (Node node : nodes.getItems()) {
    Map<String, String> ipMap = new HashMap<>();
    List<NodeAddress> addresses = node.getStatus().getAddresses();
    for (NodeAddress address : addresses) {
        String type = address.getType();
        String ipAddress = address.getAddress();
        if (type.equalsIgnoreCase("InternalIP")) {
            ipMap.put("apiIP", ipAddress);
            ipMap.put("ingressIP", ipAddress);
        }
    }
    clusterIpList.add(ipMap);
}


        // Cluster Nodes
        int controlPlaneNodeCount = 0;
        int workerNodeCount = 0;
        for (Node node : nodeList.getItems()) {
            if (isControlPlaneNode(node)) {
                controlPlaneNodeCount++;
            } else if (isWorkerNode(node)) {
                workerNodeCount++;
            }
        }
        Map<String, String> clusterNodeMap = new HashMap<>();
        clusterNodeMap.put("controlPlaneNodes", String.valueOf(controlPlaneNodeCount));
        clusterNodeMap.put("workerNodes", String.valueOf(workerNodeCount));

        // Constructing the final response
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("clusterInfo", clusterListInfo);
        responseData.put("clusterStatus", clusterStatusList);
        responseData.put("clusterInventory", clusterInventory);
        responseData.put("clusterNetwork", clusterNetworkList);
        responseData.put("clusterIP", clusterIpList);
        responseData.put("clusterNodes", Arrays.asList(clusterNodeMap));

        return Response.ok(responseData).build();
    } catch (Exception e) {
        e.printStackTrace();
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("You are unauthorized to do this action.")
                .build();
    }
}

    @Override
    public Response listNodes(String username , String clustername){
try{

            OpenShiftClient openShiftClient = commonClusterLogin(username, clustername);
            List<String> addresses = new ArrayList<>();
        List<Node> node = openShiftClient.nodes().list().getItems();
        Gson gson = new Gson();
        JsonElement jsonElement = gson.toJsonTree(node);
        JsonArray jsonArrayList = jsonElement.getAsJsonArray();
        for (JsonElement jsonEle : jsonArrayList) {
            JsonObject jsonObject = (JsonObject) jsonEle.getAsJsonObject().get("status").getAsJsonObject();
            JsonArray jsonArray = jsonObject.get("addresses").getAsJsonArray();
            Map<String,String> addressMap = new HashMap<>();
            for (JsonElement jsonElement2 : jsonArray) {
                String type = jsonElement2.getAsJsonObject().get("type").getAsString();
                if(type.equalsIgnoreCase("HostName")){
                String address = jsonElement2.getAsJsonObject().get("address").getAsString();
                addresses.add(address);}
                // addressMap.put("ingressIP", ipAddress);
                }
            
            }

        return Response.ok(addresses).build();}
    catch (Exception e) {
        e.printStackTrace();
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("You are unauthorized to do this action.")
                .build();
    }
}


@Override
public OpenShiftClient commonClusterLogin(String username , String clustername){
    UserCredentials userCredentials = openshiftCredsRepo.getUser(username);
    System.out.println("------user credientals----");
    System.out.println(userCredentials);
    System.out.println("---------------------------");
    Gson gson = new Gson();
    JsonElement jsonElement = gson.toJsonTree(userCredentials);
    System.out.println(jsonElement);
    System.out.println("---------------------------");
    JsonArray jsonArray = jsonElement.getAsJsonObject().get("environments").getAsJsonArray();
    String CLUSTERUSERNAME = null;
    String CLUSTERPASSWORD = null;
    String CLUSTERURL = null;
    for (JsonElement jsonElement2 : jsonArray) {
        String clusterName = jsonElement2.getAsJsonObject().get("clusterName").getAsString();
        System.out.println(clusterName +"------------------");
        String clusterUserName = jsonElement2.getAsJsonObject().get("clusterUsername").getAsString();
        String clusterPassword = jsonElement2.getAsJsonObject().get("clusterPassword").getAsString();
        String hostUrl = jsonElement2.getAsJsonObject().get("hostUrl").getAsString();
        Integer clusterID = jsonElement2.getAsJsonObject().get("clusterId").getAsInt();

        if (clusterName.equalsIgnoreCase(clustername)) {
            CLUSTERUSERNAME = clusterUserName;
            CLUSTERPASSWORD = clusterPassword;
            CLUSTERURL = hostUrl;
            break;
        }
    }
    
    OpenShiftClient openshiftLogin = login(CLUSTERUSERNAME,  CLUSTERPASSWORD,"", false,  CLUSTERURL);  
    
    return openshiftLogin;
}
    @Override
    public Response clusterDetails(String username , String clustername){

        OpenShiftClient openshiftLogin = commonClusterLogin(username, clustername);
        return viewClustersInformation(openshiftLogin);
    }






	@Override
	public Response listClusters(String username) {
		UserCredentials userCredentials = openshiftCredsRepo.getUser(username);
        System.out.println(userCredentials);
        List<Environments> environments = userCredentials.getEnvironments();
        List<Map<String, Object>> clusterInventory = new ArrayList<>();

        // Create a list to store cluster names
        List<Map<String, Object>> clusters = new ArrayList<>();

        // Iterate through environments to find the cluster details
        for (Environments environment : environments) {
            Map<String, Object> clusterDetails = new HashMap<>();
            clusterDetails.put("clusterName", environment.getClusterName());
            clusterDetails.put("clusterId", environment.getClusterId());
            clusterDetails.put("clusterType", environment.getClusterType());
            clusters.add(clusterDetails);
        }

        if (!clusters.isEmpty()) {
            // Return the list of cluster details if found
            // NEED TO SEND THE FIRST CLUSTER DETAILS TO FRONT END 
            // String clustername = clusters.get(0).get("clusterName").toString();
            // Response response = clusterDetails(username,clustername);
            // Gson gson = new Gson();
            // JsonElement jsonElement = gson.toJsonTree(response.getEntity());
            // Integer nodeCount = jsonElement.getAsJsonObject().get("clusterInventory").getAsJsonArray().get(0).getAsJsonObject().get("Node").getAsInt();
            
            // Map<String, Object> combinedData = new HashMap<>();
            // combinedData.put("clusters", clusters);
            // System.out.println(clusters);
            // combinedData.put("loginResponse", response.getEntity());
            // clusterInventory.add(combinedData);
            return Response.ok(clusters).build();

        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Clusters not found for username: " + username)
                    .build();
        }
    }


	@Override
	public Response clusterNodeDetails(String username, String clustername, String nodename) {

        OpenShiftClient openShiftClient = commonClusterLogin(username, clustername);
        Response clusterInfo = viewClusterInfo(openShiftClient);
        Response clusterComponentStatus = viewClusterCondition(openShiftClient);
        Response clusterNetwork = viewClusterNetwork(openShiftClient);
        Response nodeIp = viewNodeIP(openShiftClient, nodename);
        Response nodeInventory = getNodes(username, clustername, nodename);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("clusterInfo", clusterInfo.getEntity());
        responseData.put("clusterStatus", clusterComponentStatus.getEntity());
        // responseData.put("clusterInventory", clusterInve);
        responseData.put("clusterNetwork", clusterNetwork.getEntity());
        responseData.put("clusterIP", nodeIp.getEntity());
        responseData.put("nodeInventory", nodeInventory.getEntity());
        // responseData.put("clusterNodes", Arrays.asList(clusterNodeMap));
// 
        return Response.ok(responseData).build();


        
	}




    
}




