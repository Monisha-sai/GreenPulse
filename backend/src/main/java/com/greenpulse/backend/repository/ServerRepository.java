package com.greenpulse.backend.repository;

import com.greenpulse.backend.entity.Server;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServerRepository extends JpaRepository<Server, Long> {
}
