package com.navercorp.pinpoint.otlp.collector.model;

// The Status type defines a logical error model that is suitable for different
// programming environments, including REST APIs and RPC APIs.

import io.opentelemetry.proto.trace.v1.Status;

public class OtlpTraceSpanStatus {
    // A developer-facing human readable error message.
    private final String message;

    // The status code.
    private final StatusCode code;

    public OtlpTraceSpanStatus(Builder builder) {
        this.message = builder.message;
        this.code = builder.code;
    }

    // For the semantics of status codes see
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#set-status
    enum StatusCode {
        // The default status.
        STATUS_CODE_UNSET,
        // The Span has been validated by an Application developer or Operator to
        // have completed successfully.
        STATUS_CODE_OK,
        // The Span contains an error.
        STATUS_CODE_ERROR,
        UNRECOGNIZED;

        public static StatusCode forNumber(int code) {
            switch (code) {
                case 0:
                    return STATUS_CODE_UNSET;
                case 1:
                    return STATUS_CODE_OK;
                case 2:
                    return STATUS_CODE_ERROR;
                default:
                    return UNRECOGNIZED;
            }
        }
    }


    public static class Builder {
        private String message;
        private StatusCode code;

        public OtlpTraceSpanStatus build() {
            return new OtlpTraceSpanStatus(this);
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public void setCode(int code) {
            this.code = StatusCode.forNumber(code);
        }
    }
}
