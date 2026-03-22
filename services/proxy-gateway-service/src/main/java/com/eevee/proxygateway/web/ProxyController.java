package com.eevee.proxygateway.web;

import com.eevee.proxygateway.relay.ProxyRelayService;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class ProxyController {

    private final ProxyRelayService proxyRelayService;

    public ProxyController(ProxyRelayService proxyRelayService) {
        this.proxyRelayService = proxyRelayService;
    }

    @RequestMapping(value = "/proxy/**", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
            RequestMethod.DELETE, RequestMethod.HEAD, RequestMethod.OPTIONS
    })
    public Mono<ResponseEntity<Flux<DataBuffer>>> proxy(ServerWebExchange exchange) {
        return proxyRelayService.relay(exchange);
    }
}
