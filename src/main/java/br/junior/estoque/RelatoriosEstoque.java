package br.junior.estoque;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class RelatoriosEstoque {
    public record Reposicao(String sku, String nome, int minimo,
            int maximo, long disponivel, long sugerido, BigDecimal custoEstimado) { }

    public record Resumo(long produtosAtivos, long locaisAtivos,
            long unidadesFisicas, long unidadesReservadas, long unidadesDisponiveis,
            long reservasAtivas, BigDecimal valorEstoque, Instant geradoEm) { }

    private final CatalogoProdutos catalogo;
    private final EstoqueLocalizado estoque;
    private final java.time.Clock clock;

    public RelatoriosEstoque(CatalogoProdutos catalogo, EstoqueLocalizado estoque) {
        this(catalogo, estoque, java.time.Clock.systemUTC());
    }

    public RelatoriosEstoque(CatalogoProdutos catalogo, EstoqueLocalizado estoque,
            java.time.Clock clock) {
        this.catalogo = Objects.requireNonNull(catalogo);
        this.estoque = Objects.requireNonNull(estoque);
        this.clock = Objects.requireNonNull(clock);
    }

    public Resumo resumo() {
        var posicoes = estoque.listarPosicoes(null);
        long fisico = posicoes.stream().mapToLong(EstoqueLocalizado.Posicao::quantidadeFisica).sum();
        long reservado = posicoes.stream().mapToLong(EstoqueLocalizado.Posicao::quantidadeReservada).sum();
        BigDecimal valor = BigDecimal.ZERO;
        for (var posicao : posicoes) {
            var custo = catalogo.obterProduto(posicao.sku()).custoUnitario();
            valor = valor.add(custo.multiply(BigDecimal.valueOf(posicao.quantidadeFisica())));
        }
        return new Resumo(
                catalogo.listarProdutos(null, null, true).size(),
                estoque.listarLocais(true).size(),
                fisico, reservado, fisico - reservado,
                estoque.listarReservas(EstoqueLocalizado.EstadoReserva.ATIVA).size(),
                valor, clock.instant());
    }

    public List<Reposicao> sugestoesReposicao() {
        var sugestoes = new ArrayList<Reposicao>();
        for (var produto : catalogo.listarProdutos(null, null, true)) {
            long disponivel = estoque.listarPosicoes(produto.sku()).stream()
                    .mapToLong(EstoqueLocalizado.Posicao::disponivel).sum();
            if (disponivel >= produto.estoqueMinimo()) continue;
            long sugerido = Math.max(0, (long) produto.estoqueMaximo() - disponivel);
            sugestoes.add(new Reposicao(produto.sku(), produto.nome(),
                    produto.estoqueMinimo(), produto.estoqueMaximo(), disponivel,
                    sugerido, produto.custoUnitario().multiply(BigDecimal.valueOf(sugerido))));
        }
        return List.copyOf(sugestoes);
    }

    public List<EstoqueLocalizado.Posicao> posicoesCriticas() {
        return estoque.listarPosicoes(null).stream()
                .filter(p -> catalogo.obterProduto(p.sku()).ativo())
                .filter(p -> p.disponivel() < catalogo.obterProduto(p.sku()).estoqueMinimo())
                .sorted(Comparator.comparing(EstoqueLocalizado.Posicao::sku)
                        .thenComparing(EstoqueLocalizado.Posicao::local))
                .toList();
    }

    private static String csv(String texto) {
        if (texto == null) texto = "";
        if (!texto.isEmpty() && "=+-@\t\r".indexOf(texto.charAt(0)) >= 0)
            texto = "'" + texto;
        return "\"" + texto.replace("\"", "\"\"") + "\"";
    }

    public String exportarPosicoesCsv() {
        var linhas = new StringBuilder();
        linhas.append("sku;produto;local;fisico;reservado;disponivel;custo_unitario\n");
        for (var posicao : estoque.listarPosicoes(null)) {
            var produto = catalogo.obterProduto(posicao.sku());
            linhas.append(csv(produto.sku())).append(';')
                    .append(csv(produto.nome())).append(';')
                    .append(csv(posicao.local())).append(';')
                    .append(posicao.quantidadeFisica()).append(';')
                    .append(posicao.quantidadeReservada()).append(';')
                    .append(posicao.disponivel()).append(';')
                    .append(produto.custoUnitario().toPlainString()).append('\n');
        }
        return linhas.toString();
    }

    public String exportarEventosCsv() {
        var linhas = new StringBuilder();
        linhas.append("sequencia;quando;tipo;sku;local;quantidade;referencia;saldo_apos\n");
        for (var evento : estoque.historico(null, null)) {
            linhas.append(evento.sequencia()).append(';')
                    .append(evento.quando()).append(';')
                    .append(evento.tipo()).append(';')
                    .append(csv(evento.sku())).append(';')
                    .append(csv(evento.local())).append(';')
                    .append(evento.quantidade()).append(';')
                    .append(csv(evento.referencia())).append(';')
                    .append(evento.saldoApos()).append('\n');
        }
        return linhas.toString();
    }

    public String relatorioMarkdown() {
        var resumo = resumo();
        var linhas = new StringBuilder("# Relatório de Estoque\n\n");
        linhas.append("Gerado em: ").append(resumo.geradoEm()).append("\n\n");
        linhas.append("- Produtos ativos: ").append(resumo.produtosAtivos()).append("\n");
        linhas.append("- Locais ativos: ").append(resumo.locaisAtivos()).append("\n");
        linhas.append("- Unidades físicas: ").append(resumo.unidadesFisicas()).append("\n");
        linhas.append("- Unidades reservadas: ").append(resumo.unidadesReservadas()).append("\n");
        linhas.append("- Unidades disponíveis: ").append(resumo.unidadesDisponiveis()).append("\n");
        linhas.append("- Valor estimado: ").append(resumo.valorEstoque()).append("\n\n");
        linhas.append("## Reposição sugerida\n\n");
        linhas.append("| SKU | Produto | Disponível | Repor | Custo estimado |\n");
        linhas.append("| --- | --- | ---: | ---: | ---: |\n");
        for (var reposicao : sugestoesReposicao()) {
            linhas.append("| ").append(escaparMarkdown(reposicao.sku()))
                    .append(" | ").append(escaparMarkdown(reposicao.nome()))
                    .append(" | ").append(reposicao.disponivel())
                    .append(" | ").append(reposicao.sugerido())
                    .append(" | ").append(reposicao.custoEstimado())
                    .append(" |\n");
        }
        return linhas.toString();
    }

    private static String escaparMarkdown(String texto) {
        return texto.replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("\r", " ")
                .replace("\n", " ");
    }
}
