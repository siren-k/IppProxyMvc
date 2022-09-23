package com.example.ippproxymvc.config;

import com.example.ippproxymvc.web.servlets.IppProxyServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServletConfig {
    @Bean
    public ServletRegistrationBean ippProxyServletRegistrationBean() {
        return new ServletRegistrationBean(new IppProxyServlet(), "/printers/*");
    }
}
