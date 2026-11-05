package br.com.autocenterfiap.osservice.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.autocenterfiap.osservice.ContainersConfig;
import br.com.autocenterfiap.osservice.application.port.HistoricoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.OrdemServicoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.SagaRepositoryPort;
import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import br.com.autocenterfiap.osservice.domain.saga.RegistroHistorico;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "sistema.seguranca.chave.secreta=chave-de-teste")
@Import(ContainersConfig.class)
class OrdemServicoApiIT {

    private static final String URL = "/v1/ordem-servicos";
    private static final AtomicLong VEICULO = new AtomicLong(50_000);

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OrdemServicoRepositoryPort ordens;
    @Autowired
    private SagaRepositoryPort sagas;
    @Autowired
    private HistoricoRepositoryPort historico;

    private OrdemServico criar(StatusOS status) {
        var agora = Instant.now();
        var os = ordens.salvar(OrdemServico.abrir(1L, VEICULO.incrementAndGet(), "Problema", UUID.randomUUID(), agora));
        if (status != StatusOS.ABERTA) {
            os.mudarStatus(status, agora);
            os = ordens.salvar(os);
        }
        historico.registrar(new RegistroHistorico(os.getId(), null, StatusOS.ABERTA, "os.criada", null, agora));
        return os;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapa(Object corpo) {
        return (Map<String, Object>) corpo;
    }

    @Test
    @SuppressWarnings("unchecked")
    void listaPaginadaFiltraPorStatus() {
        criar(StatusOS.CONCLUIDA);
        criar(StatusOS.CONCLUIDA);
        criar(StatusOS.ABERTA);

        var r = rest.getForEntity(URL + "?status=CONCLUIDA&tamanho=1&pagina=0", Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        var json = mapa(r.getBody());
        assertThat((List<Map<String, Object>>) json.get("conteudo")).hasSize(1)
                .allSatisfy(os -> assertThat(os.get("status")).isEqualTo("CONCLUIDA"));
        assertThat(((Number) json.get("totalElementos")).longValue()).isGreaterThanOrEqualTo(2L);
        assertThat(json.get("tamanho")).isEqualTo(1);
    }

    @Test
    void tamanhoAcimaDe100ELimitado() {
        var r = rest.getForEntity(URL + "?tamanho=500", Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapa(r.getBody()).get("tamanho")).isEqualTo(100);
        assertThat(mapa(rest.getForEntity(URL, Map.class).getBody()).get("tamanho")).isEqualTo(20);
    }

    @Test
    void statusInvalidoNoFiltroRetorna400() {
        var r = rest.getForEntity(URL + "?status=INEXISTENTE", Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapa(r.getBody())).containsKeys("erro", "mensagem");
    }

    @Test
    void buscaPorIdENumeroEStatus() {
        var os = criar(StatusOS.ABERTA);

        var porId = rest.getForEntity(URL + "/" + os.getId(), Map.class);
        var porNumero = rest.getForEntity(URL + "/numero/" + os.getNumero(), Map.class);
        var status = rest.getForEntity(URL + "/status/" + os.getId(), Map.class);

        assertThat(porId.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapa(porId.getBody()).get("id")).isEqualTo(os.getId().intValue());
        assertThat(mapa(porId.getBody())).containsKey("linkPagamento");
        assertThat(mapa(porNumero.getBody()).get("id")).isEqualTo(os.getId().intValue());
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapa(status.getBody()).get("status")).isEqualTo("ABERTA");
    }

    @Test
    void osInexistenteRetorna404() {
        for (var caminho : List.of("/999999", "/numero/999999", "/status/999999", "/999999/historico")) {
            var r = rest.getForEntity(URL + caminho, Map.class);
            assertThat(r.getStatusCode()).as(caminho).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(rest.postForEntity(URL + "/999999/entregar", null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void historicoEmOrdemCronologica() {
        var os = criar(StatusOS.ABERTA);
        var t = Instant.now();
        historico.registrar(new RegistroHistorico(os.getId(), StatusOS.ABERTA, StatusOS.EM_DIAGNOSTICO,
                "diagnostico.iniciado", null, t));
        historico.registrar(new RegistroHistorico(os.getId(), StatusOS.EM_DIAGNOSTICO, StatusOS.AGUARDANDO_APROVACAO,
                "orcamento.gerado", null, t.plusSeconds(1)));

        var r = rest.getForEntity(URL + "/" + os.getId() + "/historico", List.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        var itens = (List<Map<String, Object>>) r.getBody();
        assertThat(itens).extracting(i -> i.get("evento"))
                .containsExactly("os.criada", "diagnostico.iniciado", "orcamento.gerado");
        assertThat(itens.get(1)).containsKeys("statusAnterior", "statusNovo", "motivo", "data");
    }

    @Test
    void entregarSoEmConcluida() {
        var concluida = criar(StatusOS.CONCLUIDA);
        var aberta = criar(StatusOS.ABERTA);

        var ok = rest.postForEntity(URL + "/" + concluida.getId() + "/entregar", null, Map.class);
        var conflito = rest.postForEntity(URL + "/" + aberta.getId() + "/entregar", null, Map.class);

        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapa(ok.getBody()).get("status")).isEqualTo("ENTREGUE");
        assertThat(conflito.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mapa(conflito.getBody())).containsKeys("erro", "mensagem");
        assertThat(rest.getForEntity(URL + "/status/" + aberta.getId(), Map.class).getBody())
                .extracting("status").isEqualTo("ABERTA");
    }

    @Test
    void cancelarAntesDaExecucao() {
        var aberta = criar(StatusOS.ABERTA);
        var emExecucao = criar(StatusOS.EM_EXECUCAO);

        var ok = rest.postForEntity(URL + "/" + aberta.getId() + "/cancelar", null, Map.class);
        var conflito = rest.postForEntity(URL + "/" + emExecucao.getId() + "/cancelar", null, Map.class);

        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapa(ok.getBody()).get("status")).isEqualTo("CANCELADA");
        assertThat(jdbc.queryForObject("select count(*) from outbox where chave = ? and conteudo like '%os.cancelada%'",
                Long.class, String.valueOf(aberta.getId()))).isEqualTo(1L);
        assertThat(conflito.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void reprocessarEstornoSoComPendente() {
        var comPendente = criar(StatusOS.AGUARDANDO_ESTORNO);
        var saga = sagas.buscar(comPendente.getId());
        saga.setEstornoPendenteId(77L);
        sagas.salvar(saga);
        var semPendente = criar(StatusOS.AGUARDANDO_ESTORNO);

        var ok = rest.postForEntity(URL + "/" + comPendente.getId() + "/reprocessar-estorno", null, Map.class);
        var conflito = rest.postForEntity(URL + "/" + semPendente.getId() + "/reprocessar-estorno", null, Map.class);

        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(jdbc.queryForObject("select count(*) from outbox where chave = ? and conteudo like '%billing.estornar-pagamento%'",
                Long.class, String.valueOf(comPendente.getId()))).isEqualTo(1L);
        assertThat(conflito.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
