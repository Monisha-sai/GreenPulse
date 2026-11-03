package com.greenpulse.backend.service;

import com.greenpulse.backend.entity.Server;
import com.greenpulse.backend.repository.ServerRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ServerService {

    private final ServerRepository serverRepository;

    public ServerService(ServerRepository serverRepository) {
        this.serverRepository = serverRepository;
    }

    public List<Server> getAllServers() {
        return serverRepository.findAll();
    }

    public Server getServerById(Long id) {
        return serverRepository.findById(id).orElse(null);
    }

    public Server saveServer(Server server) {
        return serverRepository.save(server);
    }

    public void deleteServer(Long id) {
        serverRepository.deleteById(id);
    }
}
