package com.navercorp.pinpoint.otlp.collector.model;

import java.util.HashMap;
import java.util.Map;

// A pointer from the current span to another span in the same trace or in a
// different trace. For example, this can be used in batching operations,
// where a single batch handler processes multiple requests from different
// traces or when the handler receives a request from a different project.
public class OtlpTraceSpanLink {
    // A unique identifier of a trace that this linked span is part of. The ID is a
    // 16-byte array.
    private final byte[] traceId;

    // A unique identifier for the linked span. The ID is an 8-byte array.
    private final byte[] spanId;

    // The trace_state associated with the link.
    private final String traceState;

    // attributes is a collection of attribute key/value pairs on the link.
    // Attribute keys MUST be unique (it is not allowed to have more than one
    // attribute with the same key).
    private final Map<String, Object> attributes;

    // dropped_attributes_count is the number of dropped attributes. If the value is 0,
    // then no attributes were dropped.
    private final int droppedAttributesCount;

    // Flags, a bit field.
    //
    // Bits 0-7 (8 least significant bits) are the trace flags as defined in W3C Trace
    // Context specification. To read the 8-bit W3C trace flag, use
    // `flags & SPAN_FLAGS_TRACE_FLAGS_MASK`.
    //
    // See https://www.w3.org/TR/trace-context-2/#trace-flags for the flag definitions.
    //
    // Bits 8 and 9 represent the 3 states of whether the link is remote.
    // The states are (unknown, is not remote, is remote).
    // To read whether the value is known, use `(flags & SPAN_FLAGS_CONTEXT_HAS_IS_REMOTE_MASK) != 0`.
    // To read whether the link is remote, use `(flags & SPAN_FLAGS_CONTEXT_IS_REMOTE_MASK) != 0`.
    //
    // Readers MUST NOT assume that bits 10-31 (22 most significant bits) will be zero.
    // When creating new spans, bits 10-31 (most-significant 22-bits) MUST be zero.
    //
    // [Optional].
    private final float flags;

    public OtlpTraceSpanLink(Builder builder) {
        this.traceId = builder.traceId;
        this.spanId = builder.spanId;
        this.traceState = builder.traceState;
        this.attributes = builder.attributes;
        this.droppedAttributesCount = builder.droppedAttributesCount;
        this.flags = builder.flags;
    }

    public byte[] getTraceId() {
        return traceId;
    }

    public byte[] getSpanId() {
        return spanId;
    }

    public String getTraceState() {
        return traceState;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public int getDroppedAttributesCount() {
        return droppedAttributesCount;
    }

    public float getFlags() {
        return flags;
    }

    public static class Builder {
        private byte[] traceId;
        private byte[] spanId;
        private String traceState;
        private Map<String, Object> attributes = new HashMap<>();
        private int droppedAttributesCount;
        private float flags;

        public OtlpTraceSpanLink build() {
            return new OtlpTraceSpanLink(this);
        }

        public void setTraceId(byte[] traceId) {
            this.traceId = traceId;
        }

        public void setSpanId(byte[] spanId) {
            this.spanId = spanId;
        }

        public void setTraceState(String traceState) {
            this.traceState = traceState;
        }

        public void addAttribute(String key, String value) {
            this.attributes.put(key, value);
        }

        public void setDroppedAttributesCount(int droppedAttributesCount) {
            this.droppedAttributesCount = droppedAttributesCount;
        }

        public void setFlags(float flags) {
            this.flags = flags;
        }
    }
}
