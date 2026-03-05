The Spice Jar is a full-stack Spring Boot web application designed for managing and ordering spice products.
It supports product browsing, cart management, admin operations, and a clean UI built with Thymeleaf.

This project demonstrates end-to-end Java web development using modern Spring practices.

🚀 Tech Stack
Backend

Java 17

Spring Boot

Spring MVC

Spring Data JPA

Hibernate

SQLite (lightweight & cost-effective)

Maven

Frontend

Thymeleaf

HTML5 / CSS3

Bootstrap / Tailwind (if applicable)

JavaScript

Dev & Deployment

Docker

Docker Hub

Git

AWS EC2

GoDaddy (Domain hosting)

✨ Features
User Features

Browse spice products

View product details with images

Add products to cart

Place orders

Responsive UI

Admin Features

Admin dashboard

Add / update / delete products

Manage orders

Upload product images

Technical Highlights

MVC architecture

Clean controller–service–repository layering

Thymeleaf template rendering

Dockerized application for easy deployment

Run Locally (Without Docker)
git clone https://github.com/<your-username>/spice-jar.git
cd spice-jar
mvn clean install
mvn spring-boot:run


Access the app at:

http://localhost:8080