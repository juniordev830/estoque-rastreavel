package br.junior.estoque;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EstoqueService {
    private final Clock clock;
    private final Map<String, Integer> saldos = new HashMap<>();
    private final List<Movimentacao> historico = new ArrayList<>();

    public EstoqueService() {
        this(Clock.systemUTC());
    }

    public EstoqueService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Relógio obrigatório");
    }

    public synchronized void registrarEntrada(String produto, int quantidade) {
        movimentar(produto, Movimentacao.TipoMovimento.ENTRADA, quantidade);
    }

    public synchronized void registrarSaida(String produto, int quantidade) {
        var saldo = saldo(produto);
        if (quantidade <= 0) throw new IllegalArgumentException("Quantidade deve ser positiva");
        if (saldo < quantidade) throw new IllegalStateException("Saldo insuficiente");
        movimentar(produto, Movimentacao.TipoMovimento.SAIDA, quantidade);
    }

    public synchronized int saldo(String produto) {
        if (produto == null || produto.isBlank()) throw new IllegalArgumentException("Produto obrigatório");
        return saldos.getOrDefault(produto.trim(), 0);
    }

    public synchronized List<Movimentacao> historico(String produto) {
        if (produto == null || produto.isBlank()) throw new IllegalArgumentException("Produto obrigatório");
        return Collections.unmodifiableList(historico.stream()
                .filter(m -> m.produto().equals(produto.trim()))
                .toList());
    }

    /**
     * Transfers stock between two distinct product keys as one synchronized operation.
     * Both balances and movement records are validated before any mutation.
     */
    public synchronized void transferir(String origem, String destino, int quantidade) {
        String from = normalizarProduto(origem);
        String to = normalizarProduto(destino);
        if (from.equals(to)) throw new IllegalArgumentException("Produtos devem ser diferentes");
        if (quantidade <= 0) throw new IllegalArgumentException("Quantidade deve ser positiva");

        int disponivel = saldos.getOrDefault(from, 0);
        if (disponivel < quantidade) throw new IllegalStateException("Saldo insuficiente");
        int destinoAtualizado = Math.addExact(saldos.getOrDefault(to, 0), quantidade);

        var agora = LocalDateTime.now(clock);
        var saida = new Movimentacao(from, Movimentacao.TipoMovimento.SAIDA, quantidade, agora);
        var entrada = new Movimentacao(to, Movimentacao.TipoMovimento.ENTRADA, quantidade, agora);

        saldos.put(from, disponivel - quantidade);
        saldos.put(to, destinoAtualizado);
        historico.add(saida);
        historico.add(entrada);
    }

    private static String normalizarProduto(String produto) {
        if (produto == null || produto.isBlank()) {
            throw new IllegalArgumentException("Produto obrigatório");
        }
        return produto.trim();
    }

    private void movimentar(String produto, Movimentacao.TipoMovimento tipo, int quantidade) {
        var nome = produto == null ? null : produto.trim();
        var movimento = new Movimentacao(nome, tipo, quantidade, LocalDateTime.now(clock));
        var delta = tipo == Movimentacao.TipoMovimento.ENTRADA ? quantidade : -quantidade;
        // Calculate before changing either collection so overflow leaves no partial entry.
        var novoSaldo = Math.addExact(saldos.getOrDefault(nome, 0), delta);
        saldos.put(nome, novoSaldo);
        historico.add(movimento);
    }
}
