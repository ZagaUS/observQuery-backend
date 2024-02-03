package com.zaga.entity.oteltrace;

// import java.util.Collection;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceSpans {
  private Resource resource;
  private List<ScopeSpans> scopeSpans;
  private String schemaUrl;

}
