/*
 * Copyright 2014 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.web.applicationmap.histogram;

import com.navercorp.pinpoint.common.trace.HistogramSchema;
import com.navercorp.pinpoint.common.trace.ServiceType;
import com.navercorp.pinpoint.common.trace.SlotType;
import com.navercorp.pinpoint.web.view.LoadTimeViewModel;
import com.navercorp.pinpoint.web.view.ResponseTimeViewModel;
import com.navercorp.pinpoint.web.vo.Application;
import com.navercorp.pinpoint.web.vo.Range;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author emeroad
 * @author jaehong.kim
 */
public class ApplicationTimeHistogram {
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    private static final long SIX_HOURS = TimeUnit.HOURS.toMillis(6);
    private static final long TWELVE_HOURS = TimeUnit.HOURS.toMillis(12);
    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final long TWO_DAYS = TimeUnit.DAYS.toMillis(2);
    private static final long MINUTE = TimeUnit.MINUTES.toMillis(1);
    private static final long FIVE_MINUTES = TimeUnit.MINUTES.toMillis(5);
    private static final long TEN_MINUTES = TimeUnit.MINUTES.toMillis(10);
    private static final long TWENTY_MINUTES = TimeUnit.MINUTES.toMillis(20);
    private static final long THIRTY_MINUTES = TimeUnit.MINUTES.toMillis(30);

    private final Application application;
    private final Range range;
    private final List<TimeHistogram> histogramList;

    public ApplicationTimeHistogram(Application application, Range range) {
        this(application, range, Collections.emptyList());
    }

    public ApplicationTimeHistogram(Application application, Range range, List<TimeHistogram> histogramList) {
        this.application = Objects.requireNonNull(application, "application");
        this.range = Objects.requireNonNull(range, "range");
        this.histogramList = Objects.requireNonNull(histogramList, "histogramList");
    }

    public List<ResponseTimeViewModel> createViewModel() {
        final List<ResponseTimeViewModel> value = new ArrayList<>(5);
        ServiceType serviceType = application.getServiceType();
        HistogramSchema schema = serviceType.getHistogramSchema();
        value.add(new ResponseTimeViewModel(schema.getFastSlot().getSlotName(), getColumnValue(SlotType.FAST)));
        value.add(new ResponseTimeViewModel(schema.getNormalSlot().getSlotName(), getColumnValue(SlotType.NORMAL)));
        value.add(new ResponseTimeViewModel(schema.getSlowSlot().getSlotName(), getColumnValue(SlotType.SLOW)));
        value.add(new ResponseTimeViewModel(schema.getVerySlowSlot().getSlotName(), getColumnValue(SlotType.VERY_SLOW)));
        value.add(new ResponseTimeViewModel(schema.getErrorSlot().getSlotName(), getColumnValue(SlotType.ERROR)));

        return value;
    }

    public List<ResponseTimeViewModel.TimeCount> getColumnValue(SlotType slotType) {
        List<ResponseTimeViewModel.TimeCount> result = new ArrayList<>(histogramList.size());
        for (TimeHistogram timeHistogram : histogramList) {
            final long timeStamp = timeHistogram.getTimeStamp();

            ResponseTimeViewModel.TimeCount TimeCount = new ResponseTimeViewModel.TimeCount(timeStamp, getCount(timeHistogram, slotType));
            result.add(TimeCount);
        }
        return result;
    }

    public long getCount(TimeHistogram timeHistogram, SlotType slotType) {
        return timeHistogram.getCount(slotType);
    }

    public List<LoadTimeViewModel> createLoadTimeViewModel() {
        final List<LoadTimeViewModel> loadTimeViewModelList = newLoadHistogramList();
        for (TimeHistogram timeHistogram : histogramList) {
            final long timestamp = timeHistogram.getTimeStamp();
            for (LoadTimeViewModel loadHistogram : loadTimeViewModelList) {
                if (timestamp <= loadHistogram.getTimestamp()) {
                    loadHistogram.addHistogram(timeHistogram);
                    break;
                }
            }
        }

        return loadTimeViewModelList;
    }

    private List<LoadTimeViewModel> newLoadHistogramList() {
        long termMillis = range.getTo() - range.getFrom();
        long intervalMillis;
        if (termMillis <= HOUR) {
            intervalMillis = MINUTE;
        } else if (termMillis <= SIX_HOURS) {
            intervalMillis = FIVE_MINUTES;
        } else if (termMillis <= TWELVE_HOURS) {
            intervalMillis = TEN_MINUTES;
        } else if (termMillis <= DAY) {
            intervalMillis = TWENTY_MINUTES;
        } else if (termMillis <= TWO_DAYS) {
            intervalMillis = THIRTY_MINUTES;
        } else {
            return Collections.emptyList();
        }

        if (termMillis < intervalMillis) {
            return Collections.emptyList();
        }

        final int count = (int) (termMillis / intervalMillis) + 1;
        final List<LoadTimeViewModel> loadTimeViewModelList = new ArrayList<>(count);
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(range.getFrom());
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long timestamp = calendar.getTimeInMillis();
        for (int i = 0; i < count; i++) {
            final Histogram histogram = new Histogram(this.application.getServiceType());
            loadTimeViewModelList.add(new LoadTimeViewModel(timestamp, new LoadHistogram(histogram)));
            timestamp += intervalMillis;
        }

        return loadTimeViewModelList;
    }
}