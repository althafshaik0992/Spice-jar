package com.example.foodapp.service;

import com.example.foodapp.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
@Service
public class AnalyticsService {
    private final OrderRepository orderRepo;
    public AnalyticsService(OrderRepository orderRepo){ this.orderRepo = orderRepo; }
    public Map<String, Long> dailyOrdersLast7Days(){
        Map<String, Long> result = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for(int i=6;i>=0;i--){
            LocalDate d = today.minusDays(i);
            long count = orderRepo.findByCreatedAtBetween(d.atStartOfDay(), d.atTime(23,59,59)).size();
            result.put(d.getMonthValue()+"/"+d.getDayOfMonth(), count);
        }
        return result;
    }
}
