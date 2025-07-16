package com.navercorp.pinpoint.otlp.collector.mapper;

import com.navercorp.pinpoint.otlp.collector.model.OtlpTraceSpanStatus;
import org.springframework.stereotype.Component;

@Component
public class OtlpTraceSpanStatusMapper {

    public OtlpTraceSpanStatus mapper(io.opentelemetry.proto.trace.v1.Status status) {
        if (status == null) {
            return null;
        }

        OtlpTraceSpanStatus.Builder builder = new OtlpTraceSpanStatus.Builder();
        builder.setMessage(status.getMessage());
        builder.setCode(status.getCode().getNumber());

        return builder.build();
    }
}
