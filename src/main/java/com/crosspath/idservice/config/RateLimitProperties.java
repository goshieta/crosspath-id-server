package com.crosspath.idservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int perIpPerMinute = 10;
    private int perIpBurst = 20;
    private int newRegistrationsPerSecond = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPerIpPerMinute() {
        return perIpPerMinute;
    }

    public void setPerIpPerMinute(int perIpPerMinute) {
        this.perIpPerMinute = perIpPerMinute;
    }

    public int getPerIpBurst() {
        return perIpBurst;
    }

    public void setPerIpBurst(int perIpBurst) {
        this.perIpBurst = perIpBurst;
    }

    public int getNewRegistrationsPerSecond() {
        return newRegistrationsPerSecond;
    }

    public void setNewRegistrationsPerSecond(int newRegistrationsPerSecond) {
        this.newRegistrationsPerSecond = newRegistrationsPerSecond;
    }

}