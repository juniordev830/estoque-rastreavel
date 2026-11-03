package br.junior.estoque;

import java.time.LocalDateTime;
import java.util.Objects;

public record Movimentacao(
        String produto,
        TipoMovimento tipo,
        int quantidade,
        LocalDateTime quando) {
    public Movimentacao {
        if (produto == null || produto.isBlank()) throw new IllegalArgumentException("Produto obrigatório");
        Objects.requireNonNull(tipo, "Tipo obrigatório");
        if (quantidade <= 0) throw new IllegalArgumentException("Quantidade deve ser positiva");
        Objects.requireNonNull(quando, "Data obrigatória");
        produto = produto.trim();
    }

    public enum TipoMovimento {
        ENTRADA, SAIDA
    }
}
