package org.example.integration.hue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hue.bridge")
public class HueProperties {

    private String ip;
    private String applicationKey;
    private boolean trustAllCerts = true;

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getApplicationKey() { return applicationKey; }
    public void setApplicationKey(String applicationKey) { this.applicationKey = applicationKey; }

    public boolean isTrustAllCerts() { return trustAllCerts; }
    public void setTrustAllCerts(boolean trustAllCerts) { this.trustAllCerts = trustAllCerts; }
}
