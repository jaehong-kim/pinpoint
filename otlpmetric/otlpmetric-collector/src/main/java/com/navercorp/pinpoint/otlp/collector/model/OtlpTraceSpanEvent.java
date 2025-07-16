package com.navercorp.pinpoint.otlp.collector.model;

import java.util.HashMap;
import java.util.Map;

public class OtlpTraceSpanEvent {

    // time_unix_nano is the time the event occurred.
    private final long timeUnixNano;

    // name of the event.
    // This field is semantically required to be set to non-empty string.
    private final String name;

    // attributes is a collection of attribute key/value pairs on the event.
    // Attribute keys MUST be unique (it is not allowed to have more than one
    // attribute with the same key).
    private final Map<String, Object> attributes;

    // dropped_attributes_count is the number of dropped attributes. If the value is 0,
    // then no attributes were dropped.
    private final int droppedAttributesCount;

    public OtlpTraceSpanEvent(Builder builder) {
        this.timeUnixNano = builder.timeUnixNano;
        this.name = builder.name;
        this.attributes = builder.attributes;
        this.droppedAttributesCount = builder.droppedAttributesCount;
    }

    public long getTimeUnixNano() {
        return timeUnixNano;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public int getDroppedAttributesCount() {
        return droppedAttributesCount;
    }

    public static class Builder {
        private long timeUnixNano;
        private String name;
        private Map<String, Object> attributes = new HashMap<>();
        private int droppedAttributesCount;

        public OtlpTraceSpanEvent build() {
            return new OtlpTraceSpanEvent(this);
        }

        public void setTimeUnixNano(long timeUnixNano) {
            this.timeUnixNano = timeUnixNano;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void addAttribute(String key, String value) {
            this.attributes.put(key, value);
        }

        public void setDroppedAttributesCount(int droppedAttributesCount) {
            this.droppedAttributesCount = droppedAttributesCount;
        }
    }
}
