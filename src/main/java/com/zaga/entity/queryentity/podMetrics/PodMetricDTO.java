package com.zaga.entity.queryentity.podMetrics;

import java.util.ArrayList;
import java.util.List;

// import com.zaga.entity.queryentity.metric.MetricDTO;

import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@MongoEntity(collection = "PodMetricDTO", database = "OtelPodMetrics")
public class PodMetricDTO {
    private List<MetricDTO> metrics = new ArrayList<>();
    private String clusterName;
    private String podName;
    private String namespaceName;
}
