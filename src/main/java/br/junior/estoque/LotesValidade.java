package br.junior.estoque;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class LotesValidade {
    public record Lote(String sku, String numero, LocalDate validade,
            int recebido, int saldo, java.time.Instant recebidoEm) {
        public Lote {
            if (recebido <= 0 || saldo < 0 || saldo > recebido)
                throw new IllegalArgumentException("Quantidade do lote inválida");
            Objects.requireNonNull(validade);
            Objects.requireNonNull(recebidoEm);
        }

        public boolean vencido(LocalDate hoje) {
            return validade.isBefore(hoje);
        }

        public boolean venceAte(LocalDate hoje, int dias) {
            return !vencido(hoje) && !validade.isAfter(hoje.plusDays(dias));
        }
    }

    public record ConsumoLote(String sku, String numero, int quantidade,
            java.time.Instant quando, String referencia) {
        public ConsumoLote {
            if (quantidade <= 0) throw new IllegalArgumentException("Quantidade inválida");
            Objects.requireNonNull(quando);
        }
    }

    private record Chave(String sku, String numero) { }

    private final CatalogoProdutos catalogo;
    private final Clock clock;
    private final Map<Chave, Lote> lotes = new HashMap<>();
    private final List<ConsumoLote> consumos = new ArrayList<>();

    public LotesValidade(CatalogoProdutos catalogo, Clock clock) {
        this.catalogo = Objects.requireNonNull(catalogo);
        this.clock = Objects.requireNonNull(clock);
    }

    public LotesValidade(CatalogoProdutos catalogo) {
        this(catalogo, Clock.systemUTC());
    }

    private LocalDate hoje() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private Chave chave(String sku, String numero) {
        var produto = catalogo.obterProduto(sku);
        var lote = CatalogoProdutos.validarCodigo(numero, "Lote");
        return new Chave(produto.sku(), lote);
    }

    public synchronized Lote receber(String sku, String numero, LocalDate validade,
            int quantidade) {
        var chave = chave(sku, numero);
        if (!catalogo.obterProduto(sku).ativo())
            throw new IllegalStateException("Produto inativo");
        Objects.requireNonNull(validade, "Validade obrigatória");
        if (quantidade <= 0) throw new IllegalArgumentException("Quantidade inválida");
        if (validade.isBefore(hoje()))
            throw new IllegalArgumentException("Lote já vencido");
        if (lotes.containsKey(chave))
            throw new IllegalStateException("Lote duplicado");
        var lote = new Lote(chave.sku(), chave.numero(), validade,
                quantidade, quantidade, clock.instant());
        lotes.put(chave, lote);
        return lote;
    }

    public synchronized Lote obter(String sku, String numero) {
        var chave = chave(sku, numero);
        var lote = lotes.get(chave);
        if (lote == null) throw new NoSuchElementException("Lote não encontrado");
        return lote;
    }

    public synchronized List<Lote> listar(String sku, boolean incluirZerados) {
        String filtro = sku == null ? null : catalogo.obterProduto(sku).sku();
        return lotes.values().stream()
                .filter(l -> filtro == null || l.sku().equals(filtro))
                .filter(l -> incluirZerados || l.saldo() > 0)
                .sorted(Comparator.comparing(Lote::validade)
                        .thenComparing(Lote::sku).thenComparing(Lote::numero))
                .toList();
    }

    public synchronized int disponivel(String sku, LocalDate data) {
        var produto = catalogo.obterProduto(sku);
        Objects.requireNonNull(data);
        return lotes.values().stream()
                .filter(l -> l.sku().equals(produto.sku()) && !l.vencido(data))
                .mapToInt(Lote::saldo).sum();
    }

    public synchronized List<ConsumoLote> consumir(String sku, int quantidade,
            String referencia) {
        if (quantidade <= 0) throw new IllegalArgumentException("Quantidade inválida");
        var produto = catalogo.obterProduto(sku);
        var motivo = CatalogoProdutos.validarTexto(referencia, "Referência", 120);
        var data = hoje();
        if (disponivel(produto.sku(), data) < quantidade)
            throw new IllegalStateException("Lotes válidos insuficientes");

        var candidatos = listar(produto.sku(), false).stream()
                .filter(l -> !l.vencido(data)).toList();
        int restante = quantidade;
        var feitos = new ArrayList<ConsumoLote>();
        for (var lote : candidatos) {
            if (restante == 0) break;
            int retirado = Math.min(restante, lote.saldo());
            if (retirado == 0) continue;
            var chave = new Chave(lote.sku(), lote.numero());
            lotes.put(chave, new Lote(lote.sku(), lote.numero(), lote.validade(),
                    lote.recebido(), lote.saldo() - retirado, lote.recebidoEm()));
            feitos.add(new ConsumoLote(lote.sku(), lote.numero(),
                    retirado, clock.instant(), motivo));
            restante -= retirado;
        }
        consumos.addAll(feitos);
        return List.copyOf(feitos);
    }

    public synchronized List<Lote> vencendoEm(int dias) {
        if (dias < 0 || dias > 3650)
            throw new IllegalArgumentException("Intervalo inválido");
        var data = hoje();
        return lotes.values().stream()
                .filter(l -> l.saldo() > 0 && l.venceAte(data, dias))
                .sorted(Comparator.comparing(Lote::validade)
                        .thenComparing(Lote::numero))
                .toList();
    }

    public synchronized List<Lote> vencidos() {
        var data = hoje();
        return lotes.values().stream()
                .filter(l -> l.saldo() > 0 && l.vencido(data))
                .sorted(Comparator.comparing(Lote::validade)
                        .thenComparing(Lote::numero))
                .toList();
    }

    public synchronized List<ConsumoLote> historico(String sku) {
        String filtro = sku == null ? null : catalogo.obterProduto(sku).sku();
        return consumos.stream()
                .filter(c -> filtro == null || c.sku().equals(filtro))
                .toList();
    }

    public synchronized int saldoTotal(String sku) {
        var produto = catalogo.obterProduto(sku);
        return lotes.values().stream().filter(l -> l.sku().equals(produto.sku()))
                .mapToInt(Lote::saldo).sum();
    }
}
