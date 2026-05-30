package org.example.integration.hue.model;

/** Simplified view of a Hue v2 light resource returned by GET /clip/v2/resource/light */
public class HueLightState {

    private String id;
    private String name;
    private boolean on;
    private double brightness;   // 0.0–100.0

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isOn() { return on; }
    public void setOn(boolean on) { this.on = on; }

    public double getBrightness() { return brightness; }
    public void setBrightness(double brightness) { this.brightness = brightness; }
}
