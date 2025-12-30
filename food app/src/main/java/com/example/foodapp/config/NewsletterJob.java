// src/main/java/com/example/foodapp/jobs/NewsletterJob.java
package com.example.foodapp.config;

import com.example.foodapp.model.Subscriber;
import com.example.foodapp.repository.SubscriberRepository;
import com.example.foodapp.service.EmailService;
import com.example.foodapp.service.AnalyticsService; // your top products method
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class NewsletterJob {
    private final SubscriberRepository subs;
    private final AnalyticsService analytics;
    private final EmailService mail;

    public NewsletterJob(SubscriberRepository subs, @Qualifier("analyticsServiceImpl") AnalyticsService analytics, EmailService mail) {
        this.subs = subs;
        this.analytics = analytics;
        this.mail = mail;
    }


    // Every Monday 10:00am server time
    @Scheduled(cron = "0 0 10 * * MON")
    public void weeklyDigest() {
        List<?> products = analytics.topSellers(4, 30);
        for (Subscriber s : subs.findByStatus(Subscriber.Status.ACTIVE)) {
            String unsub = "{{your-site}}/newsletter/unsubscribe?token=" + s.getUnsubToken();
            mail.sendTemplate(s.getEmail(), "Fresh Spice Picks This Week",
                    "email/newsletter", Map.of("products", products, "unsubLink", unsub));
        }
    }
}
