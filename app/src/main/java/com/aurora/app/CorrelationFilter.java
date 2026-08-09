package com.aurora.app;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;
@Component
public class CorrelationFilter extends OncePerRequestFilter {
  @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String id=req.getHeader("X-Correlation-Id"); if(id==null||id.isBlank()) id=UUID.randomUUID().toString();
    MDC.put("correlationId",id); res.setHeader("X-Correlation-Id",id);
    try{chain.doFilter(req,res);}finally{MDC.remove("correlationId");}
  }
}
