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

import io.fabric8.kubernetes.api.model.Cluster;
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
import io.fabric8.openshift.api.model.config.v1.InfrastructureSpec;
import io.fabric8.openshift.api.model.hive.v1.ClusterClaimSpec;
import io.fabric8.openshift.api.model.miscellaneous.metal3.v1alpha1.BareMetalHostList;
import io.fabric8.openshift.client.OpenShiftClient;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class OpenshiftLoginHandler implements LoginHandler {

    @Inject
    ServiceListRepo serviceListRepo;

    @Inject
    ClusterNetwork clusterNetwork;

    @Inject
    OpenshiftCredsRepo openshiftCredsRepo;

    // @CacheResult(cacheName = "openshift-cluster-login")
    public OpenShiftClient login(String username, String password, String oauthToken, boolean useOAuthToken,
            String clusterUrl) {
        try {
            KubernetesClient kubernetesClient;
            if (useOAuthToken) {
                kubernetesClient = new KubernetesClientBuilder()
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
            // Need to check the openshift client can able to access the project().list()
            // If not able to access means ,user don't have permission
            return openShiftClient;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Response listAllServices(String username, String clustername) {

        OpenShiftClient openshiftLogin = commonClusterLogin(username, clustername);
        return listServices(openshiftLogin);

    }

    public Response listServices(OpenShiftClient authenticatedClient) {
        try {
            OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);
            NamespaceList namespaceList = openShiftClient.namespaces().list();
            List<ServiceList> deploymentsInfoList = new ArrayList<>();
            for (Namespace namespace : namespaceList.getItems()) {
                String namespaceName = namespace.getMetadata().getName();
                DeploymentList deploymentList = openShiftClient.apps().deployments().inNamespace(namespaceName)
                        .list();
                for (Deployment deployment : deploymentList.getItems()) {
                    String serviceName = deployment.getMetadata().getLabels().get("app");
                    String deploymentName = deployment.getMetadata().getName();
                    Map<String, String> annotations = deployment.getSpec().getTemplate().getMetadata()
                            .getAnnotations();
                    String injectJavaValue = annotations.get("instrumentation.opentelemetry.io/inject-java");
                    // Extracting createdTime from metadata
                    String createdTime = deployment.getMetadata().getCreationTimestamp();
                    if (injectJavaValue == null) {
                        injectJavaValue = "false";
                    }
                    ServiceList serviceList = new ServiceList();
                    serviceList.setNamespaceName(namespaceName); // Correct usage of setNamespaceName
                    serviceList.setServiceName(serviceName);
                    serviceList.setInstrumented(injectJavaValue);
                    serviceList.setDeploymentName(deploymentName);
                    serviceList.setCreatedTime(createdTime);
                    deploymentsInfoList.add(serviceList);
                }
            }
            return Response.ok(deploymentsInfoList).build();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void instrumentDeployment(String username, String clustername, String namespace, String deploymentName) {

        OpenShiftClient openshiftLogin = commonClusterLogin(username, clustername);
        instrumentation(openshiftLogin, namespace, deploymentName);

    }

    public boolean instrumentation(OpenShiftClient authenticatedClient, String namespace, String deploymentName) {
        try {
            OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);
            Deployment deployment = openShiftClient.apps().deployments()
                    .inNamespace(namespace)
                    .withName(deploymentName)
                    .get();
            Map<String, String> annotations = deployment.getSpec().getTemplate().getMetadata().getAnnotations();
            openShiftClient.apps().deployments()
                    .inNamespace(namespace)
                    .withName(deploymentName)
                    .patch(deployment);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void unInstrumentDeployment(String username, String clustername, String namespace, String deploymentName) {
        if (username == null || username.isEmpty() || clustername == null || clustername.isEmpty()) {
            throw new IllegalArgumentException("Username and clustername must be provided.");
        }
        OpenShiftClient openshiftLogin = commonClusterLogin(username, clustername);
        uninstrumentation(openshiftLogin, namespace, deploymentName);
    }

    public boolean uninstrumentation(OpenShiftClient authenticatedClient, String namespace, String deploymentName) {
        try {
            OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);
            Deployment deployment = openShiftClient.apps().deployments()
                    .inNamespace(namespace)
                    .withName(deploymentName)
                    .get();

            Map<String, String> annotations = deployment.getSpec().getTemplate().getMetadata().getAnnotations();

            openShiftClient.apps().deployments()
                    .inNamespace(namespace)
                    .withName(deploymentName)
                    .patch(deployment);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @CacheResult(cacheName = "openshift-cluster-view")
    @Override
    public Response viewClusterInfo(OpenShiftClient authenticatedClient) {
        try {
            OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);
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
                clusterInfoMap.put("version",
                        jsonObject2.get("desired").getAsJsonObject().get("version").getAsString());
                clusterListInfo.add(clusterInfoMap);
            }
            return Response.ok(clusterListInfo).build();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @CacheResult(cacheName = "openshift-cluster-condition")
    @Override
    public Response viewClusterCondition(OpenShiftClient authenticatedClient) {
        try {
            OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);
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
                clusterListInfo.add(clusterMap);
            }
            return Response.ok(clusterListInfo).build();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @CacheResult(cacheName = "openshift-cluster-inventory")
    @Override
    public Response viewClusterInventory(OpenShiftClient authenticatedClient) {
        try {
            OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);
            NodeList nodeList = openShiftClient.nodes().list();
            Gson nodeGson = new Gson();
            JsonElement nodeJsonElement = nodeGson.toJsonTree(nodeList);
            JsonArray nodeJsonArray = nodeJsonElement.getAsJsonObject().get("items").getAsJsonArray();
            Integer nodeCount = nodeJsonArray.size();
            PersistentVolumeClaimList pvc = openShiftClient.persistentVolumeClaims().inAnyNamespace().list();
            Gson pvcGson = new Gson();
            JsonElement pvcJsonElement = pvcGson.toJsonTree(pvc);
            JsonArray pvcArray = pvcJsonElement.getAsJsonObject().get("items").getAsJsonArray();
            Integer pvcCount = pvcArray.size();
            PodList podList = openShiftClient.pods().inAnyNamespace().list();
            Gson podGson = new Gson();
            JsonElement podJsonElement = podGson.toJsonTree(podList);
            JsonArray podArray = podJsonElement.getAsJsonObject().get("items").getAsJsonArray();
            Integer PodCount = podArray.size();
            StorageClassList storage = openShiftClient.storage().storageClasses().list();
            Gson gson = new Gson();
            JsonElement jsonElement = gson.toJsonTree(storage);
            JsonArray jsonArray = jsonElement.getAsJsonObject().get("items").getAsJsonArray();
            Integer StorageClass = jsonArray.size();
            List<Map<String, Integer>> clusterInventory = new ArrayList<>();
            Map<String, Integer> clusterInventoryMap = new HashMap<>();
            clusterInventoryMap.put("Node", nodeCount);
            clusterInventoryMap.put("StorageClass", StorageClass);
            clusterInventoryMap.put("PersistentVolumeClaims", pvcCount);
            clusterInventoryMap.put("Pods", PodCount);
            clusterInventory.add(clusterInventoryMap);
            return Response.ok(clusterInventory).build();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @CacheResult(cacheName = "openshift-cluster-network")
    @Override
    public Response viewClusterNetwork(OpenShiftClient authenticatedClient) {
        try {
            OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);
            List<io.fabric8.openshift.api.model.config.v1.Network> clusterInfo = openShiftClient.config().networks()
                    .list().getItems();
            List<Map<String, String>> clusterNetworkInfo = new ArrayList<>();
            for (io.fabric8.openshift.api.model.config.v1.Network network : clusterInfo) {
                Gson gson = new Gson();
                JsonElement jsonElement = gson.toJsonTree(network);
                JsonObject jsonObject = (JsonObject) jsonElement.getAsJsonObject().get("spec");
                JsonArray clusterNetworkArray = jsonObject.getAsJsonArray("clusterNetwork");
                System.out.println("Cluster Network:");
                for (JsonElement clusterNetworkElement : clusterNetworkArray) {
                    JsonObject clusterNetworkObject = clusterNetworkElement.getAsJsonObject();
                    String cidr = clusterNetworkObject.get("cidr").getAsString();
                    int hostPrefix = clusterNetworkObject.get("hostPrefix").getAsInt();
                    Map<String, String> clusterNetworkMap = new HashMap<>();
                    clusterNetworkMap.put("networkType", jsonObject.get("networkType").getAsString());
                    clusterNetworkMap.put("serviceNetwork", jsonObject.get("serviceNetwork").getAsString());
                    clusterNetworkMap.put("cidr", clusterNetworkObject.get("cidr").getAsString());
                    clusterNetworkMap.put("hostPrefix", clusterNetworkObject.get("hostPrefix").getAsString());
                    clusterNetworkInfo.add(clusterNetworkMap);
                }
            }
            return Response.ok(clusterNetworkInfo).build();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @CacheResult(cacheName = "openshift-cluster-ip")
    @Override
    public Response viewClusterIP(OpenShiftClient authenticatedClient) {
        try {
            OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);
            NodeList nodeList = openShiftClient.nodes().list();
            Gson nodeGson = new Gson();
            JsonElement nodeJsonElement = nodeGson.toJsonTree(nodeList);
            JsonArray nodeJsonArray = nodeJsonElement.getAsJsonObject().get("items").getAsJsonArray();
            Integer nodeCount = nodeJsonArray.size();
            List<Map<String, String>> clusterConfigInfo = new ArrayList<>();
            if (nodeCount == 1) {
                List<Node> node = openShiftClient.nodes().list().getItems();
                Gson gson = new Gson();
                JsonElement jsonElement = gson.toJsonTree(node);
                JsonArray jsonArrayList = jsonElement.getAsJsonArray();
                for (JsonElement jsonEle : jsonArrayList) {
                    JsonObject jsonObject = (JsonObject) jsonEle.getAsJsonObject().get("status").getAsJsonObject();
                    JsonArray jsonArray = jsonObject.get("addresses").getAsJsonArray();
                    Map<String, String> addressMap = new HashMap<>();
                    for (JsonElement jsonElement2 : jsonArray) {
                        String type = jsonElement2.getAsJsonObject().get("type").getAsString();
                        if (type.equalsIgnoreCase("InternalIP")) {
                            String ipAddress = jsonElement2.getAsJsonObject().get("address").getAsString();
                            addressMap.put("apiServerInternalIP", ipAddress);
                            addressMap.put("ingressIP", ipAddress);
                        }
                    }
                    clusterConfigInfo.add(addressMap);
                }
            } else if (nodeCount > 1) {
                InfrastructureList clusterConfig = openShiftClient.config().infrastructures().list();
                Gson gson = new Gson();
                JsonElement jsonElement = gson.toJsonTree(clusterConfig);
                JsonArray jsonArray = jsonElement.getAsJsonObject().get("items").getAsJsonArray();
                for (JsonElement jsonElement2 : jsonArray) {
                    JsonObject jsonObject = jsonElement2.getAsJsonObject().get("status").getAsJsonObject()
                            .get("platformStatus").getAsJsonObject();
                    String apiServerInternalIP = jsonObject.get("baremetal").getAsJsonObject()
                            .get("apiServerInternalIP").getAsString();
                    String ingressIP = jsonObject.get("baremetal").getAsJsonObject().get("ingressIP").getAsString();
                    Map<String, String> clustMap = new HashMap<>();
                    clustMap.put("apiServerInternalIP", apiServerInternalIP);
                    clustMap.put("ingressIP", ingressIP);
                    clusterConfigInfo.add(clustMap);
                }
            }
            return Response.ok(clusterConfigInfo).build();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @CacheResult(cacheName = "openshift-cluster-nodes")
    @Override
    public Response viewClusterNodes(OpenShiftClient authenticatedClient) {
        try {
            OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);
            int controlPlaneNodeCount = 0;
            int workerNodeCount = 0;
            Map<String, String> clusterConfigInfo = new HashMap<>();
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

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean isControlPlaneNode(Node node) {
        return node.getMetadata().getLabels().containsKey("node-role.kubernetes.io/master");
    }

    private boolean isWorkerNode(Node node) {
        return node.getMetadata().getLabels().containsKey("node-role.kubernetes.io/worker");
    }

    @CacheResult(cacheName = "openshift-node-ip")
    @Override
    public Response viewNodeIP(OpenShiftClient authenticatedClient, String nodename) {
        try {
            OpenShiftClient openShiftClient = authenticatedClient.adapt(OpenShiftClient.class);
            List<Map<String, String>> clusterConfigInfo = new ArrayList<>();
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
                if (hostname != null && hostname.equals(nodename)) {
                    clusterConfigInfo.add(addressMap);
                }
            }
            return Response.ok(clusterConfigInfo).build();
        }

        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @CacheResult(cacheName = "openshift-node-getdetails")
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
            return null;
        }
    }

    // one method
    @CacheResult(cacheName = "openshift-cluster-details")
    @Override
    public Response clusterDetails(String username, String clustername) {
        try {
            OpenShiftClient openShiftClient = commonClusterLogin(username, clustername);
            Response clusterInfo = viewClusterInfo(openShiftClient);
            Response clusterComponentStatus = viewClusterCondition(openShiftClient);
            Response clusterInventory = viewClusterInventory(openShiftClient);
            Response clusterNetwork = viewClusterNetwork(openShiftClient);
            Response clusterIp = viewClusterIP(openShiftClient);
            Response clusterNodeMap = viewClusterNodes(openShiftClient);
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("clusterInfo", clusterInfo.getEntity());
            responseData.put("clusterStatus", clusterComponentStatus.getEntity());
            responseData.put("clusterInventory", clusterInventory.getEntity());
            responseData.put("clusterNetwork", clusterNetwork.getEntity());
            responseData.put("clusterIP", clusterIp.getEntity());
            responseData.put("clusterNodes", Arrays.asList(clusterNodeMap.getEntity()));
            String clusterName = "ClusterMethod";
            Response cpuCapacity = viewClusterCapacity(openShiftClient, clusterName);
            responseData.put("cpuCapacity", cpuCapacity.getEntity());
            return Response.ok(responseData).build();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // one method
    @CacheResult(cacheName = "openshift-node-list")
    @Override
    public Response listNodes(String username, String clustername) {
        try {
            OpenShiftClient openShiftClient = commonClusterLogin(username, clustername);
            List<String> addresses = new ArrayList<>();
            List<Node> node = openShiftClient.nodes().list().getItems();
            Gson gson = new Gson();
            JsonElement jsonElement = gson.toJsonTree(node);
            JsonArray jsonArrayList = jsonElement.getAsJsonArray();
            for (JsonElement jsonEle : jsonArrayList) {
                JsonObject jsonObject = (JsonObject) jsonEle.getAsJsonObject().get("status").getAsJsonObject();
                JsonArray jsonArray = jsonObject.get("addresses").getAsJsonArray();
                Map<String, String> addressMap = new HashMap<>();
                for (JsonElement jsonElement2 : jsonArray) {
                    String type = jsonElement2.getAsJsonObject().get("type").getAsString();
                    if (type.equalsIgnoreCase("HostName")) {
                        String address = jsonElement2.getAsJsonObject().get("address").getAsString();
                        addresses.add(address);
                    }
                }
            }
            return Response.ok(addresses).build();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // @CacheResult(cacheName = "cluster-login")
    @Override
    public OpenShiftClient commonClusterLogin(String username, String clustername) {
        try {
            UserCredentials userCredentials = openshiftCredsRepo.getUser(username);
            Gson gson = new Gson();
            JsonElement jsonElement = gson.toJsonTree(userCredentials);
            JsonArray jsonArray = jsonElement.getAsJsonObject().get("environments").getAsJsonArray();
            String CLUSTERUSERNAME = null;
            String CLUSTERPASSWORD = null;
            String CLUSTERURL = null;
            for (JsonElement jsonElement2 : jsonArray) {
                System.out.println("---------------[COMMON CLUSTER LOGIN]----------- " + jsonElement2);
                String clusterName = jsonElement2.getAsJsonObject().get("clusterName").getAsString();
                String clusterUserName = jsonElement2.getAsJsonObject().get("clusterUsername") == null ? null
                        : jsonElement2.getAsJsonObject().get("clusterUsername").getAsString();
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

            OpenShiftClient openshiftLogin = login(CLUSTERUSERNAME, CLUSTERPASSWORD, "", false, CLUSTERURL);

            return openshiftLogin;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    // listActiveClusters
    @Override
    public Response listActiveClusters(String username) {
        UserCredentials userCredentials = openshiftCredsRepo.getUser(username);
        System.out.println(userCredentials);
        List<Environments> environments = userCredentials.getEnvironments();
        List<Map<String, Object>> clusterInventory = new ArrayList<>();

        // Create a list to store cluster names
        List<Map<String, Object>> clusters = new ArrayList<>();

        // Iterate through environments to find the cluster details
        for (Environments environment : environments) {
            if (environment.getClusterStatus().equals("active")) {
                Map<String, Object> clusterDetails = new HashMap<>();
                clusterDetails.put("clusterName", environment.getClusterName());
                clusterDetails.put("clusterId", environment.getClusterId());
                clusterDetails.put("clusterType", environment.getClusterType());
                clusterDetails.put("clusterUserName", environment.getClusterUsername());
                clusterDetails.put("hostUrl", environment.getHostUrl());
                clusterDetails.put("clusterPassword", environment.getClusterPassword());
                clusterDetails.put("openshiftClusterName", environment.getOpenshiftClusterName());
                clusterDetails.put("clusterStatus", environment.getClusterStatus());
                clusters.add(clusterDetails);
            }
        }

        if (!clusters.isEmpty()) {
            return Response.ok(clusters).build();

        } else {
            return null;
        }
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
            clusterDetails.put("clusterUserName", environment.getClusterUsername());
            clusterDetails.put("hostUrl", environment.getHostUrl());
            clusterDetails.put("clusterPassword", environment.getClusterPassword());
            clusterDetails.put("openshiftClusterName", environment.getOpenshiftClusterName());
            clusterDetails.put("clusterStatus", environment.getClusterStatus());
            clusters.add(clusterDetails);
        }

        if (!clusters.isEmpty()) {
            return Response.ok(clusters).build();

        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Clusters not found for username: " + username)
                    .build();
        }
    }

    @CacheResult(cacheName = "openshift-node-details")
    @Override
    public Response clusterNodeDetails(String username, String clustername, String nodename) {
        try {
            OpenShiftClient openShiftClient = commonClusterLogin(username, clustername);
            Response clusterInfo = viewClusterInfo(openShiftClient);
            Response clusterComponentStatus = viewClusterCondition(openShiftClient);
            Response clusterNetwork = viewClusterNetwork(openShiftClient);
            Response nodeIp = viewNodeIP(openShiftClient, nodename);
            Response nodeInventory = getNodes(username, clustername, nodename);
            Response cpuCapacity = viewClusterCapacity(openShiftClient, nodename);
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("clusterInfo", clusterInfo.getEntity());
            responseData.put("clusterStatus", clusterComponentStatus.getEntity());
            responseData.put("clusterNetwork", clusterNetwork.getEntity());
            responseData.put("clusterIP", nodeIp.getEntity());
            responseData.put("nodeInventory", nodeInventory.getEntity());
            responseData.put("cpuCapacity", cpuCapacity.getEntity());
            return Response.ok(responseData).build();
        } catch (Exception e) {
            return null;
        }
    }

    @CacheResult(cacheName = "view-cluster-capacity")
    @Override
    public Response viewClusterCapacity(OpenShiftClient openShiftClient, String nodename) {

        try {
            if (nodename.equals("ClusterMethod")) {
                nodename = null;
            }
            NodeList spec = openShiftClient.nodes().list();
            Gson gson = new Gson();
            JsonElement jsonElement = gson.toJsonTree(spec);
            JsonArray jsonArray = jsonElement.getAsJsonObject().get("items").getAsJsonArray();
            Integer cpuTotalAmount = 0;
            Long totalMemoryAmount = 0L;
            Long totalFileSystemAmount  = 0L;
            for (JsonElement jsonElement2 : jsonArray) {
                String nodeName = jsonElement2.getAsJsonObject().get("metadata").getAsJsonObject().get("name")
                        .getAsString();
                Integer totalCpu = jsonElement2.getAsJsonObject().get("status").getAsJsonObject().get("capacity")
                        .getAsJsonObject().get("cpu").getAsJsonObject().get("amount").getAsInt();
                Long totalMemory = jsonElement2.getAsJsonObject().get("status").getAsJsonObject().get("capacity")
                        .getAsJsonObject().get("memory").getAsJsonObject().get("amount").getAsLong();
                Long totalFileMemory = jsonElement2.getAsJsonObject().get("status").getAsJsonObject().get("capacity")
                        .getAsJsonObject().get("ephemeral-storage").getAsJsonObject().get("amount").getAsLong();
                if (nodename != null && nodename.equals(nodeName)) {
                    totalMemoryAmount += totalMemory;
                    cpuTotalAmount += totalCpu;
                    totalFileSystemAmount += totalFileMemory;
                    break;
                }
                if (nodename == null) {
                    totalMemoryAmount += totalMemory;
                    cpuTotalAmount += totalCpu;
                    totalFileSystemAmount += totalFileMemory;
                }
            }

            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("cpuTotalAmount", cpuTotalAmount);
            responseMap.put("memoryTotalAmount", totalMemoryAmount / (1024.0 * 1024.0));
            responseMap.put("memoryFileSystemAmount", totalFileSystemAmount / (1024.0 * 1024.0));
            return Response.ok(responseMap).build();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
