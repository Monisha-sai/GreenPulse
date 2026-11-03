package com.greenpulse.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "servers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String hostname;

    private String ipAddress;

    private Double cpuUsage;

    private Double memoryUsage;

    private Double diskUsage;

    private Double networkIn;

    private Double networkOut;

    private Double powerConsumption;

    private String status;

    @Column(nullable = false)
    private String powerState = "ACTIVE";
}