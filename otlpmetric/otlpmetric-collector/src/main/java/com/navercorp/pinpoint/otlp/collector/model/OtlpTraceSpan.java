package com.navercorp.pinpoint.otlp.collector.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OtlpTraceSpan {
    // A unique identifier for a trace. All spans from the same trace share
    // the same `trace_id`. The ID is a 16-byte array. An ID with all zeroes OR
    // of length other than 16 bytes is considered invalid (empty string in OTLP/JSON
    // is zero-length and thus is also invalid).
    //
    // This field is required.
    private final byte[] traceId;

    // A unique identifier for a span within a trace, assigned when the span
    // is created. The ID is an 8-byte array. An ID with all zeroes OR of length
    // other than 8 bytes is considered invalid (empty string in OTLP/JSON
    // is zero-length and thus is also invalid).
    //
    // This field is required.
    private final byte[] spanId;

    // trace_state conveys information about request position in multiple distributed tracing graphs.
    // It is a trace_state in w3c-trace-context format: https://www.w3.org/TR/trace-context/#tracestate-header
    // See also https://github.com/w3c/distributed-tracing for more details about this field.
    private final String traceState;

    // The `span_id` of this span's parent span. If this is a root span, then this
    // field must be empty. The ID is an 8-byte array.
    private final byte[] parentSpanId;

    // Flags, a bit field.
    //
    // Bits 0-7 (8 least significant bits) are the trace flags as defined in W3C Trace
    // Context specification. To read the 8-bit W3C trace flag, use
    // `flags & SPAN_FLAGS_TRACE_FLAGS_MASK`.
    //
    // See https://www.w3.org/TR/trace-context-2/#trace-flags for the flag definitions.
    //
    // Bits 8 and 9 represent the 3 states of whether a span's parent
    // is remote. The states are (unknown, is not remote, is remote).
    // To read whether the value is known, use `(flags & SPAN_FLAGS_CONTEXT_HAS_IS_REMOTE_MASK) != 0`.
    // To read whether the span is remote, use `(flags & SPAN_FLAGS_CONTEXT_IS_REMOTE_MASK) != 0`.
    //
    // When creating span messages, if the message is logically forwarded from another source
    // with an equivalent flags fields (i.e., usually another OTLP span message), the field SHOULD
    // be copied as-is. If creating from a source that does not have an equivalent flags field
    // (such as a runtime representation of an OpenTelemetry span), the high 22 bits MUST
    // be set to zero.
    // Readers MUST NOT assume that bits 10-31 (22 most significant bits) will be zero.
    //
    // [Optional].
    private final float flags;

    // A description of the span's operation.
    //
    // For example, the name can be a qualified method name or a file name
    // and a line number where the operation is called. A best practice is to use
    // the same display name at the same call point in an application.
    // This makes it easier to correlate spans in different traces.
    //
    // This field is semantically required to be set to non-empty string.
    // Empty value is equivalent to an unknown span name.
    //
    // This field is required.
    private final String name;

    // Distinguishes between spans generated in a particular context. For example,
    // two spans with the same name may be distinguished using `CLIENT` (caller)
    // and `SERVER` (callee) to identify queueing latency associated with the span.
    private final SpanKind spanKind;

    // start_time_unix_nano is the start time of the span. On the client side, this is the time
    // kept by the local machine where the span execution starts. On the server side, this
    // is the time when the server's application handler starts running.
    // Value is UNIX Epoch time in nanoseconds since 00:00:00 UTC on 1 January 1970.
    //
    // This field is semantically required and it is expected that end_time >= start_time.
    private final long startTimeUnixNano;

    // end_time_unix_nano is the end time of the span. On the client side, this is the time
    // kept by the local machine where the span execution ends. On the server side, this
    // is the time when the server application handler stops running.
    // Value is UNIX Epoch time in nanoseconds since 00:00:00 UTC on 1 January 1970.
    //
    // This field is semantically required and it is expected that end_time >= start_time.
    private final long endTimeUnixNano;

    // attributes is a collection of key/value pairs. Note, global attributes
    // like server name can be set using the resource API. Examples of attributes:
    //
    //     "/http/user_agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36"
    //     "/http/server_latency": 300
    //     "example.com/myattribute": true
    //     "example.com/score": 10.239
    //
    // The OpenTelemetry API specification further restricts the allowed value types:
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/common/README.md#attribute
    // Attribute keys MUST be unique (it is not allowed to have more than one
    // attribute with the same key).
    private final Map<String, Object> attributes;

    // dropped_attributes_count is the number of attributes that were discarded. Attributes
    // can be discarded because their keys are too long or because there are too many
    // attributes. If this value is 0, then no attributes were dropped.
    private final int droppedAttributesCount;

    // events is a collection of Event items.
    private final List<OtlpTraceSpanEvent> events;

    // dropped_events_count is the number of dropped events. If the value is 0, then no
    // events were dropped.
    private final int droppedEventsCount;

    // links is a collection of Links, which are references from this span to a span
    // in the same or different trace.
    private final List<OtlpTraceSpanLink> links;

    // dropped_links_count is the number of dropped links after the maximum size was
    // enforced. If this value is 0, then no links were dropped.
    private final int dropedLinksCount;

    // An optional final status for this span. Semantically when Status isn't set, it means
    // span's status code is unset, i.e. assume STATUS_CODE_UNSET (code = 0).
    private final OtlpTraceSpanStatus status;

    private final long collectorAcceptTime;
    private final String agentId;
    private final String applicationName;

    public OtlpTraceSpan(Builder builder) {
        this.traceId = builder.traceId;
        this.spanId = builder.spanId;
        this.traceState = builder.traceState;
        this.parentSpanId = builder.parentSpanId;
        this.flags = builder.flags;
        this.name = builder.name;
        this.spanKind = builder.spanKind;
        this.startTimeUnixNano = builder.startTimeUnixNano;
        this.endTimeUnixNano = builder.endTimeUnixNano;
        this.attributes = builder.attributes;
        this.droppedAttributesCount = builder.droppedAttributesCount;
        this.events = builder.events;
        this.droppedEventsCount = builder.droppedEventsCount;
        this.links = builder.links;
        this.dropedLinksCount = builder.dropedLinksCount;
        this.status = builder.status;
        this.collectorAcceptTime = builder.collectorAcceptTime;
        this.agentId = builder.agentId;
        this.applicationName = builder.applicationName;
    }

    public byte[] getTraceId() {
        return traceId;
    }

    public long getCollectorAcceptTime() {
        return collectorAcceptTime;
    }


    // SpanKind is the type of span. Can be used to specify additional relationships between spans
    // in addition to a parent/child relationship.
    enum SpanKind {
        // Unspecified. Do NOT use as default.
        // Implementations MAY assume SpanKind to be INTERNAL when receiving UNSPECIFIED.
        SPAN_KIND_UNSPECIFIED,

        // Indicates that the span represents an internal operation within an application,
        // as opposed to an operation happening at the boundaries. Default value.
        SPAN_KIND_INTERNAL,

        // Indicates that the span covers server-side handling of an RPC or other
        // remote network request.
        SPAN_KIND_SERVER,

        // Indicates that the span describes a request to some remote service.
        SPAN_KIND_CLIENT,

        // Indicates that the span describes a producer sending a message to a broker.
        // Unlike CLIENT and SERVER, there is often no direct critical path latency relationship
        // between producer and consumer spans. A PRODUCER span ends when the message was accepted
        // by the broker while the logical processing of the message might span a much longer time.
        SPAN_KIND_PRODUCER,

        // Indicates that the span describes consumer receiving a message from a broker.
        // Like the PRODUCER kind, there is often no direct critical path latency relationship
        // between producer and consumer spans.
        SPAN_KIND_CONSUMER;

        public static SpanKind forNumber(int code) {
            switch (code) {
                case 0:
                    return SPAN_KIND_UNSPECIFIED;
                case 1:
                    return SPAN_KIND_INTERNAL;
                case 2:
                    return SPAN_KIND_SERVER;
                case 3:
                    return SPAN_KIND_CLIENT;
                case 4:
                    return SPAN_KIND_PRODUCER;
                case 5:
                    return SPAN_KIND_CONSUMER;
                default:
                    throw new IllegalArgumentException("Unknown SpanKind code: " + code);
            }
        }
    }

    public static class Builder {
        private byte[] traceId;
        private byte[] spanId;
        private String traceState;
        private byte[] parentSpanId;
        private float flags;
        private String name;
        private SpanKind spanKind;
        private long startTimeUnixNano;
        private long endTimeUnixNano;
        private Map<String, Object> attributes = new HashMap<>(16);
        private int droppedAttributesCount;
        private List<OtlpTraceSpanEvent> events = new ArrayList<>(16);
        private int droppedEventsCount;
        private List<OtlpTraceSpanLink> links = new ArrayList<>(16);
        private int dropedLinksCount;
        private OtlpTraceSpanStatus status;
        private long collectorAcceptTime;
        private String agentId;
        private String applicationName;

        public OtlpTraceSpan build() {
            return new OtlpTraceSpan(this);
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

        public void setParentSpanId(byte[] parentSpanId) {
            this.parentSpanId = parentSpanId;
        }

        public void setFlags(float flags) {
            this.flags = flags;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setSpanKind(int spanKindNumber) {
            this.spanKind = SpanKind.forNumber(spanKindNumber);
        }

        public void setStartTimeUnixNano(long startTimeUnixNano) {
            this.startTimeUnixNano = startTimeUnixNano;
        }

        public void setEndTimeUnixNano(long endTimeUnixNano) {
            this.endTimeUnixNano = endTimeUnixNano;
        }

        public void addAttribute(String key, String value) {
            this.attributes.put(key, value);
        }

        public void setDroppedAttributesCount(int droppedAttributesCount) {
            this.droppedAttributesCount = droppedAttributesCount;
        }

        public void addEvent(OtlpTraceSpanEvent event) {
            this.events.add(event);
        }

        public void setDroppedEventsCount(int droppedEventsCount) {
            this.droppedEventsCount = droppedEventsCount;
        }

        public void addLink(OtlpTraceSpanLink link) {
            this.links.add(link);
        }

        public void setDropedLinksCount(int dropedLinksCount) {
            this.dropedLinksCount = dropedLinksCount;
        }

        public void setStatus(OtlpTraceSpanStatus status) {
            this.status = status;
        }

        public void setCollectorAcceptTime(long collectorAcceptTime) {
            this.collectorAcceptTime = collectorAcceptTime;
        }

        public void setAgentId(String agentId) {
            this.agentId = agentId;
        }

        public void setApplicationName(String applicationName) {
            this.applicationName = applicationName;
        }
    }
}
