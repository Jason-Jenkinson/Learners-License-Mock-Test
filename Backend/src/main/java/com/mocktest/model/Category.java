package com.mocktest.model;

import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long categoryId;
    @Column(unique = true, nullable = false)
    public String categoryName;
}