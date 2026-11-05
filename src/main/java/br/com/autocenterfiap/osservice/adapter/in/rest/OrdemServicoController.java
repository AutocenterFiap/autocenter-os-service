package br.com.autocenterfiap.osservice.adapter.in.rest;

import br.com.autocenterfiap.osservice.adapter.in.rest.dto.AbrirOSRequest;
import br.com.autocenterfiap.osservice.adapter.in.rest.dto.OSResponse;
import br.com.autocenterfiap.osservice.application.usecase.AbrirOrdemServicoUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/ordem-servicos")
public class OrdemServicoController {

    private final AbrirOrdemServicoUseCase abrir;

    public OrdemServicoController(AbrirOrdemServicoUseCase abrir) {
        this.abrir = abrir;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OSResponse abrir(@Valid @RequestBody AbrirOSRequest req) {
        var os = abrir.executar(req.clienteId(), req.veiculoId(), req.problemaRelatado());
        return OSResponse.de(os, null);
    }
}
