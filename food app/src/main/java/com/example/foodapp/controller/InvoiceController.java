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
    @ResponseBody
    public ResponseEntity<byte[]> invoicePdfHtml(@PathVariable Long id) {
        Order order = orderService.getOrderById(id);

        // 1) Render Thymeleaf HTML string (same data as your HTML invoice view)
        Context ctx = new Context();
        ctx.setVariable("order", order);
        String html = templateEngine.process("invoice", ctx); // templates/invoice.html

        // 2) Convert HTML to PDF
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(html, null)
                    .toStream(baos)
                    .run();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.inline()
                    .filename("invoice-" + id + ".pdf").build());
            return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .header(HttpHeaders.LOCATION, "/orders/" + id + "/invoice")
                    .build();
        }
    }

}
