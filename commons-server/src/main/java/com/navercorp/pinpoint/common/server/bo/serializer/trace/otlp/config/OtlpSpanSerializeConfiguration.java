package com.navercorp.pinpoint.common.server.bo.serializer.trace.otlp.config;

import com.navercorp.pinpoint.common.hbase.wd.RowKeyDistributor;
import com.navercorp.pinpoint.common.server.bo.serializer.trace.otlp.OtlpTraceRowKeyEncoderV2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OtlpSpanSerializeConfiguration {


    @Bean
    public OtlpTraceRowKeyEncoderV2 otlpTraceRowKeyEncoderV2(@Qualifier("traceV2Distributor") RowKeyDistributor rowKeyDistributor) {
        return new OtlpTraceRowKeyEncoderV2(rowKeyDistributor);
    }
}
