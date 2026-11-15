package br.junior.estoque;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CatalogoProdutos {
    public enum Unidade { UNIDADE, CAIXA, QUILOGRAMA, LITRO }

    public record Produto(
            String sku,
            String nome,
            String categoria,
            Unidade unidade,
            int estoqueMinimo,
            int estoqueMaximo,
            BigDecimal custoUnitario,
            boolean ativo,
            Instant criadoEm) {
        public Produto {
            sku = validarCodigo(sku, "SKU");
            nome = validarTexto(nome, "Nome", 160);
            categoria = validarTexto(categoria, "Categoria", 100);
            Objects.requireNonNull(unidade, "Unidade obrigatória");
            if (estoqueMinimo < 0) throw new IllegalArgumentException("Mínimo negativo");
            if (estoqueMaximo <= 0 || estoqueMaximo < estoqueMinimo)
                throw new IllegalArgumentException("Máximo inválido");
            custoUnitario = validarCusto(custoUnitario);
            Objects.requireNonNull(criadoEm, "Criação obrigatória");
        }

        public boolean precisaRepor(int saldoDisponivel) {
            return ativo && saldoDisponivel < estoqueMinimo;
        }

        public int quantidadeSugerida(int saldoDisponivel) {
            if (!precisaRepor(saldoDisponivel)) return 0;
            return Math.max(0, estoqueMaximo - saldoDisponivel);
        }
    }

    public record Fornecedor(
            String codigo,
            String nome,
            String email,
            boolean ativo) {
        public Fornecedor {
            codigo = validarCodigo(codigo, "Código");
            nome = validarTexto(nome, "Nome", 160);
            email = validarEmail(email);
        }
    }

    public record AlteracaoCatalogo(
            Instant quando,
            String entidade,
            String codigo,
            String acao) {
        public AlteracaoCatalogo {
            Objects.requireNonNull(quando);
            Objects.requireNonNull(entidade);
            Objects.requireNonNull(codigo);
            Objects.requireNonNull(acao);
        }
    }

    private final Clock clock;
    private final Map<String, Produto> produtos = new HashMap<>();
    private final Map<String, Fornecedor> fornecedores = new HashMap<>();
    private final Map<String, Set<String>> fornecimento = new HashMap<>();
    private final List<AlteracaoCatalogo> auditoria = new ArrayList<>();

    public CatalogoProdutos() {
        this(Clock.systemUTC());
    }

    public CatalogoProdutos(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Relógio obrigatório");
    }

    static String validarCodigo(String valor, String campo) {
        var texto = validarTexto(valor, campo, 50).toUpperCase(java.util.Locale.ROOT);
        if (!texto.matches("[A-Z0-9][A-Z0-9._-]*"))
            throw new IllegalArgumentException(campo + " contém caracteres inválidos");
        return texto;
    }

    static String validarTexto(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank())
            throw new IllegalArgumentException(campo + " obrigatório");
        var texto = valor.trim();
        if (texto.length() > limite)
            throw new IllegalArgumentException(campo + " excede o limite");
        return texto;
    }

    static BigDecimal validarCusto(BigDecimal custo) {
        Objects.requireNonNull(custo, "Custo obrigatório");
        if (custo.signum() < 0 || custo.compareTo(new BigDecimal("1000000")) > 0)
            throw new IllegalArgumentException("Custo fora do intervalo");
        if (custo.scale() > 2)
            throw new IllegalArgumentException("Custo deve ter no máximo duas casas");
        return custo.setScale(2, RoundingMode.UNNECESSARY);
    }

    static String validarEmail(String email) {
        var texto = validarTexto(email, "Email", 254).toLowerCase(java.util.Locale.ROOT);
        int arroba = texto.indexOf('@');
        if (arroba <= 0 || arroba == texto.length() - 1 || texto.contains(" ")
                || !texto.substring(arroba + 1).contains("."))
            throw new IllegalArgumentException("Email inválido");
        return texto;
    }

    private void auditar(String entidade, String codigo, String acao) {
        auditoria.add(new AlteracaoCatalogo(clock.instant(), entidade, codigo, acao));
    }

    public synchronized Produto cadastrarProduto(String sku, String nome, String categoria,
            Unidade unidade, int minimo, int maximo, BigDecimal custo) {
        var produto = new Produto(sku, nome, categoria, unidade, minimo, maximo,
                custo, true, clock.instant());
        if (produtos.containsKey(produto.sku()))
            throw new IllegalStateException("SKU já cadastrado");
        produtos.put(produto.sku(), produto);
        auditar("Produto", produto.sku(), "Criado");
        return produto;
    }

    public synchronized Produto atualizarProduto(String sku, String nome, String categoria,
            Unidade unidade, int minimo, int maximo, BigDecimal custo) {
        var anterior = obterProduto(sku);
        var atualizado = new Produto(anterior.sku(), nome, categoria, unidade,
                minimo, maximo, custo, anterior.ativo(), anterior.criadoEm());
        produtos.put(atualizado.sku(), atualizado);
        auditar("Produto", atualizado.sku(), "Atualizado");
        return atualizado;
    }

    public synchronized Produto alterarAtivo(String sku, boolean ativo) {
        var anterior = obterProduto(sku);
        var atualizado = new Produto(anterior.sku(), anterior.nome(),
                anterior.categoria(), anterior.unidade(), anterior.estoqueMinimo(),
                anterior.estoqueMaximo(), anterior.custoUnitario(), ativo,
                anterior.criadoEm());
        produtos.put(atualizado.sku(), atualizado);
        auditar("Produto", atualizado.sku(), ativo ? "Ativado" : "Desativado");
        return atualizado;
    }

    public synchronized Produto obterProduto(String sku) {
        var codigo = validarCodigo(sku, "SKU");
        var produto = produtos.get(codigo);
        if (produto == null) throw new java.util.NoSuchElementException("Produto inexistente: " + codigo);
        return produto;
    }

    public synchronized List<Produto> listarProdutos(String termo, String categoria,
            Boolean ativo) {
        var busca = termo == null ? "" : termo.trim();
        var grupo = categoria == null ? "" : categoria.trim();
        return produtos.values().stream()
                .filter(p -> busca.isEmpty() || p.sku().contains(busca.toUpperCase(java.util.Locale.ROOT))
                        || p.nome().toLowerCase(java.util.Locale.ROOT)
                                .contains(busca.toLowerCase(java.util.Locale.ROOT)))
                .filter(p -> grupo.isEmpty() || p.categoria().equalsIgnoreCase(grupo))
                .filter(p -> ativo == null || p.ativo() == ativo)
                .sorted(Comparator.comparing(Produto::sku))
                .toList();
    }

    public synchronized Fornecedor cadastrarFornecedor(String codigo, String nome, String email) {
        var fornecedor = new Fornecedor(codigo, nome, email, true);
        if (fornecedores.containsKey(fornecedor.codigo()))
            throw new IllegalStateException("Fornecedor já cadastrado");
        fornecedores.put(fornecedor.codigo(), fornecedor);
        auditar("Fornecedor", fornecedor.codigo(), "Criado");
        return fornecedor;
    }

    public synchronized Fornecedor atualizarFornecedor(String codigo, String nome,
            String email, boolean ativo) {
        var anterior = obterFornecedor(codigo);
        var atualizado = new Fornecedor(anterior.codigo(), nome, email, ativo);
        fornecedores.put(atualizado.codigo(), atualizado);
        auditar("Fornecedor", atualizado.codigo(), "Atualizado");
        return atualizado;
    }

    public synchronized Fornecedor obterFornecedor(String codigo) {
        var chave = validarCodigo(codigo, "Fornecedor");
        var fornecedor = fornecedores.get(chave);
        if (fornecedor == null)
            throw new java.util.NoSuchElementException("Fornecedor inexistente: " + chave);
        return fornecedor;
    }

    public synchronized void vincularFornecedor(String sku, String codigoFornecedor) {
        var produto = obterProduto(sku);
        var fornecedor = obterFornecedor(codigoFornecedor);
        if (!produto.ativo() || !fornecedor.ativo())
            throw new IllegalStateException("Produto e fornecedor devem estar ativos");
        var vinculos = fornecimento.computeIfAbsent(produto.sku(), k -> new HashSet<>());
        if (!vinculos.add(fornecedor.codigo()))
            throw new IllegalStateException("Fornecedor já vinculado");
        auditar("Fornecimento", produto.sku(), "Vinculado:" + fornecedor.codigo());
    }

    public synchronized void desvincularFornecedor(String sku, String codigoFornecedor) {
        var produto = obterProduto(sku);
        var fornecedor = obterFornecedor(codigoFornecedor);
        var vinculos = fornecimento.get(produto.sku());
        if (vinculos == null || !vinculos.remove(fornecedor.codigo()))
            throw new java.util.NoSuchElementException("Vínculo não encontrado");
        auditar("Fornecimento", produto.sku(), "Desvinculado:" + fornecedor.codigo());
    }

    public synchronized List<Fornecedor> fornecedoresDe(String sku) {
        var produto = obterProduto(sku);
        return fornecimento.getOrDefault(produto.sku(), Set.of()).stream()
                .map(fornecedores::get)
                .sorted(Comparator.comparing(Fornecedor::codigo))
                .toList();
    }

    public synchronized List<AlteracaoCatalogo> auditoria() {
        return List.copyOf(auditoria);
    }

    public synchronized List<Fornecedor> listarFornecedores(boolean somenteAtivos) {
        return fornecedores.values().stream()
                .filter(f -> !somenteAtivos || f.ativo())
                .sorted(Comparator.comparing(Fornecedor::codigo))
                .toList();
    }
}
