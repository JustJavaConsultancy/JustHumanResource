package com.justjava.humanresource.request.handler;
import com.justjava.humanresource.request.enums.RequestType;
import org.springframework.stereotype.Component;
import java.util.*;
@Component public class WorkflowRequestHandlerRegistry {
 private final Map<RequestType,WorkflowRequestTypeHandler> handlers;
 public WorkflowRequestHandlerRegistry(List<WorkflowRequestTypeHandler> list) { Map<RequestType,WorkflowRequestTypeHandler> map=new EnumMap<>(RequestType.class); list.forEach(h->{ if(map.put(h.supportedType(),h)!=null) throw new IllegalStateException("Duplicate request handler: "+h.supportedType()); }); handlers=Map.copyOf(map); }
 public WorkflowRequestTypeHandler get(RequestType type) { WorkflowRequestTypeHandler h=handlers.get(type); if(h==null) throw new IllegalArgumentException("Unsupported request type: "+type); return h; }
}
