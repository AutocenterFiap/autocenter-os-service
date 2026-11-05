package br.com.autocenterfiap.osservice.adapter.in.rest;

import br.com.autocenterfiap.osservice.application.exception.CadastroNaoEncontradoException;
import br.com.autocenterfiap.osservice.application.exception.OSJaEmAndamentoException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErroHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validacao(MethodArgumentNotValidException e) {
        var mensagem = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return resposta(HttpStatus.BAD_REQUEST, "requisicao_invalida", mensagem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> corpoIlegivel(HttpMessageNotReadableException e) {
        return resposta(HttpStatus.BAD_REQUEST, "requisicao_invalida", "Corpo da requisicao invalido");
    }

    @ExceptionHandler(CadastroNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> naoEncontrado(CadastroNaoEncontradoException e) {
        return resposta(HttpStatus.NOT_FOUND, "nao_encontrado", e.getMessage());
    }

    @ExceptionHandler(OSJaEmAndamentoException.class)
    public ResponseEntity<Map<String, String>> conflito(OSJaEmAndamentoException e) {
        return resposta(HttpStatus.CONFLICT, "os_em_andamento", e.getMessage());
    }

    private static ResponseEntity<Map<String, String>> resposta(HttpStatus status, String erro, String mensagem) {
        return ResponseEntity.status(status).body(Map.of("erro", erro, "mensagem", mensagem));
    }
}
