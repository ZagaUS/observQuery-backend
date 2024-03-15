package com.zaga.entity.queryentity.cluster_utilization.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ClusterResponse {
    private double cpuUsage;
    private double memoryUsage;
    private double memoryAvailable;
    // private double fileSystemCapacity;
    private double fileSystemUsage;
    private double fileSystemAvailable;
    private Integer cpuCapacity;
    private Float memoryCapacity;
    private Float fileSystemCapacity;
}
