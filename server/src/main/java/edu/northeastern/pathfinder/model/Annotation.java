package edu.northeastern.pathfinder.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * EN: Community annotation left by NEU students on the map.
 * Categories: recommendation, warning, tip.
 * 中文：NEU 学生在地图上留下的社区标注。
 * 类别：recommendation（推荐）、warning（提醒）、tip（小贴士）。
 */
@Entity
@Table(name = "annotations")
public class Annotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(length = 100)
    private String author;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Annotation() {
    }

    public Annotation(double lat, double lon, String category, String text, String author) {
        this.lat = lat;
        this.lon = lon;
        this.category = category;
        this.text = text;
        this.author = author;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public String getCategory() { return category; }
    public String getText() { return text; }
    public String getAuthor() { return author; }
    public Instant getCreatedAt() { return createdAt; }
}
