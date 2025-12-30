// com/example/foodapp/controller/InvoiceController.java
package com.example.foodapp.controller;

import com.example.foodapp.model.Order;
import com.example.foodapp.service.OrderService;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;

@Controller
public class InvoiceController {

    private final OrderService orderService;
    private final TemplateEngine templateEngine;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public InvoiceController(OrderService orderService, TemplateEngine templateEngine) {
        this.orderService = orderService;
        this.templateEngine = templateEngine;
    }

    /**
     * HTML preview
     */
    @GetMapping("/orders/{id}/invoice")
    public String invoiceHtml(@PathVariable Long id, Model model) {
        Order order = orderService.getOrderById(id);
        model.addAttribute("order", order);
        model.addAttribute("baseUrl", baseUrl);
        return "invoice"; // templates/invoice.html
    }

    /**
     * PDF download
     */


    @GetMapping(value = "/orders/{id}/invoice.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> invoicePdf(@PathVariable Long id) {
        Order order = orderService.getOrderById(id);

        // Render Thymeleaf HTML to string
        Context ctx = new Context();
        ctx.setVariable("order", order);
        ctx.setVariable("baseUrl", baseUrl); // if you use it in HTML for absolute urls

        String html = templateEngine.process("invoice", ctx);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();

            // ✅ IMPORTANT: give baseUri so relative resources can be resolved
            // Example: http://localhost:8080  (or your deployed domain)
            builder.withHtmlContent(html, baseUrl);

            builder.toStream(baos);
            builder.run();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);

            // ✅ Download as file
            headers.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename("invoice-" + order.getConfirmationNumber() + ".pdf")
                            .build()
            );

            // Optional but nice
            headers.setCacheControl(CacheControl.noStore());

            return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);

        } catch (Exception e) {
            // ✅ Don’t redirect for PDF downloads; return a real error
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("PDF generation failed: " + e.getMessage()).getBytes());
        }
    }


}
