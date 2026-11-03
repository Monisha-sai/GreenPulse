
package com.greenpulse.backend.controller;

import com.greenpulse.backend.entity.Server;
import com.greenpulse.backend.service.ServerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/servers")
@CrossOrigin
public class ServerController {

    private final ServerService serverService;

    public ServerController(ServerService serverService) {
        this.serverService = serverService;
    }

    @GetMapping
    public List<Server> getAllServers() {
        return serverService.getAllServers();
    }

    @GetMapping("/{id}")
    public Server getServerById(@PathVariable Long id) {
        return serverService.getServerById(id);
    }

    @PostMapping
    public Server createServer(@RequestBody Server server) {
        return serverService.saveServer(server);
    }

    @PutMapping("/{id}")
    public Server updateServer(
            @PathVariable Long id,
            @RequestBody Server updatedServer) {

        Server existingServer = serverService.getServerById(id);

        if (updatedServer.getPowerState() != null
                && !updatedServer.getPowerState().isBlank()) {
            existingServer.setPowerState(updatedServer.getPowerState());
        }

        return serverService.saveServer(existingServer);
    }

    @DeleteMapping("/{id}")
    public void deleteServer(@PathVariable Long id) {
        serverService.deleteServer(id);
    }
}
