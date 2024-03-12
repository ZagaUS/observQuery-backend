package com.zaga.entity.queryentity.podMetrics;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PodMetricsResponseData {
    // private String podName;
    private String namespaceName;
    private String clusterName;
    private List<PodMetricDTO> pods;
    // private List<MetricDTO> metrics  = new ArrayList<>();
    private int totalCount;
}