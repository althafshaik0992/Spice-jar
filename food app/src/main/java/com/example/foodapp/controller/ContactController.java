package com.example.foodapp.controller;

import com.example.foodapp.model.ContactForm;
import com.example.foodapp.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.mail.MessagingException;
import java.io.UnsupportedEncodingException;


@Controller
public class ContactController {



    @Autowired
    private ContactService contactService;



    @GetMapping("/contact")
    public String contact(@RequestParam(required = false) String msg,
                          @RequestParam(required = false) String error,
                          Model m) {
        m.addAttribute("form", new ContactForm());
        if (msg != null) m.addAttribute("msg", msg);
        if (error != null) m.addAttribute("error", error);
        return "contact";
    }

    // POST submit
    @PostMapping("/contact")
    public String submit(@Valid @ModelAttribute("form") ContactForm form,
                         BindingResult br,
                         Model m) throws Exception {
        if (br.hasErrors()) {
            m.addAttribute("error", "Please correct the highlighted fields.");
            return "contact";
        }
        String ticketId = contactService.sendContact(form);


        contactService.sendContact(form);
        System.out.printf("CONTACT: %s <%s> [%s] topic=%s%n%s%n",
                form.getName(), form.getEmail(), form.getPhone(), form.getTopic(), form.getMessage());
        System.out.println(" message " + new RuntimeException());
        System.out.println("email sent");

        return "redirect:/contact?msg=Thanks!+We%E2%80%99ll+get+back+to+you+soon.+And +Your+ticket+ID+is+ "+ ticketId;
    }
}
