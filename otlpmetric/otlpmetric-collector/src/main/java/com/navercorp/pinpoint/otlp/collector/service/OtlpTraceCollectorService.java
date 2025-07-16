package com.navercorp.pinpoint.otlp.collector.service;

import com.navercorp.pinpoint.otlp.collector.model.OtlpMetricData;
import com.navercorp.pinpoint.otlp.collector.model.OtlpTraceSpan;
import jakarta.validation.Valid;

public interface OtlpTraceCollectorService {
    void save(@Valid OtlpTraceSpan otlpTraceSpan);
}
