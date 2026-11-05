CREATE TABLE ordem_servico (
    id BIGINT NOT NULL AUTO_INCREMENT,
    numero BIGINT NULL,
    cliente_id BIGINT NOT NULL,
    veiculo_id BIGINT NOT NULL,
    problema_relatado VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL,
    valor_total DECIMAL(12,2) NOT NULL,
    correlation_id CHAR(36) NOT NULL,
    veiculo_ativo_id BIGINT NULL,
    criada_em DATETIME(6) NOT NULL,
    atualizada_em DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_os_numero (numero),
    UNIQUE KEY veiculo_ativo_id (veiculo_ativo_id),
    KEY idx_os_status (status)
);

CREATE TABLE os_item_servico (
    id BIGINT NOT NULL AUTO_INCREMENT,
    os_id BIGINT NOT NULL,
    servico_id BIGINT NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    valor DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_item_servico_os FOREIGN KEY (os_id) REFERENCES ordem_servico (id)
);

CREATE TABLE os_item_produto (
    id BIGINT NOT NULL AUTO_INCREMENT,
    os_id BIGINT NOT NULL,
    produto_id BIGINT NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    quantidade INT NOT NULL,
    preco_unitario DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_item_produto_os FOREIGN KEY (os_id) REFERENCES ordem_servico (id)
);

CREATE TABLE os_historico (
    id BIGINT NOT NULL AUTO_INCREMENT,
    os_id BIGINT NOT NULL,
    status_anterior VARCHAR(30) NULL,
    status_novo VARCHAR(30) NOT NULL,
    evento VARCHAR(100) NOT NULL,
    motivo VARCHAR(500) NULL,
    data DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_historico_os (os_id),
    CONSTRAINT fk_historico_os FOREIGN KEY (os_id) REFERENCES ordem_servico (id)
);

CREATE TABLE saga_os (
    os_id BIGINT NOT NULL,
    tentativas_pagamento INT NOT NULL,
    prazo_pagamento DATETIME(6) NULL,
    link_pagamento VARCHAR(1000) NULL,
    orcamento_id BIGINT NULL,
    pagamento_id BIGINT NULL,
    pagamento_confirmado_id BIGINT NULL,
    estorno_pendente_id BIGINT NULL,
    PRIMARY KEY (os_id),
    KEY idx_saga_prazo (prazo_pagamento),
    CONSTRAINT fk_saga_os FOREIGN KEY (os_id) REFERENCES ordem_servico (id)
);

CREATE TABLE outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL,
    topico VARCHAR(100) NOT NULL,
    chave VARCHAR(100) NOT NULL,
    conteudo MEDIUMTEXT NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    enviado_em DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_outbox_pendente (enviado_em, id)
);

CREATE TABLE mensagem_processada (
    event_id CHAR(36) NOT NULL,
    processada_em DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id)
);
