package com.example.foodapp.model;// src/main/java/com/example/foodapp/dto/ChatProductDTO.java


public class ChatProductDTO {
    private Long id;
    private String name;
    private String price;
    private String imageUrl;

    public ChatProductDTO() {}

    public ChatProductDTO(Long id, String name, String price, String imageUrl) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.imageUrl = imageUrl;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getPrice() {
        return price;
    }
    public void setPrice(String price) {
        this.price = price;
    }

    public String getImageUrl() {
        return imageUrl;
    }
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
