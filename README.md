# Estoque Rastreável

Núcleo de domínio em **Java 17** para registrar entradas, saídas e histórico de estoque em memória.

[![CI](https://github.com/juniordev830/estoque-rastreavel/actions/workflows/ci.yml/badge.svg)](https://github.com/juniordev830/estoque-rastreavel/actions)

## O que o código demonstra

| Regra | Implementação e evidência |
| --- | --- |
| Quantidade positiva e produto obrigatório | Validação no record `Movimentacao` |
| Saída sem saldo suficiente | Rejeição antes de alterar saldo ou histórico |
| Saldo sem estouro de inteiro | `Math.addExact`; overflow rejeitado sem movimentação parcial |
| Histórico por produto | Registros imutáveis e lista de consulta não modificável |
| Tempo determinístico nos testes | `Clock` injetável; implementação padrão usa UTC |

Código: [serviço](src/main/java/br/junior/estoque/EstoqueService.java) · [modelo](src/main/java/br/junior/estoque/Movimentacao.java) · [testes](src/test/java/br/junior/estoque/EstoqueServiceTest.java).

## Exemplo de uso

```java
var estoque = new EstoqueService();
estoque.registrarEntrada("Teclado", 10);
estoque.registrarSaida("Teclado", 3);
System.out.println(estoque.saldo("Teclado")); // 7
System.out.println(estoque.historico("Teclado").size()); // 2
```

As chaves de produto removem espaços nas extremidades e diferenciam maiúsculas de minúsculas. O saldo de cada produto varia de 0 a `Integer.MAX_VALUE`; uma entrada acima desse limite lança `ArithmeticException`, preservando o estado anterior.

## Como verificar

Requisitos: JDK 17 e Maven instalado. Na raiz do repositório:

```sh
mvn -B test
```

O GitHub Actions executa os testes em pushes e pull requests. A suíte cobre movimentação normal, limite exato, overflow, quantidades inválidas, saída sem saldo, snapshots do histórico e relógio fixo.

## Estado e limites

Este projeto é uma biblioteca de domínio testável, sem aplicação web, API, banco ou CLI. O armazenamento em memória é destinado a uso sequencial em um único processo; não há garantias para chamadas concorrentes. Os registros desaparecem ao encerrar o processo. Ainda faltam catálogo de produtos, persistência transacional e interface.

Os exemplos usam dados fictícios. Não versionar credenciais ou dados pessoais. Licença ainda não definida.

## Documentação para manutenção

O [guia de validação](docs/GUIA_DE_VALIDACAO.md) contém testes de regressão, pontos críticos e critérios para revisar a próxima alteração.
