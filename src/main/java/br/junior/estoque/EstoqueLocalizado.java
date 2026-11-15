package br.junior.estoque;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class EstoqueLocalizado {
    public record Local(String codigo, String descricao, boolean ativo) {
        public Local {
            codigo = CatalogoProdutos.validarCodigo(codigo, "Local");
            descricao = CatalogoProdutos.validarTexto(descricao, "Descrição", 160);
        }
    }

    public record Posicao(String sku, String local, int quantidadeFisica,
            int quantidadeReservada) {
        public Posicao {
            if (quantidadeFisica < 0 || quantidadeReservada < 0
                    || quantidadeReservada > quantidadeFisica)
                throw new IllegalArgumentException("Posição inconsistente");
        }

        public int disponivel() {
            return quantidadeFisica - quantidadeReservada;
        }
    }

    public enum EstadoReserva { ATIVA, ATENDIDA, CANCELADA }

    public record Reserva(long id, String sku, String local, int quantidade,
            String referencia, EstadoReserva estado, Instant criadaEm, Instant finalizadaEm) {
        public Reserva {
            if (id <= 0 || quantidade <= 0) throw new IllegalArgumentException("Reserva inválida");
            Objects.requireNonNull(estado);
            Objects.requireNonNull(criadaEm);
        }
    }

    public enum TipoEvento {
        ENTRADA, SAIDA, TRANSFERENCIA_ORIGEM, TRANSFERENCIA_DESTINO,
        RESERVA, ATENDIMENTO, CANCELAMENTO, AJUSTE
    }

    public record Evento(long sequencia, Instant quando, TipoEvento tipo,
            String sku, String local, int quantidade, String referencia,
            int saldoApos) {
        public Evento {
            if (sequencia <= 0 || quantidade < 0)
                throw new IllegalArgumentException("Evento inválido");
            Objects.requireNonNull(quando);
            Objects.requireNonNull(tipo);
        }
    }

    private record Chave(String sku, String local) { }

    private final Clock clock;
    private final CatalogoProdutos catalogo;
    private final Map<String, Local> locais = new HashMap<>();
    private final Map<Chave, Integer> saldos = new HashMap<>();
    private final Map<Long, Reserva> reservas = new HashMap<>();
    private final List<Evento> eventos = new ArrayList<>();
    private long proximaReserva = 1;
    private long proximoEvento = 1;

    public EstoqueLocalizado(CatalogoProdutos catalogo) {
        this(catalogo, Clock.systemUTC());
    }

    public EstoqueLocalizado(CatalogoProdutos catalogo, Clock clock) {
        this.catalogo = Objects.requireNonNull(catalogo, "Catálogo obrigatório");
        this.clock = Objects.requireNonNull(clock, "Relógio obrigatório");
    }

    public synchronized Local cadastrarLocal(String codigo, String descricao) {
        var local = new Local(codigo, descricao, true);
        if (locais.putIfAbsent(local.codigo(), local) != null)
            throw new IllegalStateException("Local já cadastrado");
        return local;
    }

    public synchronized Local alterarLocal(String codigo, String descricao, boolean ativo) {
        var atual = obterLocal(codigo);
        if (!ativo && reservas.values().stream().anyMatch(r ->
                r.local().equals(atual.codigo()) && r.estado() == EstadoReserva.ATIVA))
            throw new IllegalStateException("Local com reservas ativas");
        var atualizado = new Local(atual.codigo(), descricao, ativo);
        locais.put(atualizado.codigo(), atualizado);
        return atualizado;
    }

    public synchronized Local obterLocal(String codigo) {
        var chave = CatalogoProdutos.validarCodigo(codigo, "Local");
        var local = locais.get(chave);
        if (local == null) throw new NoSuchElementException("Local não encontrado: " + chave);
        return local;
    }

    private Chave chave(String sku, String local) {
        var produto = catalogo.obterProduto(sku);
        var deposito = obterLocal(local);
        return new Chave(produto.sku(), deposito.codigo());
    }

    private Chave chaveAtiva(String sku, String local) {
        var produto = catalogo.obterProduto(sku);
        var deposito = obterLocal(local);
        if (!produto.ativo() || !deposito.ativo())
            throw new IllegalStateException("Produto e local devem estar ativos");
        return new Chave(produto.sku(), deposito.codigo());
    }

    private int fisico(Chave chave) {
        return saldos.getOrDefault(chave, 0);
    }

    private int reservado(Chave chave) {
        return reservas.values().stream()
                .filter(r -> r.estado() == EstadoReserva.ATIVA
                        && r.sku().equals(chave.sku()) && r.local().equals(chave.local()))
                .mapToInt(Reserva::quantidade).sum();
    }

    private int disponivel(Chave chave) {
        return fisico(chave) - reservado(chave);
    }

    private void exigirQuantidade(int quantidade) {
        if (quantidade <= 0) throw new IllegalArgumentException("Quantidade deve ser positiva");
    }

    private void registrar(TipoEvento tipo, Chave chave, int quantidade, String referencia) {
        eventos.add(new Evento(proximoEvento++, clock.instant(), tipo,
                chave.sku(), chave.local(), quantidade, referencia, fisico(chave)));
    }

    public synchronized Posicao consultar(String sku, String local) {
        var chave = chave(sku, local);
        return new Posicao(chave.sku(), chave.local(), fisico(chave), reservado(chave));
    }

    public synchronized Posicao receber(String sku, String local, int quantidade,
            String referencia) {
        exigirQuantidade(quantidade);
        var chave = chaveAtiva(sku, local);
        var descricao = CatalogoProdutos.validarTexto(referencia, "Referência", 120);
        int novo = Math.addExact(fisico(chave), quantidade);
        saldos.put(chave, novo);
        registrar(TipoEvento.ENTRADA, chave, quantidade, descricao);
        return consultar(sku, local);
    }

    public synchronized Posicao retirar(String sku, String local, int quantidade,
            String referencia) {
        exigirQuantidade(quantidade);
        var chave = chave(sku, local);
        var descricao = CatalogoProdutos.validarTexto(referencia, "Referência", 120);
        if (disponivel(chave) < quantidade)
            throw new IllegalStateException("Saldo disponível insuficiente");
        saldos.put(chave, fisico(chave) - quantidade);
        registrar(TipoEvento.SAIDA, chave, quantidade, descricao);
        return consultar(sku, local);
    }

    public synchronized void transferir(String sku, String origem, String destino,
            int quantidade, String referencia) {
        exigirQuantidade(quantidade);
        var de = chaveAtiva(sku, origem);
        var para = chaveAtiva(sku, destino);
        if (de.equals(para)) throw new IllegalArgumentException("Locais iguais");
        var descricao = CatalogoProdutos.validarTexto(referencia, "Referência", 120);
        if (disponivel(de) < quantidade)
            throw new IllegalStateException("Saldo disponível insuficiente");
        int novoDestino = Math.addExact(fisico(para), quantidade);
        saldos.put(de, fisico(de) - quantidade);
        saldos.put(para, novoDestino);
        registrar(TipoEvento.TRANSFERENCIA_ORIGEM, de, quantidade, descricao);
        registrar(TipoEvento.TRANSFERENCIA_DESTINO, para, quantidade, descricao);
    }

    public synchronized Reserva reservar(String sku, String local, int quantidade,
            String referencia) {
        exigirQuantidade(quantidade);
        var chave = chaveAtiva(sku, local);
        var descricao = CatalogoProdutos.validarTexto(referencia, "Referência", 120);
        if (disponivel(chave) < quantidade)
            throw new IllegalStateException("Saldo disponível insuficiente");
        var reserva = new Reserva(proximaReserva++, chave.sku(), chave.local(),
                quantidade, descricao, EstadoReserva.ATIVA, clock.instant(), null);
        reservas.put(reserva.id(), reserva);
        registrar(TipoEvento.RESERVA, chave, quantidade, descricao);
        return reserva;
    }

    public synchronized Reserva obterReserva(long id) {
        var reserva = reservas.get(id);
        if (reserva == null) throw new NoSuchElementException("Reserva inexistente: " + id);
        return reserva;
    }

    public synchronized Reserva cancelarReserva(long id) {
        var reserva = obterReserva(id);
        if (reserva.estado() != EstadoReserva.ATIVA)
            throw new IllegalStateException("Reserva já finalizada");
        var nova = new Reserva(reserva.id(), reserva.sku(), reserva.local(),
                reserva.quantidade(), reserva.referencia(), EstadoReserva.CANCELADA,
                reserva.criadaEm(), clock.instant());
        reservas.put(id, nova);
        registrar(TipoEvento.CANCELAMENTO,
                new Chave(nova.sku(), nova.local()), nova.quantidade(), nova.referencia());
        return nova;
    }

    public synchronized Reserva atenderReserva(long id) {
        var reserva = obterReserva(id);
        if (reserva.estado() != EstadoReserva.ATIVA)
            throw new IllegalStateException("Reserva já finalizada");
        var chave = new Chave(reserva.sku(), reserva.local());
        int fisicoAtual = fisico(chave);
        if (fisicoAtual < reserva.quantidade())
            throw new IllegalStateException("Saldo físico inconsistente");
        var nova = new Reserva(reserva.id(), reserva.sku(), reserva.local(),
                reserva.quantidade(), reserva.referencia(), EstadoReserva.ATENDIDA,
                reserva.criadaEm(), clock.instant());
        saldos.put(chave, fisicoAtual - reserva.quantidade());
        reservas.put(id, nova);
        registrar(TipoEvento.ATENDIMENTO, chave, reserva.quantidade(), reserva.referencia());
        return nova;
    }

    public synchronized Posicao ajustarInventario(String sku, String local,
            int contagemFisica, String justificativa) {
        if (contagemFisica < 0) throw new IllegalArgumentException("Contagem negativa");
        var chave = chaveAtiva(sku, local);
        var motivo = CatalogoProdutos.validarTexto(justificativa, "Justificativa", 200);
        if (contagemFisica < reservado(chave))
            throw new IllegalStateException("Contagem inferior às reservas");
        int diferenca = Math.subtractExact(contagemFisica, fisico(chave));
        if (diferenca == 0) return consultar(sku, local);
        saldos.put(chave, contagemFisica);
        registrar(TipoEvento.AJUSTE, chave, Math.abs(diferenca),
                (diferenca > 0 ? "+" : "-") + motivo);
        return consultar(sku, local);
    }

    public synchronized List<Posicao> listarPosicoes(String sku) {
        String filtro = sku == null ? null : catalogo.obterProduto(sku).sku();
        return saldos.keySet().stream()
                .filter(c -> filtro == null || c.sku().equals(filtro))
                .sorted(Comparator.comparing(Chave::sku).thenComparing(Chave::local))
                .map(c -> new Posicao(c.sku(), c.local(), fisico(c), reservado(c)))
                .toList();
    }

    public synchronized List<Local> listarLocais(boolean somenteAtivos) {
        return locais.values().stream()
                .filter(l -> !somenteAtivos || l.ativo())
                .sorted(Comparator.comparing(Local::codigo))
                .toList();
    }

    public synchronized List<Reserva> listarReservas(EstadoReserva estado) {
        return reservas.values().stream()
                .filter(r -> estado == null || r.estado() == estado)
                .sorted(Comparator.comparingLong(Reserva::id))
                .toList();
    }

    public synchronized List<Evento> historico(String sku, String local) {
        String filtroSku = sku == null ? null : catalogo.obterProduto(sku).sku();
        String filtroLocal = local == null ? null : obterLocal(local).codigo();
        return eventos.stream()
                .filter(e -> filtroSku == null || e.sku().equals(filtroSku))
                .filter(e -> filtroLocal == null || e.local().equals(filtroLocal))
                .toList();
    }

    public synchronized int saldoTotal(String sku) {
        var produto = catalogo.obterProduto(sku);
        return saldos.entrySet().stream().filter(e -> e.getKey().sku().equals(produto.sku()))
                .mapToInt(Map.Entry::getValue).sum();
    }

    public synchronized int disponivelTotal(String sku) {
        var produto = catalogo.obterProduto(sku);
        return saldos.keySet().stream().filter(c -> c.sku().equals(produto.sku()))
                .mapToInt(this::disponivel).sum();
    }
}
