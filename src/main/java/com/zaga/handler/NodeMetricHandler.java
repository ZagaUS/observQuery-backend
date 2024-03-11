package com.zaga.handler;

import java.util.Collections;
import java.util.List;

import com.zaga.entity.queryentity.node.NodeMetricDTO;
import com.zaga.repo.NodeDTORepo;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class NodeMetricHandler {

    @Inject
    NodeDTORepo nodeDTORepo;

    // public List<NodeMetricDTO> getAllNodeMetricData(String nodeName, String OPENSHIFTCLUSTERNAME) {
        
    //     return nodeDTORepo.listAll();
    // }
    
    public List<NodeMetricDTO> getAllNodeMetricData(String nodeName, String clusterName) {
        if (clusterName != null && !clusterName.isEmpty()) {
            if (nodeName != null && !nodeName.isEmpty()) {
                return nodeDTORepo.find("nodeName = ?1 and clusterName = ?2", nodeName, clusterName).list();
            } else {
                return nodeDTORepo.find("clusterName = ?1", clusterName).list();
            }
        }
        return Collections.emptyList();
    }
}
