package com.example.foodapp.service;

import com.example.foodapp.model.DashboardMetrics;
import com.example.foodapp.model.DayBucket;
import com.example.foodapp.model.RecentOrderDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
@Service
public interface AnalyticsService {

//        Map<String, Long> result = new LinkedHashMap<>();
//        LocalDate today = LocalDate.now();
//        for(int i=6;i>=0;i--){
//            LocalDate d = today.minusDays(i);
//            long count = orderRepo.findByCreatedAtBetween(d.atStartOfDay(), d.atTime(23,59,59)).size();
//            result.put(d.getMonthValue()+"/"+d.getDayOfMonth(), count);
//        }
//        return result;
//    }

    DashboardMetrics getDashboardMetrics();

    /** Last N days, inclusive */
    List<DayBucket> ordersByDay(int lastNDays);

    TopProducts topProducts(int limit);

    List<RecentOrderDTO> recentOrders(int limit);

    AnalyticsService.Series revenueSeries(LocalDate from, LocalDate to);


    record Series(List<String> labels, List<BigDecimal> values) {}
    record TopProducts(List<String> names, List<Long> qty) {}

    Map<String, Object> kpis();
}
