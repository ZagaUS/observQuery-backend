package com.zaga.entity.otelevent;


import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResourceLogs {
    private List<ScopeLogs> scopeLogs;
    private Resource resource;
    private String schemaUrl;
}
