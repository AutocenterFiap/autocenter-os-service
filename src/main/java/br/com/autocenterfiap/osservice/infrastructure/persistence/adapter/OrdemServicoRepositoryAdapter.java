package br.com.autocenterfiap.osservice.infrastructure.persistence.adapter;

import br.com.autocenterfiap.osservice.application.exception.OSJaEmAndamentoException;
import br.com.autocenterfiap.osservice.application.port.OrdemServicoRepositoryPort;
import br.com.autocenterfiap.osservice.application.port.Pagina;
import br.com.autocenterfiap.osservice.domain.entity.ItemProduto;
import br.com.autocenterfiap.osservice.domain.entity.ItemServico;
import br.com.autocenterfiap.osservice.domain.entity.ItensOS;
import br.com.autocenterfiap.osservice.domain.entity.OrdemServico;
import br.com.autocenterfiap.osservice.domain.enums.StatusOS;
import br.com.autocenterfiap.osservice.infrastructure.persistence.entity.ItemProdutoJpaEntity;
import br.com.autocenterfiap.osservice.infrastructure.persistence.entity.ItemServicoJpaEntity;
import br.com.autocenterfiap.osservice.infrastructure.persistence.entity.OrdemServicoJpaEntity;
import br.com.autocenterfiap.osservice.infrastructure.persistence.repository.OrdemServicoJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrdemServicoRepositoryAdapter implements OrdemServicoRepositoryPort {

    private static final String RESTRICAO_VEICULO_ATIVO = "veiculo_ativo_id";

    private final OrdemServicoJpaRepository repositorio;

    public OrdemServicoRepositoryAdapter(OrdemServicoJpaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional
    public OrdemServico salvar(OrdemServico os) {
        var entidade = paraEntidade(os);
        try {
            var salva = repositorio.saveAndFlush(entidade);
            if (salva.getNumero() == null) {
                salva.setNumero(salva.getId());
                repositorio.flush();
            }
            return paraDominio(salva);
        } catch (DataIntegrityViolationException e) {
            if (violouVeiculoAtivo(e)) {
                throw new OSJaEmAndamentoException(os.getVeiculoId());
            }
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrdemServico> buscarPorId(Long id) {
        return repositorio.findById(id).map(this::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrdemServico> buscarPorNumero(Long numero) {
        return repositorio.findByNumero(numero).map(this::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<OrdemServico> listar(StatusOS statusOuNull, int pagina, int tamanho) {
        var pageable = PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "id"));
        var resultado = statusOuNull == null
                ? repositorio.findAll(pageable)
                : repositorio.findByStatus(statusOuNull, pageable);
        return new Pagina<>(resultado.map(this::paraDominio).getContent(), resultado.getNumber(),
                resultado.getSize(), resultado.getTotalElements(), resultado.getTotalPages());
    }

    private static boolean violouVeiculoAtivo(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(RESTRICAO_VEICULO_ATIVO)) {
                return true;
            }
        }
        return false;
    }

    private OrdemServicoJpaEntity paraEntidade(OrdemServico os) {
        var e = new OrdemServicoJpaEntity();
        e.setId(os.getId());
        e.setNumero(os.getNumero());
        e.setClienteId(os.getClienteId());
        e.setVeiculoId(os.getVeiculoId());
        e.setProblemaRelatado(os.getProblemaRelatado());
        e.setStatus(os.getStatus());
        e.setValorTotal(os.getValorTotal());
        e.setCorrelationId(os.getCorrelationId().toString());
        e.setVeiculoAtivoId(os.getStatus().bloqueiaNovaOS() ? os.getVeiculoId() : null);
        e.setCriadaEm(os.getCriadaEm());
        e.setAtualizadaEm(os.getAtualizadaEm());
        e.setVersion(os.getVersion());
        os.getItens().servicos().forEach(s -> {
            var i = new ItemServicoJpaEntity();
            i.setServicoId(s.servicoId());
            i.setDescricao(s.descricao());
            i.setValor(s.valor());
            e.getServicos().add(i);
        });
        os.getItens().produtos().forEach(p -> {
            var i = new ItemProdutoJpaEntity();
            i.setProdutoId(p.produtoId());
            i.setDescricao(p.descricao());
            i.setQuantidade(p.quantidade());
            i.setPrecoUnitario(p.precoUnitario());
            e.getProdutos().add(i);
        });
        return e;
    }

    private OrdemServico paraDominio(OrdemServicoJpaEntity e) {
        var servicos = e.getServicos().stream()
                .map(i -> new ItemServico(i.getServicoId(), i.getDescricao(), i.getValor())).toList();
        var produtos = e.getProdutos().stream()
                .map(i -> new ItemProduto(i.getProdutoId(), i.getDescricao(), i.getQuantidade(), i.getPrecoUnitario()))
                .toList();
        return OrdemServico.reconstruir(e.getId(), e.getNumero(), e.getClienteId(), e.getVeiculoId(),
                e.getProblemaRelatado(), e.getStatus(), e.getValorTotal(), new ItensOS(servicos, produtos),
                UUID.fromString(e.getCorrelationId()), e.getCriadaEm(), e.getAtualizadaEm(), e.getVersion());
    }
}
