package com.zaga.handler;

import java.util.List;

import com.zaga.entity.queryentity.node.NodeMetricDTO;
import com.zaga.repo.NodeDTORepo;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class NodeMetricHandler {

    @Inject
    NodeDTORepo nodeDTORepo;

    public List<NodeMetricDTO> getAllNodeMetricData(String nodeName, String OPENSHIFTCLUSTERNAME) {
        return nodeDTORepo.listAll();
    }
    
    
}
