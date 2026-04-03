//package com.from.domain;
//
//import jakarta.persistence.*;
//import lombok.*;
//import java.time.LocalDateTime;
//
//@Entity
//@Table(name = "imge_results")
//@Getter @Setter
//@NoArgsConstructor @AllArgsConstructor
//@Builder
//public class ImageResult {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long imageId;
//
//    @Column(nullable = false)
//    private Long bookId;
//
//    @Column(nullable = false)
//    private Long userId;
//
//    @Column(columnDefinition = "LONGTEXT")
//    private String imageUrl;
//
//    @Column(length = 100)
//    private String style;
//
//    @Column(updatable = false)
//    private LocalDateTime createAt;
//
//    @PrePersist
//    protected void onCreate() {
//        this.createAt = LocalDateTime.now();
//    }
//}
